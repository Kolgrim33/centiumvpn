package org.centium.vpn.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.centium.vpn.R
import org.centium.vpn.data.CircuitNode
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.databinding.FragmentConnectionBinding
import org.centium.vpn.vpn.CentiumVpnService
import org.centium.vpn.vpn.VpnLifecycleManager

class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var vpnManager: VpnLifecycleManager

    /** First render jumps straight to the current state; later changes animate. */
    private var hasRendered = false

    private data class Snapshot(
        val state: ConnectionState,
        val tunnelHeld: Boolean,
        val bootstrap: Int,
        val message: String,
        val exitIp: String?,
        val country: String?,
        val circuit: List<CircuitNode>
    )

    override fun onAttach(context: Context) {
        super.onAttach(context)
        vpnManager = VpnLifecycleManager(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        hasRendered = false
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConnectToggle.setOnClickListener { handleConnectToggle() }

        binding.btnNewCircuit.setOnClickListener {
            val service = CentiumVpnService.activeServiceInstance ?: return@setOnClickListener
            binding.btnNewCircuit.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = service.requestNewCircuit()
                _binding?.btnNewCircuit?.isEnabled = true
                if (ok) Toast.makeText(requireContext(), R.string.new_circuit_done, Toast.LENGTH_SHORT).show()
            }
        }

        observeServiceState()
    }

    private fun handleConnectToggle() {
        if (isActiveOrHeld(CentiumVpnService.connectionState.value, CentiumVpnService.tunnelHeld.value)) {
            vpnManager.stopVpnService()
        } else {
            val act = activity ?: return
            if (vpnManager.prepareVpn(act, MainActivity.VPN_PREPARE_REQUEST_CODE)) {
                vpnManager.startVpnService()
            }
        }
    }

    /** Connected, connecting, or holding a fail-closed tunnel: the button disconnects. */
    private fun isActiveOrHeld(state: ConnectionState, tunnelHeld: Boolean): Boolean =
        state.isConnected || state.isTransitioning || tunnelHeld

    private fun observeServiceState() {
        val s = CentiumVpnService
        val core = combine(s.connectionState, s.tunnelHeld, s.bootstrapPercent, s.statusMessage) { state, held, pct, msg ->
            arrayOf<Any?>(state, held, pct, msg)
        }
        val exit = combine(s.publicIp, s.exitCountry, s.circuit) { ip, country, circuit ->
            Triple(ip, country, circuit)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(core, exit) { c, e ->
                Snapshot(
                    state = c[0] as ConnectionState,
                    tunnelHeld = c[1] as Boolean,
                    bootstrap = c[2] as Int,
                    message = c[3] as String,
                    exitIp = e.first,
                    country = e.second,
                    circuit = e.third
                )
            }.collect { render(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(s.bytesReceived, s.bytesSent) { rx, tx -> rx to tx }.collect { (rx, tx) ->
                binding.tvTraffic.text = getString(R.string.traffic, formatBytes(rx), formatBytes(tx))
            }
        }
    }

    private fun render(snap: Snapshot) {
        val state = snap.state
        val held = snap.tunnelHeld && !state.isConnected && !state.isTransitioning
        val connected = state.isConnected

        // Headline and body
        val (headline, body) = when {
            state == ConnectionState.DISCONNECTING ->
                getString(R.string.headline_disconnecting) to getString(R.string.body_disconnecting)
            held -> getString(R.string.headline_blocked) to getString(R.string.body_blocked)
            connected -> getString(R.string.headline_on) to getString(R.string.body_on)
            state.isTransitioning -> getString(R.string.headline_building) to buildingBody(state, snap.bootstrap)
            state == ConnectionState.ERROR -> getString(R.string.headline_error) to snap.message
            else -> getString(R.string.headline_off) to getString(R.string.body_off)
        }
        binding.tvHeadline.text = headline
        binding.tvBody.text = body

        // Route line
        val route = binding.route
        route.tone = if (held || state == ConnectionState.ERROR) RouteLineView.Tone.DANGER else RouteLineView.Tone.ROUTE
        route.building = state.isTransitioning && state != ConnectionState.DISCONNECTING
        route.setProgress(routeProgress(state, snap.bootstrap, held), animate = hasRendered)
        hasRendered = true

        renderStations(connected, snap)

        // Footer
        val showLive = connected || held
        binding.tvTraffic.visibility = if (showLive) View.VISIBLE else View.INVISIBLE
        binding.btnNewCircuit.visibility = if (connected) View.VISIBLE else View.INVISIBLE

        renderButton(state, held)
    }

    private fun buildingBody(state: ConnectionState, bootstrap: Int): String = when (state) {
        ConnectionState.STARTING_TOR, ConnectionState.WAITING_FOR_BOOTSTRAP, ConnectionState.RECONNECTING ->
            getString(R.string.body_bootstrap, bootstrap.coerceIn(0, 100))
        ConnectionState.VERIFYING_PROTECTION -> getString(R.string.body_verifying)
        else -> getString(R.string.body_tunnel)
    }

    /** Stations sit at 0, .25, .5, .75 and 1; Tor's bootstrap carries the line to the exit. */
    private fun routeProgress(state: ConnectionState, bootstrap: Int, held: Boolean): Float = when {
        held -> 0.25f
        state == ConnectionState.CONNECTED -> 1f
        state == ConnectionState.VERIFYING_PROTECTION -> 0.92f
        state == ConnectionState.STARTING_BRIDGE ||
            state == ConnectionState.ESTABLISHING_VPN ||
            state == ConnectionState.CONFIGURING_DNS_AND_ROUTES -> 0.85f
        state == ConnectionState.STARTING_TOR -> 0.03f
        state == ConnectionState.WAITING_FOR_BOOTSTRAP || state == ConnectionState.RECONNECTING ->
            0.03f + 0.72f * bootstrap.coerceIn(0, 100) / 100f
        else -> 0f
    }

    private fun renderStations(connected: Boolean, snap: Snapshot) {
        val ink = requireContext().getColor(R.color.ink)
        val slate = requireContext().getColor(R.color.slate)

        binding.tvPhoneDesc.setText(if (connected) R.string.station_phone_on else R.string.station_phone_off)

        val byRole = snap.circuit.associateBy { it.role }
        listOf(
            Triple(binding.tvGuardTitle, binding.tvGuardMeta, CircuitNode.Role.Guard),
            Triple(binding.tvMiddleTitle, binding.tvMiddleMeta, CircuitNode.Role.Middle),
            Triple(binding.tvExitTitle, binding.tvExitMeta, CircuitNode.Role.Exit)
        ).forEach { (title, meta, role) ->
            title.setTextColor(if (connected) ink else slate)
            meta.text = if (connected) byRole[role]?.nickname.orEmpty() else ""
        }

        binding.tvInternetMeta.text = if (connected) snap.exitIp.orEmpty() else ""
        binding.tvInternetDesc.text = when {
            !connected -> getString(R.string.station_internet_off)
            !snap.country.isNullOrBlank() && snap.country != "Unknown" ->
                getString(R.string.station_internet_on_country, snap.country)
            else -> getString(R.string.station_internet_on)
        }
    }

    private fun renderButton(state: ConnectionState, held: Boolean) {
        val btn = binding.btnConnectToggle
        val ctx = requireContext()
        val density = resources.displayMetrics.density

        fun filled(text: Int) {
            btn.setText(text)
            btn.backgroundTintList = ColorStateList.valueOf(ctx.getColor(R.color.route))
            btn.setTextColor(ctx.getColor(R.color.on_route))
            btn.strokeWidth = 0
        }

        fun outlined(text: Int) {
            btn.setText(text)
            btn.backgroundTintList = ColorStateList.valueOf(ctx.getColor(R.color.mist))
            btn.setTextColor(ctx.getColor(R.color.ink))
            btn.strokeWidth = (1.5f * density).toInt()
        }

        btn.isEnabled = state != ConnectionState.DISCONNECTING
        when {
            state == ConnectionState.DISCONNECTING -> outlined(R.string.action_disconnecting)
            state.isConnected || held -> outlined(R.string.action_disconnect)
            state.isTransitioning -> outlined(R.string.action_cancel)
            else -> filled(R.string.action_connect)
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

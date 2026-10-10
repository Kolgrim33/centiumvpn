package org.centium.vpn.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.centium.vpn.R
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.databinding.FragmentConnectionBinding
import org.centium.vpn.security.LockdownDetector
import org.centium.vpn.vpn.CentiumVpnService
import org.centium.vpn.vpn.VpnLifecycleManager

class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var vpnManager: VpnLifecycleManager
    private lateinit var lockdownDetector: LockdownDetector

    override fun onAttach(context: Context) {
        super.onAttach(context)
        vpnManager = VpnLifecycleManager(context)
        lockdownDetector = LockdownDetector(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvKillSwitchBanner.text = lockdownDetector.getLockdownStatusDescription()

        binding.btnConnectToggle.setOnClickListener {
            handleConnectToggle()
        }

        binding.btnNewIdentity.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val service = CentiumVpnService.activeServiceInstance
                val ok = service?.torManager?.controller?.signalNewnym() ?: false
                if (ok) {
                    CentiumVpnService.addLog("[Identity] Signal NEWNYM sent. New connections will use fresh circuits.")
                }
            }
        }

        binding.btnRunDiagnostics.setOnClickListener {
            (activity as? MainActivity)?.navigateToDiagnostics()
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
        val service = CentiumVpnService

        viewLifecycleOwner.lifecycleScope.launch {
            service.connectionState.combine(service.tunnelHeld) { state, held -> state to held }
                .collectLatest { (state, held) -> updateUiForState(state, held) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            service.statusMessage.collectLatest { msg ->
                binding.tvStatusSubtitle.text = msg
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            service.bootstrapPercent.collectLatest { pct ->
                if (pct in 1..99) {
                    binding.pbBootstrap.visibility = View.VISIBLE
                    binding.pbBootstrap.progress = pct
                } else {
                    binding.pbBootstrap.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            service.publicIp.collectLatest { ip ->
                binding.tvExitIp.text = ip ?: "--"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            service.circuit.collectLatest { nodes ->
                if (nodes.isNotEmpty()) {
                    val summary = nodes.joinToString(" ➔ ") { "${it.role}: ${it.nickname} (${it.countryCode})" }
                    binding.tvCircuitNodes.text = summary
                } else {
                    binding.tvCircuitNodes.text = "Guard ➔ Middle ➔ Exit"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            service.bytesReceived.collectLatest { rx ->
                binding.tvDownloadBytes.text = formatBytes(rx)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            service.bytesSent.collectLatest { tx ->
                binding.tvUploadBytes.text = formatBytes(tx)
            }
        }
    }

    private fun updateUiForState(state: ConnectionState, tunnelHeld: Boolean) {
        binding.tvStatusTitle.text = state.defaultMessage

        when {
            state == ConnectionState.DISCONNECTING -> {
                binding.btnConnectToggle.text = "Disconnecting..."
                binding.btnConnectToggle.isEnabled = false
                binding.btnNewIdentity.isEnabled = false
            }
            state.isConnected || tunnelHeld -> {
                binding.btnConnectToggle.isEnabled = true
                binding.btnConnectToggle.text = getString(R.string.action_disconnect)
                binding.btnConnectToggle.setBackgroundColor(requireContext().getColor(R.color.centium_error))
                binding.btnNewIdentity.isEnabled = true
            }
            state.isTransitioning -> {
                binding.btnConnectToggle.isEnabled = true
                binding.btnConnectToggle.text = getString(R.string.action_cancel)
                binding.btnConnectToggle.setBackgroundColor(requireContext().getColor(R.color.centium_warning))
                binding.btnNewIdentity.isEnabled = false
            }
            else -> {
                binding.btnConnectToggle.isEnabled = true
                binding.btnConnectToggle.text = getString(R.string.action_connect)
                binding.btnConnectToggle.setBackgroundColor(requireContext().getColor(R.color.centium_primary))
                binding.btnNewIdentity.isEnabled = false
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

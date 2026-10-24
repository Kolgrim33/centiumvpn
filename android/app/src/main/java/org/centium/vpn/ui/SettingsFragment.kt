package org.centium.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import org.centium.vpn.CentiumApplication
import org.centium.vpn.R
import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.databinding.FragmentSettingsBinding
import org.centium.vpn.tor.BridgeLines
import org.centium.vpn.update.UpdateManager
import org.centium.vpn.vpn.CentiumVpnService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    /** "auto" plus countries that host many Tor exits; codes go straight into torrc ExitNodes. */
    private lateinit var exitCodes: MutableList<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefsRepo = CentiumApplication.instance.preferencesRepository
        val currentConfig = prefsRepo.getConfig()

        binding.switchKillSwitch.isChecked = currentConfig.killSwitch
        binding.switchBlockIpv6.isChecked = currentConfig.blockIpv6

        setUpAdBlock(currentConfig.adBlockEnabled, prefsRepo)

        setUpExitCountry(currentConfig.exitLocation)

        binding.etCustomBridges.setText(currentConfig.customBridges)
        binding.rgBridgeMode.check(bridgeButtonFor(currentConfig.bridgeMode, currentConfig.bridgeType))
        binding.groupCustomBridges.visibility =
            if (binding.rbBridgeCustom.isChecked) View.VISIBLE else View.GONE

        binding.rgBridgeMode.setOnCheckedChangeListener { _, checkedId ->
            binding.groupCustomBridges.visibility =
                if (checkedId == R.id.rbBridgeCustom) View.VISIBLE else View.GONE
        }

        binding.btnOpenVpnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }

        binding.btnOpenWebsite.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_url))))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.settings_no_browser, Toast.LENGTH_SHORT).show()
            }
        }

        setUpUpdates()

        binding.btnSaveSettings.setOnClickListener {
            val (mode, type) = selectedBridgeChoice()
            val customBridges = binding.etCustomBridges.text.toString().trim()
            if (mode == BridgeMode.CUSTOM) {
                try {
                    BridgeLines.parse(customBridges)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }
            val updated = currentConfig.copy(
                killSwitch = binding.switchKillSwitch.isChecked,
                blockIpv6 = binding.switchBlockIpv6.isChecked,
                adBlockEnabled = binding.switchAdBlock.isChecked,
                exitLocation = selectedExitCode(),
                bridgeMode = mode,
                bridgeType = type,
                customBridges = customBridges
            )
            prefsRepo.adBlockAllowlist = binding.etAdBlockAllowlist.text.toString()
            prefsRepo.saveConfig(updated)
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bridgeButtonFor(mode: BridgeMode, type: BridgeType): Int = when (mode) {
        BridgeMode.NONE -> R.id.rbBridgeNone
        BridgeMode.CUSTOM -> R.id.rbBridgeCustom
        BridgeMode.BUILTIN -> when (type) {
            BridgeType.SNOWFLAKE -> R.id.rbBridgeSnowflake
            BridgeType.OBFS4 -> R.id.rbBridgeObfs4
            else -> R.id.rbBridgeAuto
        }
        BridgeMode.AUTO -> R.id.rbBridgeAuto
    }

    private fun selectedBridgeChoice(): Pair<BridgeMode, BridgeType> = when (binding.rgBridgeMode.checkedRadioButtonId) {
        R.id.rbBridgeNone -> BridgeMode.NONE to BridgeType.NONE
        R.id.rbBridgeSnowflake -> BridgeMode.BUILTIN to BridgeType.SNOWFLAKE
        R.id.rbBridgeObfs4 -> BridgeMode.BUILTIN to BridgeType.OBFS4
        R.id.rbBridgeCustom -> BridgeMode.CUSTOM to BridgeType.NONE
        else -> BridgeMode.AUTO to BridgeType.NONE
    }

    private fun setUpAdBlock(enabled: Boolean, prefsRepo: org.centium.vpn.data.PreferencesRepository) {
        binding.switchAdBlock.isChecked = enabled
        binding.etAdBlockAllowlist.setText(prefsRepo.adBlockAllowlist)
        binding.groupAdBlockAllowlist.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.switchAdBlock.setOnCheckedChangeListener { _, checked ->
            binding.groupAdBlockAllowlist.visibility = if (checked) View.VISIBLE else View.GONE
        }

        val updatedAt = prefsRepo.blocklistUpdatedAt
        binding.tvBlocklistUpdated.text = if (updatedAt == 0L) {
            getString(R.string.settings_adblock_updated_never)
        } else {
            getString(
                R.string.settings_adblock_updated,
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(updatedAt))
            )
        }
    }

    private fun setUpUpdates() {
        val updates = CentiumApplication.instance.updateManager
        binding.tvUpdateVersion.text = getString(R.string.update_version_title, updates.currentVersion)

        // F-Droid and test builds get updates elsewhere
        if (!updates.enabled) {
            binding.labelUpdates.visibility = View.GONE
            binding.groupUpdates.visibility = View.GONE
            return
        }

        binding.switchAutoUpdate.isChecked = updates.prefs.autoUpdate
        binding.switchAutoUpdate.setOnCheckedChangeListener { _, checked -> updates.prefs.autoUpdate = checked }

        viewLifecycleOwner.lifecycleScope.launch {
            updates.state.combine(CentiumVpnService.connectionState) { s, c -> s to c }.collect { (state, conn) ->
                renderUpdateState(state, conn == ConnectionState.CONNECTED)
            }
        }
    }

    private fun renderUpdateState(state: UpdateManager.State, viaTor: Boolean) {
        val button = binding.btnCheckUpdate
        val status = binding.tvUpdateStatus
        button.isEnabled = true
        button.setText(R.string.update_check_now)
        button.setOnClickListener { CentiumApplication.instance.updateManager.checkNow() }

        when (state) {
            UpdateManager.State.Idle -> status.setText(
                if (viaTor) R.string.update_status_idle_connected else R.string.update_status_idle_direct
            )
            UpdateManager.State.Checking -> {
                status.setText(R.string.update_status_checking)
                button.isEnabled = false
            }
            is UpdateManager.State.UpToDate -> status.setText(R.string.update_status_up_to_date)
            is UpdateManager.State.Downloading -> {
                status.text = getString(R.string.update_status_downloading, state.version.toString())
                button.isEnabled = false
            }
            is UpdateManager.State.Ready -> {
                status.text = getString(R.string.update_status_ready, state.release.version.toString())
                button.setText(R.string.update_install)
                button.setOnClickListener { (activity as? MainActivity)?.installUpdate() }
            }
            is UpdateManager.State.Failed -> status.text = getString(R.string.update_status_failed, state.reason)
        }
    }

    private fun setUpExitCountry(saved: String) {
        exitCodes = (listOf("auto") + EXIT_COUNTRIES).toMutableList()
        val savedCode = saved.lowercase(Locale.ROOT).ifBlank { "auto" }
        if (savedCode !in exitCodes) exitCodes.add(savedCode)

        val labels = exitCodes.map { countryLabel(it) }
        val field = binding.acExitCountry
        field.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        field.setText(countryLabel(savedCode), false)
        field.tag = savedCode
        field.setOnItemClickListener { _, _, position, _ -> field.tag = exitCodes[position] }
    }

    private fun selectedExitCode(): String = binding.acExitCountry.tag as? String ?: "auto"

    private fun countryLabel(code: String): String {
        if (code == "auto") return getString(R.string.settings_exit_any)
        val name = regionName(code)
        return if (name.isBlank() || name.equals(code, ignoreCase = true)) code.uppercase(Locale.ROOT) else name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private fun regionName(code: String): String = try {
            Locale.Builder().setRegion(code.uppercase(Locale.ROOT)).build().displayCountry
        } catch (_: java.util.IllformedLocaleException) {
            ""
        }

        private val EXIT_COUNTRIES = listOf(
            "de", "nl", "us", "fr", "ch", "se", "fi", "no", "at", "ro", "pl", "lu", "is", "ca", "gb"
        ).sortedBy { regionName(it) }
    }
}

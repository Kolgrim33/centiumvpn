package org.centium.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Locale
import org.centium.vpn.CentiumApplication
import org.centium.vpn.R
import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.databinding.FragmentSettingsBinding

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

        setUpExitCountry(currentConfig.exitLocation)

        // Built-in (Snowflake) bridges aren't bundled, so an old BUILTIN choice falls back to direct
        if (currentConfig.bridgeMode == BridgeMode.CUSTOM) {
            binding.rbBridgeCustom.isChecked = true
            binding.groupCustomBridges.visibility = View.VISIBLE
            binding.etCustomBridges.setText(currentConfig.customBridges)
        } else {
            binding.rbBridgeNone.isChecked = true
        }

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

        binding.btnSaveSettings.setOnClickListener {
            val custom = binding.rbBridgeCustom.isChecked
            val updated = currentConfig.copy(
                killSwitch = binding.switchKillSwitch.isChecked,
                blockIpv6 = binding.switchBlockIpv6.isChecked,
                exitLocation = selectedExitCode(),
                bridgeMode = if (custom) BridgeMode.CUSTOM else BridgeMode.NONE,
                bridgeType = if (custom) BridgeType.OBFS4 else BridgeType.NONE,
                customBridges = binding.etCustomBridges.text.toString().trim()
            )
            prefsRepo.saveConfig(updated)
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
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

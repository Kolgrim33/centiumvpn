package org.centium.vpn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.centium.vpn.CentiumApplication
import org.centium.vpn.R
import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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

        // Populate values
        binding.switchKillSwitch.isChecked = currentConfig.killSwitch
        binding.switchDnsProtection.isChecked = currentConfig.dnsProtection
        binding.switchBlockIpv6.isChecked = currentConfig.blockIpv6
        binding.etExitLocation.setText(currentConfig.exitLocation)

        when (currentConfig.bridgeMode) {
            BridgeMode.BUILTIN -> binding.rbBridgeSnowflake.isChecked = true
            BridgeMode.CUSTOM -> {
                binding.rbBridgeCustom.isChecked = true
                binding.etCustomBridges.visibility = View.VISIBLE
                binding.etCustomBridges.setText(currentConfig.customBridges)
            }
            else -> binding.rbBridgeNone.isChecked = true
        }

        binding.rgBridgeMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbBridgeCustom) {
                binding.etCustomBridges.visibility = View.VISIBLE
            } else {
                binding.etCustomBridges.visibility = View.GONE
            }
        }

        binding.btnSaveSettings.setOnClickListener {
            val updated = currentConfig.copy(
                killSwitch = binding.switchKillSwitch.isChecked,
                dnsProtection = binding.switchDnsProtection.isChecked,
                blockIpv6 = binding.switchBlockIpv6.isChecked,
                exitLocation = binding.etExitLocation.text.toString().trim().ifEmpty { "auto" },
                bridgeMode = when {
                    binding.rbBridgeSnowflake.isChecked -> BridgeMode.BUILTIN
                    binding.rbBridgeCustom.isChecked -> BridgeMode.CUSTOM
                    else -> BridgeMode.NONE
                },
                bridgeType = when {
                    binding.rbBridgeSnowflake.isChecked -> BridgeType.SNOWFLAKE
                    binding.rbBridgeCustom.isChecked -> BridgeType.OBFS4
                    else -> BridgeType.NONE
                },
                customBridges = binding.etCustomBridges.text.toString().trim()
            )

            prefsRepo.saveConfig(updated)
            Toast.makeText(requireContext(), "Preferences saved.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

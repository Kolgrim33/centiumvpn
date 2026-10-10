package org.centium.vpn.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.centium.vpn.R
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.databinding.ActivityMainBinding
import org.centium.vpn.vpn.CentiumVpnService
import org.centium.vpn.vpn.VpnLifecycleManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vpnManager: VpnLifecycleManager

    private val connectionFragment = ConnectionFragment()
    private val diagnosticsFragment = DiagnosticsFragment()
    private val settingsFragment = SettingsFragment()
    private val logsFragment = LogsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vpnManager = VpnLifecycleManager(this)

        if (savedInstanceState == null) {
            loadFragment(connectionFragment)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_connection -> {
                    loadFragment(connectionFragment)
                    true
                }
                R.id.nav_diagnostics -> {
                    loadFragment(diagnosticsFragment)
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(settingsFragment)
                    true
                }
                R.id.nav_logs -> {
                    loadFragment(logsFragment)
                    true
                }
                else -> false
            }
        }

        observeServiceStatus()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun navigateToDiagnostics() {
        binding.bottomNav.selectedItemId = R.id.nav_diagnostics
    }

    private fun observeServiceStatus() {
        lifecycleScope.launch {
            while (true) {
                val service = CentiumVpnService.activeServiceInstance
                if (service != null) {
                    service.connectionState.collectLatest { state ->
                        binding.headerStatusBadge.text = state.name
                        val color = when (state) {
                            ConnectionState.CONNECTED -> getColor(R.color.status_connected)
                            ConnectionState.ERROR -> getColor(R.color.status_error)
                            ConnectionState.DISCONNECTED -> getColor(R.color.status_disconnected)
                            else -> getColor(R.color.status_connecting)
                        }
                        binding.headerStatusBadge.setTextColor(color)
                    }
                    break
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PREPARE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                vpnManager.startVpnService()
            }
        }
    }

    companion object {
        const val VPN_PREPARE_REQUEST_CODE = 8421
    }
}

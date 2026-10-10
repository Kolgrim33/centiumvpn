package org.centium.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
        requestNotificationPermission()
    }

    /** The VPN status notification is silently hidden on Android 13+ without this. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
        }
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
            CentiumVpnService.connectionState.collectLatest { state ->
                binding.headerStatusBadge.text = state.name
                val color = when (state) {
                    ConnectionState.CONNECTED -> getColor(R.color.status_connected)
                    ConnectionState.ERROR -> getColor(R.color.status_error)
                    ConnectionState.DISCONNECTED -> getColor(R.color.status_disconnected)
                    else -> getColor(R.color.status_connecting)
                }
                binding.headerStatusBadge.setTextColor(color)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PREPARE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                vpnManager.startVpnService()
            } else {
                Toast.makeText(this, "VPN permission is required to route traffic through Tor.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val VPN_PREPARE_REQUEST_CODE = 8421
        private const val NOTIFICATION_REQUEST_CODE = 8422
    }
}

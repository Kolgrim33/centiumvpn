package org.centium.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.centium.vpn.BuildConfig
import org.centium.vpn.CentiumApplication
import org.centium.vpn.R
import org.centium.vpn.databinding.ActivityMainBinding
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
        fitSystemBars()

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

        requestNotificationPermission()
        if (savedInstanceState == null) showWhatsNew()
    }

    /** Release notes for the version that just installed itself, shown once. */
    private fun showWhatsNew() {
        val notes = CentiumApplication.instance.updateManager.prefs.consumeWhatsNew(BuildConfig.VERSION_CODE)
        if (notes.isNullOrBlank()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
            .setMessage(plainNotes(notes))
            .setPositiveButton(R.string.whats_new_dismiss, null)
            .show()
    }

    /** Release notes are Markdown on GitHub; show them as plain text with bullets. */
    private fun plainNotes(markdown: String): String = markdown.lines()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n") { line ->
            line.trimEnd()
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("`(.+?)`"), "$1")
        }
        .trim()

    /** Set while the user is in Android settings allowing Centium to install updates. */
    private var resumeInstallOnReturn = false

    /** Shared by the Connect banner and Settings. */
    fun installUpdate() {
        val needsPermission = CentiumApplication.instance.updateManager.install() ?: return
        Toast.makeText(this, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
        resumeInstallOnReturn = true
        startActivity(needsPermission)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "install unknown apps" switch: carry on without a second tap
        if (resumeInstallOnReturn && packageManager.canRequestPackageInstalls()) {
            resumeInstallOnReturn = false
            CentiumApplication.instance.updateManager.install()
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PREPARE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                vpnManager.startVpnService()
            } else {
                Toast.makeText(this, R.string.vpn_permission_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val VPN_PREPARE_REQUEST_CODE = 8421
        private const val NOTIFICATION_REQUEST_CODE = 8422
    }

    /**
     * Android 15+ always draws apps edge to edge, so do it on every version and pad for the bars.
     * The bottom nav pads itself for the navigation bar. While the keyboard is open the nav is
     * hidden (it would otherwise add the keyboard's height again) and the content sits above it.
     */
    private fun fitSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboardOpen = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottom = if (keyboardOpen) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            root.setPadding(bars.left, bars.top, bars.right, bottom)
            binding.bottomNav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            binding.bottomNavDivider.visibility = binding.bottomNav.visibility
            insets
        }
    }
}

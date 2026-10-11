package org.centium.vpn.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import org.centium.vpn.CentiumApplication
import org.centium.vpn.vpn.CentiumVpnService

/**
 * Hands a verified APK to Android's PackageInstaller. Once Centium has installed
 * itself, Android 12+ lets later updates skip the confirmation screen.
 */
object UpdateInstaller {

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("centium.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallStatusReceiver::class.java),
                // The system adds the status extras, so the intent must stay mutable
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(callback.intentSender)
        }
    }
}

/** Receives the install outcome; shows the system confirmation when Android requires it. */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = CentiumApplication.instance.updateManager
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // this process is replaced right after
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                manager.onInstallFailed(
                    if (status == PackageInstaller.STATUS_FAILURE_ABORTED) null else message ?: "Install failed ($status)"
                )
            }
        }
    }
}

/** Runs in the new version right after an update; restores the VPN if it was on. */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = CentiumApplication.instance.updateManager.prefs
        if (!prefs.reconnectAfterUpdate) return
        prefs.reconnectAfterUpdate = false
        if (android.net.VpnService.prepare(context) == null) {
            CentiumVpnService.addLog("[Update] Reconnecting after update.")
            CentiumVpnService.start(context)
        }
    }
}

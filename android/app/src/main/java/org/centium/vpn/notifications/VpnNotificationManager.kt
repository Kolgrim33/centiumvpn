package org.centium.vpn.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.centium.vpn.R
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.ui.MainActivity
import org.centium.vpn.vpn.CentiumVpnService

class VpnNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_vpn),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_vpn_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildVpnNotification(
        state: ConnectionState,
        exitIp: String?,
        country: String?
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(context, CentiumVpnService::class.java).apply {
            action = CentiumVpnService.ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            context,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when (state) {
            ConnectionState.CONNECTED -> {
                if (exitIp != null) "Protected via Tor ($exitIp - ${country ?: "Exit"})"
                else "All device traffic routed through Tor"
            }
            else -> state.defaultMessage
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_lock)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(state.isConnected || state.isTransitioning)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state.isConnected || state.isTransitioning || state == ConnectionState.ERROR) {
            builder.addAction(
                R.drawable.ic_vpn_lock,
                context.getString(R.string.action_disconnect),
                disconnectPending
            )
        }

        return builder.build()
    }

    fun updateNotification(state: ConnectionState, exitIp: String?, country: String?) {
        val notification = buildVpnNotification(state, exitIp, country)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "centium_vpn_channel"
        const val NOTIFICATION_ID = 8420
    }
}

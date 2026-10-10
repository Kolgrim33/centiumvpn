package org.centium.vpn.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.ui.MainActivity
import org.centium.vpn.vpn.CentiumVpnService

class CentiumTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val state = CentiumVpnService.connectionState.value

        if (state.isConnected || state.isTransitioning || CentiumVpnService.tunnelHeld.value) {
            CentiumVpnService.stop(this)
        } else if (VpnService.prepare(this) != null) {
            // VPN consent dialog needs an Activity
            openApp()
        } else {
            try {
                CentiumVpnService.start(this)
            } catch (_: Exception) {
                openApp()
            }
        }
        updateTileState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val state = CentiumVpnService.connectionState.value

        val subtitle = when (state) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.DISCONNECTED -> "Disconnected"
            ConnectionState.ERROR -> "Error"
            else -> "Connecting…"
        }
        tile.state = when (state) {
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }
}

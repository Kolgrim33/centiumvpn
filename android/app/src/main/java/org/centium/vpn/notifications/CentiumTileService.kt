package org.centium.vpn.notifications

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import org.centium.vpn.CentiumApplication
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.vpn.CentiumVpnService

@RequiresApi(Build.VERSION_CODES.N)
class CentiumTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val service = CentiumVpnService.activeServiceInstance
        val isConnected = service?.connectionState?.value?.isConnected == true

        if (isConnected) {
            CentiumVpnService.stop(this)
        } else {
            val config = CentiumApplication.instance.preferencesRepository.getConfig()
            CentiumVpnService.start(this)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val service = CentiumVpnService.activeServiceInstance
        val state = service?.connectionState?.value ?: ConnectionState.DISCONNECTED

        when (state) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "Connected"
            }
            ConnectionState.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Disconnected"
            }
            else -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = state.defaultMessage
            }
        }
        tile.updateTile()
    }
}

package org.centium.vpn

import android.app.Application
import org.centium.vpn.data.PreferencesRepository
import org.centium.vpn.notifications.VpnNotificationManager
import org.centium.vpn.update.UpdateManager
import org.centium.vpn.update.UpdateNotifier

class CentiumApplication : Application() {

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var notificationManager: VpnNotificationManager
        private set

    lateinit var updateManager: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesRepository = PreferencesRepository(this)
        notificationManager = VpnNotificationManager(this)
        notificationManager.createNotificationChannels()
        UpdateNotifier.createChannel(this)
        updateManager = UpdateManager(this)
    }

    companion object {
        lateinit var instance: CentiumApplication
            private set
    }
}

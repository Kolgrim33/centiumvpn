package org.centium.vpn

import android.app.Application
import org.centium.vpn.data.PreferencesRepository
import org.centium.vpn.notifications.VpnNotificationManager

class CentiumApplication : Application() {

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var notificationManager: VpnNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesRepository = PreferencesRepository(this)
        notificationManager = VpnNotificationManager(this)
        notificationManager.createNotificationChannels()
    }

    companion object {
        lateinit var instance: CentiumApplication
            private set
    }
}

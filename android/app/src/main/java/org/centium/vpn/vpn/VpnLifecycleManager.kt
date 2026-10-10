package org.centium.vpn.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService

class VpnLifecycleManager(private val context: Context) {

    fun isVpnPrepared(): Boolean {
        return VpnService.prepare(context) == null
    }

    fun prepareVpn(activity: Activity, requestCode: Int): Boolean {
        val intent = VpnService.prepare(activity)
        return if (intent != null) {
            activity.startActivityForResult(intent, requestCode)
            false
        } else {
            true
        }
    }

    fun startVpnService() {
        CentiumVpnService.start(context)
    }

    fun stopVpnService() {
        CentiumVpnService.stop(context)
    }
}

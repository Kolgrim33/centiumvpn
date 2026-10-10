package org.centium.vpn.security

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

class LockdownDetector(private val context: Context) {

    fun isAlwaysOnVpnActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ provides explicit system VPN lockdown awareness
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
        } else {
            false
        }
    }

    fun getLockdownStatusDescription(): String {
        return if (isAlwaysOnVpnActive()) {
            "OS-Enforced Lockdown Active: Android blocks all non-VPN traffic at the kernel level."
        } else {
            "In-App Fail-Closed Active: Tunnel drops unhandled traffic. For full OS-level protection, enable 'Block connections without VPN' in Android Settings."
        }
    }
}

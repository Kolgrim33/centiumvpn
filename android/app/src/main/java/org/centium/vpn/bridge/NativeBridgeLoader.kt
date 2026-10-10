package org.centium.vpn.bridge

object NativeBridgeLoader {

    fun isNativeLibraryAvailable(): Boolean {
        return try {
            System.loadLibrary("hev-socks5-tunnel")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}

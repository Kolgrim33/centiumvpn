package org.centium.vpn.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): CentiumConfig {
        return CentiumConfig(
            exitLocation = prefs.getString(KEY_EXIT_LOCATION, "auto") ?: "auto",
            bridgeMode = BridgeMode.valueOf(prefs.getString(KEY_BRIDGE_MODE, BridgeMode.AUTO.name) ?: BridgeMode.AUTO.name),
            bridgeType = BridgeType.valueOf(prefs.getString(KEY_BRIDGE_TYPE, BridgeType.NONE.name) ?: BridgeType.NONE.name),
            customBridges = prefs.getString(KEY_CUSTOM_BRIDGES, "") ?: "",
            killSwitch = prefs.getBoolean(KEY_KILL_SWITCH, true),
            blockIpv6 = prefs.getBoolean(KEY_BLOCK_IPV6, true),
            autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            dnsProtection = prefs.getBoolean(KEY_DNS_PROTECTION, true),
            socksPort = prefs.getInt(KEY_SOCKS_PORT, 9050),
            controlPort = prefs.getInt(KEY_CONTROL_PORT, 9051),
            dnsPort = prefs.getInt(KEY_DNS_PORT, 9053),
            connectionTimeoutSeconds = prefs.getInt(KEY_TIMEOUT, 60),
            disallowPackages = prefs.getStringSet(KEY_DISALLOWED_PKGS, emptySet()) ?: emptySet()
        )
    }

    fun saveConfig(config: CentiumConfig) {
        prefs.edit()
            .putString(KEY_EXIT_LOCATION, config.exitLocation)
            .putString(KEY_BRIDGE_MODE, config.bridgeMode.name)
            .putString(KEY_BRIDGE_TYPE, config.bridgeType.name)
            .putString(KEY_CUSTOM_BRIDGES, config.customBridges)
            .putBoolean(KEY_KILL_SWITCH, config.killSwitch)
            .putBoolean(KEY_BLOCK_IPV6, config.blockIpv6)
            .putBoolean(KEY_AUTO_CONNECT, config.autoConnect)
            .putBoolean(KEY_DNS_PROTECTION, config.dnsProtection)
            .putInt(KEY_SOCKS_PORT, config.socksPort)
            .putInt(KEY_CONTROL_PORT, config.controlPort)
            .putInt(KEY_DNS_PORT, config.dnsPort)
            .putInt(KEY_TIMEOUT, config.connectionTimeoutSeconds)
            .putStringSet(KEY_DISALLOWED_PKGS, config.disallowPackages)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "centium_vpn_prefs"
        private const val KEY_EXIT_LOCATION = "exit_location"
        private const val KEY_BRIDGE_MODE = "bridge_mode"
        private const val KEY_BRIDGE_TYPE = "bridge_type"
        private const val KEY_CUSTOM_BRIDGES = "custom_bridges"
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_BLOCK_IPV6 = "block_ipv6"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_DNS_PROTECTION = "dns_protection"
        private const val KEY_SOCKS_PORT = "socks_port"
        private const val KEY_CONTROL_PORT = "control_port"
        private const val KEY_DNS_PORT = "dns_port"
        private const val KEY_TIMEOUT = "connection_timeout"
        private const val KEY_DISALLOWED_PKGS = "disallowed_packages"
    }
}

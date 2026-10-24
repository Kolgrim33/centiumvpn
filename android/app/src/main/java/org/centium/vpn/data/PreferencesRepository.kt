package org.centium.vpn.data

import android.content.Context
import android.content.SharedPreferences
import org.centium.vpn.tor.Route

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrate()
    }

    /** The route that last connected in Automatic mode, tried first next time. */
    var lastWorkingRoute: Route?
        get() = Route.entries.firstOrNull { it.name == prefs.getString(KEY_LAST_ROUTE, null) }
        set(value) = prefs.edit().putString(KEY_LAST_ROUTE, value?.name).apply()

    /** Domains the user allows past the blocklist (one per line, exactly as entered). */
    var adBlockAllowlist: String
        get() = prefs.getString(KEY_AD_BLOCK_ALLOW, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AD_BLOCK_ALLOW, value).apply()

    /** When the downloaded blocklist was last refreshed, epoch millis, or 0 if never. */
    var blocklistUpdatedAt: Long
        get() = prefs.getLong(KEY_BLOCKLIST_UPDATED, 0L)
        set(value) = prefs.edit().putLong(KEY_BLOCKLIST_UPDATED, value).apply()

    // Before 1.3.0, saving Settings stored "Connect directly" (NONE) because it was the only
    // non-custom choice. Automatic still tries direct first, so move those users onto it once.
    private fun migrate() {
        if (prefs.getInt(KEY_SCHEMA, 1) >= 2) return
        val edit = prefs.edit()
        if (prefs.getString(KEY_BRIDGE_MODE, null) == BridgeMode.NONE.name) {
            edit.putString(KEY_BRIDGE_MODE, BridgeMode.AUTO.name).putString(KEY_BRIDGE_TYPE, BridgeType.NONE.name)
        }
        edit.putInt(KEY_SCHEMA, 2).apply()
    }

    fun getConfig(): CentiumConfig {
        return CentiumConfig(
            exitLocation = prefs.getString(KEY_EXIT_LOCATION, "auto") ?: "auto",
            bridgeMode = BridgeMode.entries.firstOrNull { it.name == prefs.getString(KEY_BRIDGE_MODE, null) } ?: BridgeMode.AUTO,
            bridgeType = BridgeType.entries.firstOrNull { it.name == prefs.getString(KEY_BRIDGE_TYPE, null) } ?: BridgeType.NONE,
            customBridges = prefs.getString(KEY_CUSTOM_BRIDGES, "") ?: "",
            killSwitch = prefs.getBoolean(KEY_KILL_SWITCH, true),
            blockIpv6 = prefs.getBoolean(KEY_BLOCK_IPV6, true),
            autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            dnsProtection = prefs.getBoolean(KEY_DNS_PROTECTION, true),
            adBlockEnabled = prefs.getBoolean(KEY_AD_BLOCK, false),
            connectionTimeoutSeconds = prefs.getInt(KEY_TIMEOUT, 120),
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
            .putBoolean(KEY_AD_BLOCK, config.adBlockEnabled)
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
        private const val KEY_AD_BLOCK = "ad_block_enabled"
        private const val KEY_AD_BLOCK_ALLOW = "ad_block_allowlist"
        private const val KEY_BLOCKLIST_UPDATED = "blocklist_updated_at"
        private const val KEY_TIMEOUT = "connection_timeout"
        private const val KEY_DISALLOWED_PKGS = "disallowed_packages"
        private const val KEY_LAST_ROUTE = "last_working_route"
        private const val KEY_SCHEMA = "prefs_schema"
    }
}

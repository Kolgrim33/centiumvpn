package org.centium.vpn.update

import android.content.Context

/** Update bookkeeping, kept apart from the VPN config. */
class UpdatePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("centium_updates", Context.MODE_PRIVATE)

    var autoUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var lastCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** Set just before installing; read once after the restart. */
    var reconnectAfterUpdate: Boolean
        get() = prefs.getBoolean(KEY_RECONNECT, false)
        set(value) {
            // Committed synchronously: the process is killed moments later by the install
            prefs.edit().putBoolean(KEY_RECONNECT, value).commit()
        }

    /** Notes for the version being installed, shown once it is running. */
    fun stashNotes(versionCode: Int, notes: String) {
        prefs.edit().putInt(KEY_NOTES_CODE, versionCode).putString(KEY_NOTES, notes).commit()
    }

    /**
     * Returns release notes to show if this is the first launch of a newer version,
     * then forgets them. A fresh install just records its version.
     */
    fun consumeWhatsNew(currentCode: Int): String? {
        val lastSeen = prefs.getInt(KEY_LAST_SEEN, 0)
        if (lastSeen >= currentCode) return null
        val notes = prefs.getString(KEY_NOTES, null)
            ?.takeIf { lastSeen != 0 && prefs.getInt(KEY_NOTES_CODE, 0) == currentCode }
        prefs.edit().putInt(KEY_LAST_SEEN, currentCode).remove(KEY_NOTES).remove(KEY_NOTES_CODE).apply()
        return notes
    }

    companion object {
        private const val KEY_AUTO = "auto_update"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_RECONNECT = "reconnect_after_update"
        private const val KEY_NOTES = "pending_notes"
        private const val KEY_NOTES_CODE = "pending_notes_code"
        private const val KEY_LAST_SEEN = "last_seen_version_code"
    }
}

package com.rawpos.scanner.core

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * A stable per-installation device id.
 *
 * The desktop binds a token to this id, so it must survive restarts. It is not a secret: it identifies
 * the installation, never the person holding it.
 */
object DeviceIdentity {

    private const val PREFS = "rawpos_scanner_device"
    private const val KEY = "device_id"

    fun of(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = preferences.getString(KEY, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        preferences.edit { putString(KEY, generated) }
        return generated
    }
}

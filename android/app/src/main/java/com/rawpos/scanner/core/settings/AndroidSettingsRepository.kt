package com.rawpos.scanner.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 1 persistence uses platform SharedPreferences so no additional dependency is required.
 * A DataStore-backed implementation can replace it later without changing the interface.
 */
class AndroidSettingsRepository(context: Context) : SettingsRepository {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(readLanguage(preferences))
    private val _theme = MutableStateFlow(readTheme(preferences))

    override val language: StateFlow<LanguageMode> = _language.asStateFlow()
    override val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    override fun setLanguage(mode: LanguageMode) {
        preferences.edit { putString(KEY_LANGUAGE, mode.storageValue) }
        _language.value = mode
    }

    override fun setTheme(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME, mode.storageValue) }
        _theme.value = mode
    }

    companion object {
        const val PREFS_NAME = "rawpos_scanner_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"

        fun readLanguage(context: Context): LanguageMode =
            readLanguage(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

        fun readTheme(context: Context): ThemeMode =
            readTheme(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

        private fun readLanguage(preferences: SharedPreferences): LanguageMode =
            LanguageMode.fromStorage(preferences.getString(KEY_LANGUAGE, null))

        private fun readTheme(preferences: SharedPreferences): ThemeMode =
            ThemeMode.fromStorage(preferences.getString(KEY_THEME, null))
    }
}

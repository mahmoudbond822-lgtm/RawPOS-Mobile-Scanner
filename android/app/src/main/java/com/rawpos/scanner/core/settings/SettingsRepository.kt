package com.rawpos.scanner.core.settings

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val language: StateFlow<LanguageMode>
    val theme: StateFlow<ThemeMode>
    val scanFeedback: StateFlow<Boolean>

    fun setLanguage(mode: LanguageMode)

    fun setTheme(mode: ThemeMode)

    fun setScanFeedback(enabled: Boolean)
}

package com.rawpos.scanner.core.settings

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val language: StateFlow<LanguageMode>
    val theme: StateFlow<ThemeMode>

    fun setLanguage(mode: LanguageMode)

    fun setTheme(mode: ThemeMode)
}

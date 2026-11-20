package com.rawpos.scanner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rawpos.scanner.core.AppInfo
import com.rawpos.scanner.core.settings.LanguageMode
import com.rawpos.scanner.core.settings.SettingsRepository
import com.rawpos.scanner.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val language: LanguageMode = LanguageMode.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val versionName: String = "",
    val buildType: String = "",
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    appInfo: AppInfo,
) : ViewModel() {

    private val initial = SettingsUiState(
        language = repository.language.value,
        theme = repository.theme.value,
        versionName = appInfo.versionName,
        buildType = appInfo.buildType,
    )

    val state: StateFlow<SettingsUiState> = combine(
        repository.language,
        repository.theme,
    ) { language, theme ->
        SettingsUiState(
            language = language,
            theme = theme,
            versionName = appInfo.versionName,
            buildType = appInfo.buildType,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initial,
    )

    fun setLanguage(mode: LanguageMode) {
        repository.setLanguage(mode)
    }

    fun setTheme(mode: ThemeMode) {
        repository.setTheme(mode)
    }
}

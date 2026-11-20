package com.rawpos.scanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawpos.scanner.core.AppContainer
import com.rawpos.scanner.feature.settings.SettingsViewModel
import com.rawpos.scanner.navigation.RawPosNavHost

@Composable
fun RawPosApp(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
    onLanguageChanged: () -> Unit,
) {
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    RawPosNavHost(
        container = container,
        settingsViewModel = settingsViewModel,
        settings = settings,
        onLanguageChanged = onLanguageChanged,
    )
}

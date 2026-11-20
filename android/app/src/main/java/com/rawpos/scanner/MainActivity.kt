package com.rawpos.scanner

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rawpos.scanner.core.AppContainer
import com.rawpos.scanner.core.settings.AndroidSettingsRepository
import com.rawpos.scanner.feature.settings.SettingsViewModel
import com.rawpos.scanner.ui.RawPosApp
import com.rawpos.scanner.ui.locale.LocaleSupport
import com.rawpos.scanner.ui.theme.RawPosTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy {
        (application as RawPosScannerApplication).container
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleSupport.wrap(newBase, AndroidSettingsRepository.readLanguage(newBase)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel =
                viewModel(factory = container.viewModelFactory)
            val settings by settingsViewModel.state.collectAsStateWithLifecycle()

            RawPosTheme(
                themeMode = settings.theme,
                language = settings.language,
            ) {
                RawPosApp(
                    container = container,
                    settingsViewModel = settingsViewModel,
                    onLanguageChanged = ::recreate,
                )
            }
        }
    }
}

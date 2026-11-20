package com.rawpos.scanner.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rawpos.scanner.core.AppInfo
import com.rawpos.scanner.core.settings.SettingsRepository
import com.rawpos.scanner.feature.connection.ConnectionViewModel
import com.rawpos.scanner.feature.diagnostics.DiagnosticsViewModel
import com.rawpos.scanner.feature.scanner.ScannerViewModel
import com.rawpos.scanner.feature.settings.SettingsViewModel

class AppViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val appInfo: AppInfo,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(settingsRepository, appInfo) as T

        modelClass.isAssignableFrom(ConnectionViewModel::class.java) ->
            ConnectionViewModel() as T

        modelClass.isAssignableFrom(ScannerViewModel::class.java) ->
            ScannerViewModel() as T

        modelClass.isAssignableFrom(DiagnosticsViewModel::class.java) ->
            DiagnosticsViewModel(appInfo) as T

        else -> error("No ViewModel registered for ${modelClass.name}")
    }
}

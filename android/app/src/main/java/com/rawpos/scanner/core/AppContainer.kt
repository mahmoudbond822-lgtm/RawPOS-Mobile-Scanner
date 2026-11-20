package com.rawpos.scanner.core

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.rawpos.scanner.core.settings.AndroidSettingsRepository
import com.rawpos.scanner.core.settings.SettingsRepository
import com.rawpos.scanner.core.viewmodel.AppViewModelFactory

/**
 * Manual composition root for Phase 1.
 *
 * The graph is deliberately small: one settings repository, one immutable AppInfo, and a
 * ViewModel factory. Hilt is not justified at this size; constructor injection keeps the
 * graph visible and testable.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = AndroidSettingsRepository(appContext)
    val appInfo: AppInfo = AppInfo.from(appContext)
    val viewModelFactory: ViewModelProvider.Factory =
        AppViewModelFactory(settingsRepository, appInfo)
}

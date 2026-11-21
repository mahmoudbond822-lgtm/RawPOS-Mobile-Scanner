package com.rawpos.scanner.core

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.rawpos.scanner.core.net.ClientScanTransportHolder
import com.rawpos.scanner.core.net.CredentialStore
import com.rawpos.scanner.core.net.KeystoreCredentialStore
import com.rawpos.scanner.core.settings.AndroidSettingsRepository
import com.rawpos.scanner.core.settings.SettingsRepository
import com.rawpos.scanner.core.transport.ScanTransport
import com.rawpos.scanner.core.viewmodel.AppViewModelFactory

/**
 * Manual composition root.
 *
 * The graph stays small and visible: settings, app info, the credential store, and — only when a
 * desktop has been paired — the scan transport. With no credentials the scanner runs exactly as it did
 * in Phase 3: local only, nothing leaves the phone.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = AndroidSettingsRepository(appContext)
    val appInfo: AppInfo = AppInfo.from(appContext)
    val credentialStore: CredentialStore = KeystoreCredentialStore(appContext)
    val scanTransport: ScanTransport? = ClientScanTransportHolder.from(credentialStore.load())
    val viewModelFactory: ViewModelProvider.Factory =
        AppViewModelFactory(settingsRepository, appInfo, scanTransport)
}

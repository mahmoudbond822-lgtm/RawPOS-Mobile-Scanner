package com.rawpos.scanner.core

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import com.rawpos.scanner.core.net.ClientScanTransportHolder
import com.rawpos.scanner.core.net.CredentialStore
import com.rawpos.scanner.core.net.KeystoreCredentialStore
import com.rawpos.scanner.core.net.OkHttpTransport
import com.rawpos.scanner.core.net.PairingClient
import com.rawpos.scanner.core.settings.AndroidSettingsRepository
import com.rawpos.scanner.core.settings.SettingsRepository
import com.rawpos.scanner.core.transport.ScanTransport
import com.rawpos.scanner.core.viewmodel.AppViewModelFactory

/**
 * Manual composition root.
 *
 * The graph stays small and visible: settings, app info, the credential store, and the two clients that
 * can reach a desktop — pairing and scanning. Nothing is constructed that could talk to anything except
 * the desktop whose certificate the phone has pinned.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = AndroidSettingsRepository(appContext)
    val appInfo: AppInfo = AppInfo.from(appContext)
    val credentialStore: CredentialStore = KeystoreCredentialStore(appContext)

    private val deviceId: String = DeviceIdentity.of(appContext)
    private var cachedTransport: ScanTransport? = null
    private var cachedToken: String? = null

    /** One transport per stored token; a re-pair invalidates it. */
    private val transportProvider: () -> ScanTransport? = {
        val credentials = credentialStore.load()
        if (credentials == null) {
            cachedTransport = null
            cachedToken = null
            null
        } else {
            if (cachedTransport == null || cachedToken != credentials.token) {
                cachedTransport = ClientScanTransportHolder.from(credentials)
                cachedToken = credentials.token
            }
            cachedTransport
        }
    }

    private val pairingClient: PairingClient = PairingClient(
        transportFor = { pin -> OkHttpTransport(pin, readTimeoutMillis = PAIRING_READ_TIMEOUT) },
        deviceId = deviceId,
        deviceName = Build.MODEL ?: "Android phone",
        appVersion = appInfo.versionName,
        androidApi = Build.VERSION.SDK_INT,
    )

    val viewModelFactory: ViewModelProvider.Factory = AppViewModelFactory(
        settingsRepository = settingsRepository,
        appInfo = appInfo,
        transportProvider = transportProvider,
        pairingClient = pairingClient,
        credentialStore = credentialStore,
    )

    private companion object {
        /** The desktop holds the pair request open while a human reads the code. */
        const val PAIRING_READ_TIMEOUT = 65_000L
    }
}

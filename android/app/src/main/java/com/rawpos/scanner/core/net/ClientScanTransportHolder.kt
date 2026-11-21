package com.rawpos.scanner.core.net

import com.rawpos.scanner.core.transport.ClientScanTransport
import com.rawpos.scanner.core.transport.ScanTransport

/**
 * Builds the real transport from stored credentials, or nothing at all.
 *
 * Keeping this in one place means the app has exactly one way to reach a desktop, and that way is
 * pinned. A phone without credentials gets `null`, and the scanner stays local.
 */
object ClientScanTransportHolder {

    fun from(credentials: Credentials?): ScanTransport? {
        if (credentials == null) {
            return null
        }
        val client = ScanClient(
            transport = OkHttpTransport(credentials.pin),
            endpoint = credentials.endpoint(),
            token = credentials.token,
        )
        return ClientScanTransport(client)
    }
}

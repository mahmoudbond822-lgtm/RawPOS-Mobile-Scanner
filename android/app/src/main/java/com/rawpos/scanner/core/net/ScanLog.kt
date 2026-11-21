package com.rawpos.scanner.core.net

/**
 * Diagnostics that never break the caller.
 *
 * `android.util.Log` is a stub in JVM unit tests and throws "not mocked", so calling it directly turns
 * a logged failure into a test failure. Logging must never be able to do that, so the call is guarded
 * and failures to log are swallowed on purpose.
 */
internal object ScanLog {
    private const val TAG = "RawPosScan"

    fun info(message: String) {
        runCatching { android.util.Log.i(TAG, message) }
    }

    fun warn(message: String, error: Throwable? = null) {
        runCatching { android.util.Log.w(TAG, message, error) }
    }
}

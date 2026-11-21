package com.rawpos.scanner.core.net

import java.security.SecureRandom
import java.util.Base64

/** Why pairing did not complete. Each maps to one sentence on the phone. */
enum class PairingError {
    DENIED,
    EXPIRED,
    IN_PROGRESS,
    REFUSED,
    UNREACHABLE,
    PIN_MISMATCH,
}

sealed interface PairingResult {
    data class Paired(val credentials: Credentials) : PairingResult

    data class Failed(val error: PairingError, val messageCode: String? = null) : PairingResult
}

/** The pairing operation, as the view model needs it. */
interface Pairer {
    suspend fun pair(rawUri: String): PairingResult
}

/**
 * The phone's half of pairing (protocol §4.3).
 *
 * The pin comes from the QR, so the TLS client is built *after* the QR is read — that is what makes the
 * first connection trustworthy. The desktop answers only after a human taps Approve, so the read
 * timeout here is deliberately longer than the scan route's.
 */
class PairingClient(
    private val transportFor: (pin: String) -> HttpTransport,
    private val deviceId: String,
    private val deviceName: String,
    private val appVersion: String,
    private val androidApi: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val maxAttempts: Int = 3,
    private val random: SecureRandom = SecureRandom(),
) : Pairer {
    override suspend fun pair(rawUri: String): PairingResult {
        val uri = PairingUri.parse(rawUri, nowEpochSeconds())
            ?: return PairingResult.Failed(
                if (rawUri.startsWith("${PairingUri.SCHEME}://")) {
                    PairingError.EXPIRED
                } else {
                    PairingError.REFUSED
                },
            )

        val transport = transportFor(uri.pin)
        val nonce = base64Url(random.generateSeed(32).copyOf(32))
        val body = requestBody(uri, nonce)

        repeat(maxAttempts) {
            val response = try {
                transport.execute(
                    HttpRequest(
                        url = "${uri.baseUrl()}/v1/pair",
                        headers = mapOf("Content-Type" to "application/json"),
                        body = body,
                    ),
                )
            } catch (_: Exception) {
                return PairingResult.Failed(PairingError.UNREACHABLE)
            }

            when (response.status) {
                200 -> {
                    val token = runCatching {
                        org.json.JSONObject(response.body).getString("token")
                    }.getOrNull() ?: return PairingResult.Failed(PairingError.REFUSED)
                    return PairingResult.Paired(
                        Credentials(
                            baseUrl = uri.baseUrl(),
                            deviceId = deviceId,
                            pin = uri.pin,
                            token = token,
                            desktopName = uri.desktopName,
                        ),
                    )
                }

                202 -> Unit // still waiting for the cashier; retry the identical body

                403 -> return PairingResult.Failed(PairingError.DENIED, messageCode(response.body))

                409 -> return PairingResult.Failed(
                    PairingError.IN_PROGRESS,
                    messageCode(response.body),
                )

                410 -> return PairingResult.Failed(PairingError.EXPIRED, messageCode(response.body))

                429, 500, 502, 503, 504 -> Unit

                else -> return PairingResult.Failed(PairingError.REFUSED, messageCode(response.body))
            }
        }
        return PairingResult.Failed(PairingError.REFUSED)
    }

    private fun requestBody(uri: PairingUri, nonce: String): String = org.json.JSONObject()
        .put("v", 1)
        .put("pairing_id", uri.pairingId)
        .put("secret", uri.secret)
        .put("device_id", deviceId)
        .put("device_name", deviceName)
        .put("client_nonce", nonce)
        .put("app_version", appVersion)
        .put("android_api", androidApi)
        .toString()

    private fun messageCode(body: String): String? = runCatching {
        org.json.JSONObject(body).optString("message_code").takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

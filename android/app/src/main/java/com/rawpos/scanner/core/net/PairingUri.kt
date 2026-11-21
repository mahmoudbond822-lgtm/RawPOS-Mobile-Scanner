package com.rawpos.scanner.core.net

import java.net.URI
import java.net.URLDecoder

/**
 * The pairing payload from the desktop's QR (protocol §4.2).
 *
 * It carries the endpoint, a one-time secret, and the pin. It carries **no token and no business
 * data**, which is why a photographed QR is useless without the desktop approval tap (A5).
 */
data class PairingUri(
    val host: String,
    val port: Int,
    val pairingId: String,
    val secret: String,
    val pin: String,
    val desktopId: String,
    val desktopName: String?,
    val expiresAtEpochSeconds: Long?,
) {
    fun baseUrl(): String = "https://$host:$port"

    companion object {
        const val SCHEME = "rawpos"
        const val HOST = "pair"

        /**
         * Parse a scanned string. Returns `null` for anything that is not a current RawPOS pairing QR,
         * including an expired one — a stale QR must fail with "expired", which the caller reports.
         */
        fun parse(raw: String, nowEpochSeconds: Long): PairingUri? {
            if (!raw.startsWith("$SCHEME://$HOST?")) {
                return null
            }
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val query = parseQuery(uri.rawQuery ?: return null)

            val host = query["host"]?.takeIf { it.isNotBlank() } ?: return null
            val port = query["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val pairingId = query["sid"]?.takeIf { it.length == 43 } ?: return null
            val secret = query["secret"]?.takeIf { it.length == 43 } ?: return null
            val pin = query["pin"]?.takeIf { it.length == 43 } ?: return null
            val desktopId = query["did"]?.takeIf { it.isNotBlank() } ?: return null
            if (query["v"] != "1") {
                return null
            }
            val expires = query["exp"]?.toLongOrNull()
            if (expires != null && expires <= nowEpochSeconds) {
                return null
            }
            return PairingUri(
                host = host,
                port = port,
                pairingId = pairingId,
                secret = secret,
                pin = pin,
                desktopId = desktopId,
                desktopName = query["name"],
                expiresAtEpochSeconds = expires,
            )
        }

        private fun parseQuery(rawQuery: String): Map<String, String> =
            rawQuery.split("&")
                .mapNotNull { pair ->
                    val index = pair.indexOf('=')
                    if (index <= 0) {
                        null
                    } else {
                        val key = URLDecoder.decode(pair.substring(0, index), "UTF-8")
                        val value = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
                        key to value
                    }
                }
                .toMap()
    }
}

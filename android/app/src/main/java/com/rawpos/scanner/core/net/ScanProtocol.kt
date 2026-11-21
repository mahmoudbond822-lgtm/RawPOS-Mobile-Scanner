package com.rawpos.scanner.core.net

import org.json.JSONObject

/** How the client must react to one response. */
internal enum class Reaction {
    /** The till decided; show it. */
    DECIDED,

    /** Nothing decided yet; retry the same event id. */
    RETRY,

    /** The token is not usable; re-pair. */
    UNAUTHORIZED,

    /** The desktop refused this body; retrying it is wrong. */
    REFUSED,
}

internal data class ParsedResponse(
    val reaction: Reaction,
    val delivery: Delivery?,
    val serverInstanceId: String?,
    val messageCode: String?,
)

/**
 * The JSON side of the wire contract, mirroring the shared fixtures.
 *
 * Parsing is deliberately forgiving about unknown extra fields (a newer desktop must not break an older
 * phone) and strict about the ones it relies on.
 */
object ScanProtocol {

    fun requestBody(event: ScanEvent, deviceId: String, context: String?): String {
        val json = JSONObject()
            .put("v", 1)
            .put("event_id", event.eventId)
            .put("device_id", deviceId)
            .put("sequence", event.sequence)
            .put("barcode", event.barcode)
            .put("format", event.format)
            .put("scanned_at", isoMillis(event.scannedAtMillis))
        if (context != null) {
            json.put("context", context)
        } else {
            json.put("context", JSONObject.NULL)
        }
        return json.toString()
    }

    internal fun parse(status: Int, body: String): ParsedResponse {
        val json = runCatching { JSONObject(body) }.getOrElse {
            return ParsedResponse(Reaction.RETRY, null, null, null)
        }
        val instance = json.optString("server_instance_id").takeIf { it.isNotEmpty() }
        val messageCode = json.optString("message_code").takeIf { it.isNotEmpty() }

        if (status == 401 || status == 403) {
            return ParsedResponse(Reaction.UNAUTHORIZED, null, instance, messageCode)
        }
        if (status == 409) {
            return ParsedResponse(Reaction.REFUSED, null, instance, messageCode)
        }
        if (status == 429 || status == 500 || status == 502 || status == 503 || status == 504) {
            return ParsedResponse(Reaction.RETRY, null, instance, messageCode)
        }
        if (status !in 200..299) {
            return ParsedResponse(Reaction.RETRY, null, instance, messageCode)
        }

        val scanStatus = json.optString("status")
        if (status == 202 || scanStatus == "unknown") {
            return ParsedResponse(Reaction.RETRY, null, instance, messageCode)
        }
        val delivery = Delivery.Decided(
            status = scanStatus,
            context = json.optString("context").takeIf { it.isNotEmpty() },
            originalStatus = json.optString("original_status").takeIf { it.isNotEmpty() },
            reasonCode = json.optString("reason_code").takeIf { it.isNotEmpty() },
            messageCode = messageCode,
        )
        return ParsedResponse(Reaction.DECIDED, delivery, instance, messageCode)
    }

    fun parseContext(body: String): ContextSnapshot? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return ContextSnapshot(
            checkoutActive = json.optBoolean("checkout_active", false),
            context = json.optString("context").takeIf { it.isNotEmpty() },
            serverInstanceId = json.optString("server_instance_id").takeIf { it.isNotEmpty() },
        )
    }

    /** RFC3339 UTC with milliseconds, as the desktop validates. */
    fun isoMillis(epochMillis: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(epochMillis))
    }
}

data class ContextSnapshot(
    val checkoutActive: Boolean,
    val context: String?,
    val serverInstanceId: String?,
)

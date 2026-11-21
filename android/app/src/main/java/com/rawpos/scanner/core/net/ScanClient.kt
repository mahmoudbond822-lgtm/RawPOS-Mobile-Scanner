package com.rawpos.scanner.core.net

import kotlinx.coroutines.delay

/**
 * Sends one scan at a time and reports only what the desktop actually decided.
 *
 * The rules come from the approved protocol (§5.4–§5.6) and the A12 decisions:
 *
 * - **one event id for the life of the retry**, because a new id is a new scan to the desktop (Q3);
 * - **retry only** on transport errors, 429, 5xx, and `unknown`;
 * - **stop on a refusal** — a business refusal is an answer, not a failure;
 * - **instance change means uncertain** — if `server_instance_id` moves between attempts, the desktop
 *   restarted, the event may or may not have landed, and the client never replays (D20);
 * - **bounded budget** — 15 s, then "expired" or "uncertain", never a hidden queue (D15).
 */
class ScanClient(
    private val transport: HttpTransport,
    private val endpoint: Endpoint,
    private val token: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val budgetMillis: Long = 15_000,
    private val maxAttempts: Int = 6,
    private val jitter: (Long) -> Long = { it },
) {
    suspend fun submit(event: ScanEvent, context: String?): Delivery {
        val started = nowMillis()
        var delayMillis = FIRST_DELAY_MILLIS
        var observedInstance: String? = null
        var reachedDesktop = false
        var lastMessage: String? = null

        repeat(maxAttempts) {
            val response = try {
                transport.execute(
                    HttpRequest(
                        url = "${endpoint.baseUrl}/v1/scans",
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "Authorization" to "Bearer $token",
                        ),
                        body = ScanProtocol.requestBody(event, endpoint.deviceId, context),
                    ),
                )
            } catch (error: Exception) {
                // Diagnostics only: the exception class, never the payload or the barcode.
                android.util.Log.w("RawPosScan", "attempt failed: ${error.javaClass.simpleName}", error)
                null
            }

            if (response != null) {
                reachedDesktop = true
                val parsed = ScanProtocol.parse(response.status, response.body)
                lastMessage = parsed.messageCode

                val instance = parsed.serverInstanceId
                if (instance != null) {
                    if (observedInstance == null) {
                        observedInstance = instance
                    } else if (instance != observedInstance) {
                        // The desktop restarted mid-retry. The event may have landed; never replay.
                        return Delivery.Failed(DeliveryError.UNCERTAIN, lastMessage)
                    }
                }

                when (parsed.reaction) {
                    Reaction.DECIDED -> return parsed.delivery
                        ?: Delivery.Failed(DeliveryError.UNCERTAIN, lastMessage)

                    Reaction.UNAUTHORIZED -> return Delivery.Failed(
                        DeliveryError.UNAUTHORIZED,
                        lastMessage,
                    )

                    Reaction.NOT_READY -> return Delivery.Failed(
                        DeliveryError.NOT_READY,
                        lastMessage,
                    )

                    Reaction.REFUSED -> return Delivery.Failed(DeliveryError.REFUSED, lastMessage)

                    Reaction.RETRY -> Unit
                }
            }

            val elapsed = nowMillis() - started
            if (elapsed + delayMillis >= budgetMillis) {
                return Delivery.Failed(
                    if (reachedDesktop) DeliveryError.UNCERTAIN else DeliveryError.EXPIRED,
                    lastMessage,
                )
            }
            delay(jitter(delayMillis))
            delayMillis = minOf(delayMillis * 2, MAX_DELAY_MILLIS)
        }

        return Delivery.Failed(
            if (reachedDesktop) DeliveryError.UNCERTAIN else DeliveryError.EXPIRED,
            lastMessage,
        )
    }

    suspend fun fetchContext(): ContextSnapshot? = try {
        val response = transport.execute(
            HttpRequest(
                url = "${endpoint.baseUrl}/v1/context",
                headers = mapOf("Authorization" to "Bearer $token"),
                body = "",
            ),
        )
        if (response.status == 200) ScanProtocol.parseContext(response.body) else null
    } catch (error: Exception) {
        android.util.Log.w("RawPosScan", "context fetch failed: ${error.javaClass.simpleName}", error)
        null
    }

    companion object {
        const val FIRST_DELAY_MILLIS = 250L
        const val MAX_DELAY_MILLIS = 4_000L
    }
}

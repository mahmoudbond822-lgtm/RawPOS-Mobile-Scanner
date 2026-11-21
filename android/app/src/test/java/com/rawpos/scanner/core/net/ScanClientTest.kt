package com.rawpos.scanner.core.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retry and uncertainty rules (D15/D20, decisions Q3/Q4/Q6).
 *
 * Time is virtual: `runTest` skips the delays, so the 15 s budget is exercised in milliseconds.
 */
class ScanClientTest {

    private class ScriptedTransport(
        private vararg val responses: () -> HttpResponse,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        private var index = 0

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val current = responses.getOrNull(index) ?: { throw TransportException("offline") }
            index += 1
            return current()
        }
    }

    private fun ok(body: String) = { HttpResponse(200, body) }
    private fun status(code: Int, body: String = "{}") = { HttpResponse(code, body) }

    private fun client(transport: HttpTransport, clock: () -> Long = { 0L }) = ScanClient(
        transport = transport,
        endpoint = Endpoint("https://127.0.0.1:47653", "device-1", "pin"),
        token = "rps1_token",
        nowMillis = clock,
    )

    private val event = ScanEvent(
        eventId = "ce5c8b7d-0d36-4fd7-b4e2-054f6a1604b1",
        barcode = "0036000291452",
        format = "ean_13",
        sequence = 7,
        scannedAtMillis = 1_700_000_000_000,
    )

    @Test
    fun acceptedScanIsReportedAsDecided() = runTest {
        val transport = ScriptedTransport(
            ok("""{"v":1,"status":"added","context":"ctx","server_instance_id":"i1"}"""),
        )

        val delivery = client(transport).submit(event, "ctx")

        assertTrue(delivery is Delivery.Decided)
        assertEquals("added", (delivery as Delivery.Decided).status)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun retryAfterATransportErrorReusesTheSameEventId() = runTest {
        val transport = ScriptedTransport(
            { throw TransportException("timeout") },
            ok("""{"v":1,"status":"added","context":"ctx","server_instance_id":"i1"}"""),
        )

        client(transport).submit(event, "ctx")

        assertEquals(2, transport.requests.size)
        val ids = transport.requests.map { org.json.JSONObject(it.body).getString("event_id") }
        assertEquals(setOf(event.eventId), ids.toSet())
    }

    @Test
    fun unknownThenSuccessIsDecided() = runTest {
        val transport = ScriptedTransport(
            status(202, """{"v":1,"status":"unknown","server_instance_id":"i1"}"""),
            ok("""{"v":1,"status":"merged","context":"ctx","server_instance_id":"i1"}"""),
        )

        val delivery = client(transport).submit(event, "ctx")

        assertEquals("merged", (delivery as Delivery.Decided).status)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun persistentUnknownBecomesUncertain() = runTest {
        val transport = ScriptedTransport(
            *Array(8) { status(202, """{"v":1,"status":"unknown","server_instance_id":"i1"}""") },
        )

        val delivery = client(transport).submit(event, "ctx")

        assertEquals(DeliveryError.UNCERTAIN, (delivery as Delivery.Failed).error)
    }

    @Test
    fun offlineFromTheStartBecomesExpired() = runTest {
        val transport = ScriptedTransport(*Array(8) { { throw TransportException("offline") } })

        val delivery = client(transport).submit(event, "ctx")

        assertEquals(DeliveryError.EXPIRED, (delivery as Delivery.Failed).error)
    }

    @Test
    fun unauthorizedStopsImmediately() = runTest {
        val transport = ScriptedTransport(
            status(401, """{"v":1,"status":"unauthorized","error":"invalid_token"}"""),
        )

        val delivery = client(transport).submit(event, "ctx")

        assertEquals(DeliveryError.UNAUTHORIZED, (delivery as Delivery.Failed).error)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun conflictIsARefusalNotARetry() = runTest {
        val transport = ScriptedTransport(
            status(409, """{"v":1,"status":"event_id_conflict","error":"event_id_conflict"}"""),
        )

        val delivery = client(transport).submit(event, "ctx")

        assertEquals(DeliveryError.REFUSED, (delivery as Delivery.Failed).error)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun rateLimitIsRetried() = runTest {
        val transport = ScriptedTransport(
            status(429, """{"v":1,"status":"rate_limited","error":"rate_limited"}"""),
            ok("""{"v":1,"status":"added","context":"ctx","server_instance_id":"i1"}"""),
        )

        val delivery = client(transport).submit(event, "ctx")

        assertTrue(delivery is Delivery.Decided)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun instanceChangeMidRetryBecomesUncertainAndStops() = runTest {
        val transport = ScriptedTransport(
            status(503, """{"v":1,"status":"service_unavailable","server_instance_id":"old-instance"}"""),
            ok("""{"v":1,"status":"added","context":"ctx","server_instance_id":"new-instance"}"""),
            ok("""{"v":1,"status":"added","context":"ctx","server_instance_id":"new-instance"}"""),
        )

        val delivery = client(transport).submit(event, "ctx")

        assertEquals(DeliveryError.UNCERTAIN, (delivery as Delivery.Failed).error)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun contextIsFetched() = runTest {
        val transport = ScriptedTransport(
            ok("""{"v":1,"checkout_active":true,"context":"ctx","server_instance_id":"i1"}"""),
        )

        val snapshot = client(transport).fetchContext()

        assertEquals(true, snapshot?.checkoutActive)
        assertEquals("ctx", snapshot?.context)
    }
}

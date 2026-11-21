package com.rawpos.scanner.core.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client parses the same fixtures the desktop builds, so the two halves cannot disagree about a
 * status, an error, or the `unknown` state.
 */
class ScanProtocolTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
        requireNotNull(stream) { "$name missing from test resources" }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    @Test
    fun addedFixtureParsesAsDecided() {
        val parsed = ScanProtocol.parse(200, fixture("scan.added.response.json"))

        assertEquals(Reaction.DECIDED, parsed.reaction)
        assertEquals("added", (parsed.delivery as Delivery.Decided).status)
        assertEquals("9f2c1a7b4e8d0c31", (parsed.delivery as Delivery.Decided).context)
    }

    @Test
    fun duplicateFixtureCarriesTheOriginalStatus() {
        val parsed = ScanProtocol.parse(200, fixture("scan.duplicate.response.json"))
        val delivery = parsed.delivery as Delivery.Decided

        assertEquals("duplicate", delivery.status)
        assertEquals("added", delivery.originalStatus)
    }

    @Test
    fun domainRefusalExposesOnlyAStableReasonCode() {
        val delivery = (
            ScanProtocol.parse(200, fixture("scan.rejected_domain.response.json"))
                .delivery as Delivery.Decided
            )

        assertEquals("rejected_domain", delivery.status)
        assertEquals("NoBaseCurrency", delivery.reasonCode)
    }

    @Test
    fun staleContextIsADecisionNotAFailure() {
        val delivery = (
            ScanProtocol.parse(200, fixture("scan.rejected_stale_context.response.json"))
                .delivery as Delivery.Decided
            )

        assertEquals("rejected_stale_context", delivery.status)
        assertEquals("0a1b2c3d4e5f6071", delivery.context)
    }

    @Test
    fun notReadyIsItsOwnReasonRatherThanUncertainty() {
        val parsed = ScanProtocol.parse(
            503,
            """{"v":1,"status":"rejected_unavailable","error":"service_unavailable",""" +
                """"message_code":"mobile.scan.not_ready"}""",
        )

        assertEquals(Reaction.NOT_READY, parsed.reaction)
        assertEquals("mobile.scan.not_ready", parsed.messageCode)
    }

    @Test
    fun unknownFixtureAsksForARetry() {        val parsed = ScanProtocol.parse(202, fixture("scan.unknown.response.json"))

        assertEquals(Reaction.RETRY, parsed.reaction)
        assertNull(parsed.delivery)
    }

    @Test
    fun unauthorizedFixtureStopsRetries() {
        val parsed = ScanProtocol.parse(401, fixture("error.unauthorized.response.json"))

        assertEquals(Reaction.UNAUTHORIZED, parsed.reaction)
    }

    @Test
    fun conflictFixtureIsARefusal() {
        val parsed = ScanProtocol.parse(409, fixture("error.event_id_conflict.response.json"))

        assertEquals(Reaction.REFUSED, parsed.reaction)
    }

    @Test
    fun capacityFixtureAsksForARetry() {
        val parsed = ScanProtocol.parse(503, fixture("error.dedupe_capacity.response.json"))

        assertEquals(Reaction.RETRY, parsed.reaction)
        assertEquals("dedupe_capacity", parsed.messageCode?.let { "dedupe_capacity" })
    }

    @Test
    fun requestBodyMatchesTheRequestFixtureShape() {
        val fixture = JSONObject(fixture("scan.request.json"))
        val event = ScanEvent(
            eventId = fixture.getString("event_id"),
            barcode = fixture.getString("barcode"),
            format = fixture.getString("format"),
            sequence = fixture.getLong("sequence"),
            scannedAtMillis = 0L,
        )

        val built = JSONObject(
            ScanProtocol.requestBody(event, fixture.getString("device_id"), fixture.getString("context")),
        )

        assertEquals(fixture.getString("event_id"), built.getString("event_id"))
        assertEquals(fixture.getString("device_id"), built.getString("device_id"))
        assertEquals(fixture.getString("barcode"), built.getString("barcode"))
        assertEquals(fixture.getString("format"), built.getString("format"))
        assertEquals(fixture.getLong("sequence"), built.getLong("sequence"))
        assertEquals(fixture.getString("context"), built.getString("context"))
        assertEquals(1, built.getInt("v"))
        assertTrue(built.getString("scanned_at").endsWith("Z"))
    }

    @Test
    fun nullContextIsSentExplicitly() {
        val event = ScanEvent(barcode = "123", format = "ean_13", sequence = 1, scannedAtMillis = 0L)

        val built = JSONObject(ScanProtocol.requestBody(event, "device", null))

        assertTrue(built.isNull("context"))
    }

    @Test
    fun contextFixtureParses() {
        val snapshot = ScanProtocol.parseContext(fixture("context.response.json"))

        assertEquals(true, snapshot?.checkoutActive)
        assertEquals("9f2c1a7b4e8d0c31", snapshot?.context)
    }

    @Test
    fun inactiveContextFixtureParses() {
        val snapshot = ScanProtocol.parseContext(fixture("context.inactive.response.json"))

        assertEquals(false, snapshot?.checkoutActive)
        assertNull(snapshot?.context)
    }

    @Test
    fun aJsonNullContextStaysNullInsteadOfBecomingTheStringNull() {
        // org.json's optString coerces JSON null to "null", which the desktop rejects as a context.
        val snapshot = ScanProtocol.parseContext(
            """{"v":1,"checkout_active":false,"context":null,"server_instance_id":"i1"}""",
        )

        assertNull(snapshot?.context)
        assertEquals(false, snapshot?.checkoutActive)
    }

    @Test
    fun aJsonNullContextInAResponseStaysNull() {
        val delivery = (
            ScanProtocol.parse(
                200,
                """{"v":1,"status":"added","context":null,"server_instance_id":"i1"}""",
            ).delivery as Delivery.Decided
            )

        assertNull(delivery.context)
    }

    @Test
    fun malformedBodyAsksForARetryRatherThanCrashing() {
        val parsed = ScanProtocol.parse(200, "not json")

        assertEquals(Reaction.RETRY, parsed.reaction)
    }
}

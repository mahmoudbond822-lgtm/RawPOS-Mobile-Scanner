package com.rawpos.scanner.core.protocol

import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract fixtures shared with the desktop receiver.
 *
 * Two jobs:
 *
 * 1. **Drift protection.** Every fixture must hash to the value recorded in `manifest.json`. If one
 *    side edits a field without regenerating the manifest, this fails here and in the desktop build,
 *    rather than in a shop.
 * 2. **Shape assertions.** The fields the client depends on must exist with the documented types.
 *
 * The fixtures are read from the repository's `protocol/fixtures` directory (wired as test resources),
 * so there is exactly one copy of the contract.
 */
class ContractFixturesTest {

    private fun bytes(name: String): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
        assertNotNull("fixture not on the test classpath: $name", stream)
        return stream!!.readBytes()
    }

    private fun json(name: String): JSONObject =
        JSONObject(String(bytes(name), Charsets.UTF_8))

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }

    @Test
    fun everyFixtureMatchesTheManifest() {
        val manifest = json("manifest.json")
        assertEquals(1, manifest.getInt("version"))
        val files = manifest.getJSONObject("files")
        assertTrue("manifest lists no fixtures", files.length() > 0)

        val names = files.keys().asSequence().toList()
        for (name in names) {
            assertEquals("hash mismatch for $name", files.getString(name), sha256(bytes(name)))
        }
    }

    @Test
    fun everyFixtureDeclaresProtocolVersionOne() {
        val files = json("manifest.json").getJSONObject("files")
        for (name in files.keys().asSequence()) {
            assertEquals("missing v in $name", 1, json(name).getInt("v"))
        }
    }

    @Test
    fun scanRequestCarriesTheContractFields() {
        val request = json("scan.request.json")

        assertEquals(1, request.getInt("v"))
        assertTrue(request.getString("event_id").isNotBlank())
        assertTrue(request.getString("device_id").isNotBlank())
        assertTrue(request.getLong("sequence") >= 0)
        assertTrue(request.getString("barcode").matches(Regex("^[0-9]{1,64}$")))
        assertTrue(
            request.getString("format") in setOf(
                "ean_13", "ean_8", "upc_a", "upc_e", "code_128", "code_39", "itf", "qr", "unknown",
            ),
        )
        assertTrue(request.getString("scanned_at").endsWith("Z"))
        assertTrue(request.has("context"))
    }

    @Test
    fun scanResponsesCarryStatusAndContext() {
        val cases = mapOf(
            "scan.added.response.json" to "added",
            "scan.merged.response.json" to "merged",
            "scan.not_found.response.json" to "not_found",
            "scan.rejected_stale_context.response.json" to "rejected_stale_context",
            "scan.rejected_domain.response.json" to "rejected_domain",
            "scan.duplicate.response.json" to "duplicate",
            "scan.unknown.response.json" to "unknown",
        )
        for ((name, status) in cases) {
            val payload = json(name)
            assertEquals(name, status, payload.getString("status"))
            assertTrue("$name lacks server_instance_id", payload.has("server_instance_id"))
        }
    }

    @Test
    fun duplicateCarriesTheOriginalStatus() {
        val payload = json("scan.duplicate.response.json")

        assertEquals("duplicate", payload.getString("status"))
        assertTrue(payload.getString("original_status").isNotBlank())
    }

    @Test
    fun rejectedDomainCarriesOnlyAStableReasonCode() {
        val payload = json("scan.rejected_domain.response.json")

        assertTrue(payload.getString("reason_code").matches(Regex("^[A-Za-z][A-Za-z0-9]{0,40}$")))
        assertTrue(
            "domain rejection must not leak a message body",
            !payload.getString("message_code").contains(" "),
        )
    }

    @Test
    fun contextFixturesCoverBothActiveAndInactive() {
        val active = json("context.response.json")
        assertTrue(active.getBoolean("checkout_active"))
        assertTrue(active.getString("context").isNotBlank())

        val inactive = json("context.inactive.response.json")
        assertTrue(!inactive.getBoolean("checkout_active"))
        assertTrue(inactive.isNull("context"))
    }

    @Test
    fun errorFixturesCarryASTableCode() {
        val names = listOf(
            "error.unauthorized.response.json",
            "error.event_id_conflict.response.json",
            "error.dedupe_capacity.response.json",
        )
        for (name in names) {
            val payload = json(name)
            assertTrue("$name lacks error", payload.getString("error").isNotBlank())
            assertTrue("$name lacks message_code", payload.getString("message_code").isNotBlank())
        }
    }
}

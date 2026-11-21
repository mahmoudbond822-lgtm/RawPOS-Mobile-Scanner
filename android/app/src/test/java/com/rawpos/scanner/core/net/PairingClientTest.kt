package com.rawpos.scanner.core.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's half of pairing: one POST, retried while the cashier decides. */
class PairingClientTest {

    private class ScriptedTransport(private vararg val responses: () -> HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        private var index = 0

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val current = responses.getOrNull(index) ?: { HttpResponse(500, "{}") }
            index += 1
            return current()
        }
    }

    private val uri = "rawpos://pair?v=1&host=192.168.1.20&port=47653" +
        "&sid=" + "P".repeat(43) +
        "&secret=" + "S".repeat(43) +
        "&pin=" + "zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU" +
        "&did=desk-0001&name=Front&exp=2000000000"

    private fun client(transport: HttpTransport) = PairingClient(
        transportFor = { transport },
        deviceId = "device-1",
        deviceName = "Cashier phone",
        appVersion = "1.0.0",
        androidApi = 31,
        nowEpochSeconds = { 1_900_000_000 },
    )

    @Test
    fun approvalReturnsCredentialsBoundToThePin() = runTest {
        val transport = ScriptedTransport(
            { HttpResponse(200, """{"v":1,"token":"rps1_abc"}""") },
        )

        val result = client(transport).pair(uri)

        assertTrue(result is PairingResult.Paired)
        val credentials = (result as PairingResult.Paired).credentials
        assertEquals("rps1_abc", credentials.token)
        assertEquals("https://192.168.1.20:47653", credentials.baseUrl)
        assertEquals("zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU", credentials.pin)
        assertEquals("device-1", credentials.deviceId)
    }

    @Test
    fun waitingRetriesTheIdenticalBody() = runTest {
        val transport = ScriptedTransport(
            { HttpResponse(202, """{"v":1,"state":"waiting"}""") },
            { HttpResponse(200, """{"v":1,"token":"rps1_abc"}""") },
        )

        client(transport).pair(uri)

        assertEquals(2, transport.requests.size)
        assertEquals(transport.requests[0].body, transport.requests[1].body)
        assertTrue(transport.requests[0].url.endsWith("/v1/pair"))
    }

    @Test
    fun deniedIsReportedAsDenied() = runTest {
        val transport = ScriptedTransport(
            { HttpResponse(403, """{"v":1,"error":"pairing_denied"}""") },
        )

        val result = client(transport).pair(uri)

        assertEquals(PairingError.DENIED, (result as PairingResult.Failed).error)
    }

    @Test
    fun expiredIsReportedAsExpired() = runTest {
        val transport = ScriptedTransport(
            { HttpResponse(410, """{"v":1,"error":"pairing_expired"}""") },
        )

        val result = client(transport).pair(uri)

        assertEquals(PairingError.EXPIRED, (result as PairingResult.Failed).error)
    }

    @Test
    fun unreachableIsReportedRatherThanHanging() = runTest {
        val transport = ScriptedTransport({ throw TransportException("refused") })

        val result = client(transport).pair(uri)

        assertEquals(PairingError.UNREACHABLE, (result as PairingResult.Failed).error)
    }

    @Test
    fun anExpiredQrIsRefusedBeforeAnyRequest() = runTest {
        val transport = ScriptedTransport({ HttpResponse(200, "{}") })
        val expired = uri.replace("exp=2000000000", "exp=1000")

        val result = client(transport).pair(expired)

        assertEquals(PairingError.EXPIRED, (result as PairingResult.Failed).error)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun theRequestBodyCarriesTheContractFields() = runTest {
        val transport = ScriptedTransport(
            { HttpResponse(200, """{"v":1,"token":"rps1_abc"}""") },
        )

        client(transport).pair(uri)

        val body = org.json.JSONObject(transport.requests.first().body)
        assertEquals(1, body.getInt("v"))
        assertEquals("P".repeat(43), body.getString("pairing_id"))
        assertEquals("S".repeat(43), body.getString("secret"))
        assertEquals("device-1", body.getString("device_id"))
        assertEquals(43, body.getString("client_nonce").length)
        assertEquals(31, body.getInt("android_api"))
    }
}

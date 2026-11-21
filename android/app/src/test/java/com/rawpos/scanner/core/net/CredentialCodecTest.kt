package com.rawpos.scanner.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The stored credential payload, round-tripped without the Keystore (which needs a device). */
class CredentialCodecTest {

    private val credentials = Credentials(
        baseUrl = "https://192.168.1.20:47653",
        deviceId = "5f0c2f7e-6a1e-4a6e-9a2b-2f6f5f2b9c11",
        pin = "zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU",
        token = "rps1_" + "A".repeat(43),
        desktopName = "RawPOS - Front Counter",
    )

    @Test
    fun roundTripPreservesEveryField() {
        assertEquals(credentials, CredentialCodec.decode(CredentialCodec.encode(credentials)))
    }

    @Test
    fun endpointCarriesThePathAndIdentity() {
        val endpoint = credentials.endpoint()

        assertEquals("https://192.168.1.20:47653", endpoint.baseUrl)
        assertEquals(credentials.deviceId, endpoint.deviceId)
        assertEquals(credentials.pin, endpoint.pin)
    }

    @Test
    fun desktopNameIsOptional() {
        val anonymous = credentials.copy(desktopName = null)

        assertEquals(anonymous, CredentialCodec.decode(CredentialCodec.encode(anonymous)))
    }

    @Test
    fun malformedPayloadIsRejectedRatherThanGuessed() {
        assertNull(CredentialCodec.decode("not json"))
        assertNull(CredentialCodec.decode("""{"base_url":"x"}"""))
    }
}

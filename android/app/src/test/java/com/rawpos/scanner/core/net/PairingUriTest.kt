package com.rawpos.scanner.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parsing the desktop's pairing QR. Anything malformed or expired must be refused, not guessed. */
class PairingUriTest {

    private val valid =
        "rawpos://pair?v=1&host=192.168.1.20&port=47653" +
            "&sid=" + "P".repeat(43) +
            "&secret=" + "S".repeat(43) +
            "&pin=" + "zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU" +
            "&did=desk-0001&name=RawPOS%20-%20Front%20Counter&exp=2000000000"

    @Test
    fun `valid payload parses`() {
        val uri = PairingUri.parse(valid, nowEpochSeconds = 1_900_000_000)

        assertEquals("192.168.1.20", uri?.host)
        assertEquals(47653, uri?.port)
        assertEquals("https://192.168.1.20:47653", uri?.baseUrl())
        assertEquals("desk-0001", uri?.desktopId)
        assertEquals("RawPOS - Front Counter", uri?.desktopName)
        assertEquals(43, uri?.pin?.length)
    }

    @Test
    fun `expired payload is refused`() {
        assertNull(PairingUri.parse(valid, nowEpochSeconds = 2_000_000_001))
    }

    @Test
    fun `wrong scheme or host is refused`() {
        assertNull(PairingUri.parse(valid.replace("rawpos://", "https://"), 0))
        assertNull(PairingUri.parse(valid.replace("pair?", "login?"), 0))
    }

    @Test
    fun `unsupported version is refused`() {
        assertNull(PairingUri.parse(valid.replace("v=1", "v=2"), 0))
    }

    @Test
    fun `missing or malformed fields are refused`() {
        assertNull(PairingUri.parse(valid.replace("&pin=" + "zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU", ""), 0))
        assertNull(PairingUri.parse(valid.replace("port=47653", "port=99999"), 0))
        assertNull(PairingUri.parse(valid.replace("sid=" + "P".repeat(43), "sid=short"), 0))
        assertNull(PairingUri.parse("not a uri", 0))
    }

    @Test
    fun `name is optional`() {
        val withoutName = valid.substringBefore("&name=") + "&exp=2000000000"

        val uri = PairingUri.parse(withoutName, nowEpochSeconds = 1_900_000_000)

        assertEquals("192.168.1.20", uri?.host)
        assertNull(uri?.desktopName)
    }
}

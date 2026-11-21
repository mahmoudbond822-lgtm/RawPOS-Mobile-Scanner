package com.rawpos.scanner.core.net

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pin must equal what the desktop computes (`mobile/tls.py`, `spki_pin`), or pairing will fail in
 * a shop with an unhelpful error.
 *
 * The certificate is a fixed test fixture; its pin was produced by the desktop's own algorithm in
 * Python, so this test compares two independent implementations rather than a number the phone
 * generated for itself.
 */
class SpkiPinTest {

    private fun certificate(): X509Certificate {
        val stream = javaClass.classLoader!!.getResourceAsStream("pinned-test-cert.pem")
        requireNotNull(stream) { "pinned-test-cert.pem missing from test resources" }
        return stream.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }

    @Test
    fun pinMatchesTheDesktopAlgorithm() {
        assertEquals("zYzwWOVoxUrCExm5ZEsUWA6ymg0nxppAXjCKr5hUhpU", SpkiPin.of(certificate()))
    }

    @Test
    fun pinIsBase64UrlWithoutPadding() {
        val pin = SpkiPin.of(certificate())

        assertEquals(43, pin.length)
        assertTrue(pin.matches(Regex("^[A-Za-z0-9_-]{43}$")))
    }

    @Test
    fun matchingPinIsAccepted() {
        val manager = SpkiPin.trustManager(SpkiPin.of(certificate()))

        manager.checkServerTrusted(arrayOf(certificate()), "EC")
    }

    @Test
    fun differentPinIsRefused() {
        val manager = SpkiPin.trustManager("A".repeat(43))

        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(arrayOf(certificate()), "EC")
        }
    }

    @Test
    fun noCertificateIsRefusedRatherThanTrusted() {
        val manager = SpkiPin.trustManager(SpkiPin.of(certificate()))

        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(emptyArray(), "EC")
        }
    }

    @Test
    fun constantTimeComparisonReportsMismatch() {
        assertTrue(SpkiPin.matches(certificate(), SpkiPin.of(certificate())))
        assertFalse(SpkiPin.matches(certificate(), "B".repeat(43)))
    }
}

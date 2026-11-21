package com.rawpos.scanner.core.net

import android.annotation.SuppressLint
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The desktop's identity, expressed the only way the phone can verify it: the SHA-256 of the
 * certificate's SubjectPublicKeyInfo.
 *
 * This must produce exactly what the desktop computes (`mobile/tls.py`, `spki_pin`): base64url of the
 * SHA-256 of the DER SPKI, no padding. A mismatch is not a warning — it aborts the connection.
 */
object SpkiPin {

    fun of(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun matches(certificate: X509Certificate, expected: String): Boolean {
        // Constant-time comparison: the pin is not secret, but treating it casually invites a habit.
        return MessageDigest.isEqual(
            of(certificate).toByteArray(Charsets.US_ASCII),
            expected.toByteArray(Charsets.US_ASCII),
        )
    }

    /** A trust manager that accepts exactly one SPKI pin and never falls back to the system store. */
    @SuppressLint("CustomX509TrustManager")
    fun trustManager(expected: String): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("client certificates are not accepted")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // Lint flags custom trust managers because most of them disable verification.
                // This one verifies exactly one thing - the desktop's public key - and throws on
                // everything else. There is no accept-any path and no system-store fallback.
                val leaf = chain?.firstOrNull()
                    ?: throw CertificateException("no server certificate")
                if (!matches(leaf, expected)) {
                    // Fails closed. There is no "trust anyway" path anywhere in this client.
                    throw CertificateException("desktop identity changed: pin mismatch")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

    /** A TLS 1.2+ context whose only trust anchor is the pin. */
    fun socketFactory(expected: String): SSLSocketFactory {
        val manager = trustManager(expected)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(manager), null)
        return context.socketFactory
    }

    /**
     * Current time of the certificate, used only to fail early on an expired peer.
     * Kept separate so tests can assert pin behaviour without a clock.
     */
    fun isCurrentlyValid(certificate: X509Certificate, nowMillis: Long): Boolean =
        nowMillis in certificate.notBefore.time..certificate.notAfter.time

    /** Convenience for diagnostics: the Android trust store, used only when no pin is configured. */
    fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}

package com.rawpos.scanner.core.transport

/**
 * Test double for [ScanTransport].
 *
 * It records what a scanner would have sent so a test can assert "exactly one event per physical
 * presentation" without a desktop, a network, or a protocol. It lives in the test source set on
 * purpose: no fake transport ships inside the APK.
 */
class FakeTransport : ScanTransport {
    private val _submitted = mutableListOf<String>()

    val submitted: List<String> get() = _submitted.toList()

    override suspend fun submit(barcode: String) {
        _submitted += barcode
    }
}

package com.rawpos.scanner.core.transport

import com.rawpos.scanner.core.net.Delivery
import com.rawpos.scanner.core.net.DeliveryError

/**
 * Test double for [ScanTransport].
 *
 * It records what a scanner would have sent so a test can assert "exactly one event per physical
 * presentation" without a desktop, a network, or a protocol. It lives in the test source set on
 * purpose: no fake transport ships inside the APK.
 */
class FakeTransport(
    private var reply: Delivery = Delivery.Decided(status = "added", context = "context-context1"),
) : ScanTransport {

    private val _submitted = mutableListOf<Pair<String, String>>()

    val submitted: List<String> get() = _submitted.map { it.first }

    val submittedFormats: List<String> get() = _submitted.map { it.second }

    fun replyWith(delivery: Delivery) {
        reply = delivery
    }

    fun fail(error: DeliveryError = DeliveryError.UNCERTAIN) {
        reply = Delivery.Failed(error)
    }

    override suspend fun submit(barcode: String, format: String): Delivery {
        _submitted += barcode to format
        return reply
    }
}

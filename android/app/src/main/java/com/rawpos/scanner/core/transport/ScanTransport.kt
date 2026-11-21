package com.rawpos.scanner.core.transport

import com.rawpos.scanner.core.net.Delivery
import com.rawpos.scanner.core.net.ScanClient
import com.rawpos.scanner.core.net.ScanEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * The seam between scanning and sending.
 *
 * Phase 3 had a fake here. Phase 4 supplies [ClientScanTransport]; tests keep using
 * [com.rawpos.scanner.core.transport.FakeTransport] so scanning stays testable without a desktop.
 */
interface ScanTransport {
    /** Submit one accepted barcode and report what the desktop decided. */
    suspend fun submit(barcode: String, format: String): Delivery
}

/**
 * Adapts the protocol client to the scanner.
 *
 * It owns two pieces of state the protocol requires: a monotonic sequence (diagnostics only) and the
 * last checkout context the desktop returned. The context is refreshed lazily and updated from every
 * decision, so a rejected scan reports the current token and the cashier's rescan uses it.
 */
class ClientScanTransport(
    private val client: ScanClient,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ScanTransport {

    private val sequence = AtomicLong(0)
    private var context: String? = null
    private var primed = false

    override suspend fun submit(barcode: String, format: String): Delivery {
        if (!primed) {
            context = client.fetchContext()?.context
            primed = true
        }
        val event = ScanEvent.forFormat(
            barcode = barcode,
            format = format,
            sequence = sequence.incrementAndGet(),
            nowMillis = nowMillis(),
        )
        val delivery = client.submit(event, context)
        if (delivery is Delivery.Decided) {
            delivery.context?.let { context = it }
        }
        return delivery
    }
}

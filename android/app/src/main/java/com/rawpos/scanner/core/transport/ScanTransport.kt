package com.rawpos.scanner.core.transport

/**
 * The seam between scanning and sending.
 *
 * Phase 3 has no network. The interface exists so the scanner can be exercised end to end against a
 * fake, and so Phase 4 can add the real TLS client behind the same call without touching the camera
 * or the gate. Payload shape, event identity, retries, and outcomes are Phase 4 concerns.
 */
interface ScanTransport {
    /** Submit one accepted barcode. Called at most once per physical presentation. */
    suspend fun submit(barcode: String)
}

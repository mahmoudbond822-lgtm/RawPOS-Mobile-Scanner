package com.rawpos.scanner.core.barcode

/**
 * Barcode symbologies the app can be asked to decode.
 *
 * The enabled set is a **default**, approved provisionally (decision D14 / A11, 2026-11-21). It is a
 * setting, not an architecture: a shop that uses UPC-E or ITF enables them after a real-label check.
 * RawPOS stores only ASCII digits, so no format may produce a non-digit payload on the product path.
 */
enum class BarcodeFormat {
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    CODE_39,
    CODE_128,
    ITF,
    QR,
    UNKNOWN,
}

/**
 * Which formats are decoded during product scanning.
 *
 * [BarcodeFormat.QR] is deliberately absent: QR is enabled only in pairing mode, where the payload is
 * a pairing URI rather than a product barcode.
 */
object FormatPolicy {
    val enabledForProductScanning: Set<BarcodeFormat> = setOf(
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.CODE_128,
    )

    fun isEnabledForProductScanning(format: BarcodeFormat): Boolean =
        format in enabledForProductScanning
}

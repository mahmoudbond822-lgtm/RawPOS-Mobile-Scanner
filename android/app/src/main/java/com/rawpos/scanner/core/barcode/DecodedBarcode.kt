package com.rawpos.scanner.core.barcode

/** One symbol decoded from one camera frame, before validation. */
data class DecodedBarcode(
    val rawValue: String,
    val format: BarcodeFormat,
)

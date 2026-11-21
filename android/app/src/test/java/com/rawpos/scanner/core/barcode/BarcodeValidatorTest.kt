package com.rawpos.scanner.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeValidatorTest {

    private fun accepted(value: String, format: BarcodeFormat) {
        assertEquals(
            ValidationResult.Accepted(value = value, format = format),
            BarcodeValidator.validate(value, format),
        )
    }

    private fun rejected(expected: RejectionReason, value: String, format: BarcodeFormat) {
        assertEquals(
            ValidationResult.Rejected(expected),
            BarcodeValidator.validate(value, format),
        )
    }

    @Test
    fun `accepts every enabled numeric symbology`() {
        accepted("6291041500213", BarcodeFormat.EAN_13)
        accepted("96385074", BarcodeFormat.EAN_8)
        accepted("036000291452", BarcodeFormat.UPC_A)
        accepted("12345", BarcodeFormat.CODE_128)
    }

    @Test
    fun `preserves leading zeros`() {
        accepted("000123", BarcodeFormat.CODE_128)
        accepted("012345678905", BarcodeFormat.UPC_A)
    }

    @Test
    fun `accepts the length boundaries`() {
        accepted("7", BarcodeFormat.CODE_128)
        accepted("1".repeat(BarcodeValidator.MAX_DIGITS), BarcodeFormat.CODE_128)
    }

    @Test
    fun `rejects one digit past the cap`() {
        rejected(
            RejectionReason.TOO_LONG,
            "1".repeat(BarcodeValidator.MAX_DIGITS + 1),
            BarcodeFormat.CODE_128,
        )
    }

    @Test
    fun `rejects an empty payload`() {
        rejected(RejectionReason.EMPTY, "", BarcodeFormat.EAN_13)
    }

    @Test
    fun `rejects whitespace instead of trimming it`() {
        rejected(RejectionReason.NON_DIGITS, " 6291041500213", BarcodeFormat.EAN_13)
        rejected(RejectionReason.NON_DIGITS, "6291041500213 ", BarcodeFormat.EAN_13)
        rejected(RejectionReason.NON_DIGITS, "629 1041", BarcodeFormat.EAN_13)
    }

    @Test
    fun `rejects non-ASCII digits instead of normalizing them`() {
        rejected(RejectionReason.NON_DIGITS, "\u0661\u0662\u0663", BarcodeFormat.EAN_13)
        rejected(RejectionReason.NON_DIGITS, "\uFF11\uFF12\uFF13", BarcodeFormat.EAN_13)
    }

    @Test
    fun `rejects letters and punctuation`() {
        rejected(RejectionReason.NON_DIGITS, "ABC123", BarcodeFormat.CODE_128)
        rejected(RejectionReason.NON_DIGITS, "12-34", BarcodeFormat.CODE_39)
    }

    @Test
    fun `rejects disabled symbologies even when the payload is numeric`() {
        val disabled = listOf(
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_39,
            BarcodeFormat.ITF,
            BarcodeFormat.QR,
            BarcodeFormat.UNKNOWN,
        )
        for (format in disabled) {
            rejected(RejectionReason.FORMAT_DISABLED, "1234567890", format)
        }
    }

    @Test
    fun `rejects a text QR payload as non-digits before the format check`() {
        rejected(
            RejectionReason.NON_DIGITS,
            "https://example.invalid/pair",
            BarcodeFormat.QR,
        )
    }

    @Test
    fun `the enabled set excludes pairing and carton formats`() {
        assertEquals(
            setOf(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.CODE_128,
            ),
            FormatPolicy.enabledForProductScanning,
        )
    }
}

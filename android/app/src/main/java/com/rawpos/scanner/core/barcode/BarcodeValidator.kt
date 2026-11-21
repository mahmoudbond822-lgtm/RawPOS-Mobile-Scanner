package com.rawpos.scanner.core.barcode

/** Why a decoded payload was refused. The UI maps each reason to one localized sentence. */
enum class RejectionReason {
    /** Nothing was decoded, or the decoded string was empty. */
    EMPTY,

    /** Anything that is not an ASCII digit, including whitespace, Arabic-Indic digits, or letters. */
    NON_DIGITS,

    /** More than [BarcodeValidator.MAX_DIGITS] digits. */
    TOO_LONG,

    /** The symbology is disabled for product scanning (UPC-E, Code 39, ITF, QR). */
    FORMAT_DISABLED,
}

/** Outcome of validating one decoded payload. */
sealed interface ValidationResult {
    data class Accepted(val value: String, val format: BarcodeFormat) : ValidationResult

    data class Rejected(val reason: RejectionReason) : ValidationResult
}

/**
 * The full predicate from `docs/architecture/barcode-scanning.md` §3.
 *
 * It accepts `^[0-9]{1,64}$`, full-string, on an enabled symbology. Two deliberate non-behaviours:
 *
 * - **No trimming.** A payload with whitespace is rejected, not cleaned.
 * - **No Unicode normalization.** Arabic-Indic or full-width digits are rejected, not converted.
 *
 * Leading zeros are preserved because the value is never parsed as a number. There is no check-digit
 * rejection: ML Kit already validates the symbology checksum, and RawPOS treats barcodes as opaque.
 */
object BarcodeValidator {
    const val MAX_DIGITS: Int = 64

    fun validate(rawValue: String, format: BarcodeFormat): ValidationResult {
        if (rawValue.isEmpty()) {
            return ValidationResult.Rejected(RejectionReason.EMPTY)
        }
        if (!rawValue.all { it in '0'..'9' }) {
            return ValidationResult.Rejected(RejectionReason.NON_DIGITS)
        }
        if (rawValue.length > MAX_DIGITS) {
            return ValidationResult.Rejected(RejectionReason.TOO_LONG)
        }
        if (!FormatPolicy.isEnabledForProductScanning(format)) {
            return ValidationResult.Rejected(RejectionReason.FORMAT_DISABLED)
        }
        return ValidationResult.Accepted(value = rawValue, format = format)
    }
}

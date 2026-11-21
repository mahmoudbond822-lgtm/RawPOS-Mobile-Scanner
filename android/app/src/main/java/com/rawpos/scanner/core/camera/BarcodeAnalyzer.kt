package com.rawpos.scanner.core.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rawpos.scanner.core.barcode.BarcodeFormat
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.FormatPolicy

/**
 * Camera frame in, decoded symbols out.
 *
 * It deliberately does no business filtering: mapping a symbology, and deciding whether a payload is
 * acceptable, belong to [com.rawpos.scanner.core.barcode.BarcodeValidator]. The analyzer's only jobs
 * are running ML Kit, translating its format constants, and closing the frame exactly once.
 *
 * The scanner is created with only the enabled formats so ML Kit does not waste cycles decoding
 * symbologies we would reject anyway.
 */
class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val paused: () -> Boolean,
    private val onDecoded: (List<DecodedBarcode>) -> Unit,
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (paused()) {
            image.close()
            return
        }
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                onDecoded(barcodes.map { it.toDecoded() })
            }
            .addOnFailureListener {
                // A single failed frame is not a scan. The gate never sees it.
                onDecoded(emptyList())
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    companion object {
        /** ML Kit options restricted to the provisionally approved product formats (D14/A11). */
        fun scannerOptions(): BarcodeScannerOptions =
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_CODE_128,
                )
                .build()

        /** ML Kit options for pairing mode: QR only, because product formats are disabled for QR. */
        fun pairingOptions(): BarcodeScannerOptions =
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

        /** Translate an ML Kit format constant into the app's own enum. */
        fun toFormat(value: Int): BarcodeFormat = when (value) {
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_ITF -> BarcodeFormat.ITF
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR
            else -> BarcodeFormat.UNKNOWN
        }
    }
}

private fun Barcode.toDecoded(): DecodedBarcode =
    DecodedBarcode(
        rawValue = rawValue.orEmpty(),
        format = BarcodeAnalyzer.toFormat(format),
    )

/** True when the symbology may reach the validator at all. Kept for the analyzer's options. */
internal fun BarcodeFormat.isProductFormat(): Boolean =
    FormatPolicy.isEnabledForProductScanning(this)

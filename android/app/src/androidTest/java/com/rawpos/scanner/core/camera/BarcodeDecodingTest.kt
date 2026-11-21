package com.rawpos.scanner.core.camera

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rawpos.scanner.core.barcode.BarcodeFormat
import com.rawpos.scanner.core.barcode.BarcodeValidator
import com.rawpos.scanner.core.barcode.ValidationResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that the bundled ML Kit model really decodes a retail barcode on this device.
 *
 * The image is a generated EAN-13 for `6291041500213`, checked in as a test asset, so the test is
 * deterministic and needs neither a camera nor a physical label. It closes the gap left by the
 * camera-permission restriction: the decode path and the format mapping are real, not mocked.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeDecodingTest {

    @Test
    fun decodes_a_generated_ean13_image() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("ean13-6291041500213.png").use { stream ->
            assertNotNull("test asset missing", stream)
            BitmapFactory.decodeStream(stream)
        }
        assertNotNull("asset did not decode to a bitmap", bitmap)

        val scanner = BarcodeScanning.getClient(BarcodeAnalyzer.scannerOptions())
        val latch = CountDownLatch(1)
        var decoded: List<Barcode> = emptyList()
        try {
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { bars ->
                    decoded = bars
                    latch.countDown()
                }
                .addOnFailureListener { latch.countDown() }

            assertTrue("ML Kit did not finish in time", latch.await(20, TimeUnit.SECONDS))
        } finally {
            scanner.close()
        }

        assertEquals("one symbol expected", 1, decoded.size)
        val bar = decoded.first()
        assertEquals("6291041500213", bar.rawValue)
        assertEquals(BarcodeFormat.EAN_13, BarcodeAnalyzer.toFormat(bar.format))

        val accepted = BarcodeValidator.validate(bar.rawValue.orEmpty(), BarcodeFormat.EAN_13)
        assertTrue("validator rejected a real EAN-13", accepted is ValidationResult.Accepted)
    }
}

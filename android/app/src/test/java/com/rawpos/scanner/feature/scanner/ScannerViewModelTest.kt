package com.rawpos.scanner.feature.scanner

import com.rawpos.scanner.core.barcode.BarcodeFormat
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.RejectionReason
import com.rawpos.scanner.core.barcode.ScanGate
import com.rawpos.scanner.core.net.Delivery
import com.rawpos.scanner.core.net.DeliveryError
import com.rawpos.scanner.core.transport.FakeTransport
import com.rawpos.scanner.core.transport.ScanTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun frame(vararg values: String, format: BarcodeFormat = BarcodeFormat.EAN_13) =
        values.map { DecodedBarcode(rawValue = it, format = format) }

    @Test
    fun `a stable valid code is recorded once`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val viewModel = ScannerViewModel(transport = transport, gate = ScanGate())

        viewModel.onFrame(frame("6291041500213"), 0L)
        viewModel.onFrame(frame("6291041500213"), 100L)
        advanceUntilIdle()

        assertEquals("6291041500213", viewModel.state.value.lastCode)
        assertEquals(ScannerStatus.SENT, viewModel.state.value.status)
        assertEquals(listOf("6291041500213"), transport.submitted)
    }

    @Test
    fun `without a transport the scan stays local`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(transport = null)

        viewModel.onFrame(frame("123456"), 0L)
        viewModel.onFrame(frame("123456"), 100L)

        assertEquals("123456", viewModel.state.value.lastCode)
        assertEquals(SubmissionStatus.LOCAL_ONLY, viewModel.state.value.submission)
    }

    @Test
    fun `a decided scan shows the desktop status`() = runTest(dispatcher) {
        val transport = FakeTransport()
        transport.replyWith(Delivery.Decided(status = "merged", context = "c"))
        val viewModel = ScannerViewModel(transport = transport)

        viewModel.onFrame(frame("123456"), 0L)
        viewModel.onFrame(frame("123456"), 100L)
        advanceUntilIdle()

        assertEquals(SubmissionStatus.DECIDED, viewModel.state.value.submission)
        assertEquals("merged", viewModel.state.value.decidedStatus)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `an uncertain delivery is reported as a failure, not success`() = runTest(dispatcher) {
        val transport = FakeTransport()
        transport.fail(DeliveryError.UNCERTAIN)
        val viewModel = ScannerViewModel(transport = transport)

        viewModel.onFrame(frame("123456"), 0L)
        viewModel.onFrame(frame("123456"), 100L)
        advanceUntilIdle()

        assertEquals(SubmissionStatus.FAILED, viewModel.state.value.submission)
        assertEquals("UNCERTAIN", viewModel.state.value.failure)
        assertNull(viewModel.state.value.decidedStatus)
    }

    @Test
    fun `a second scan is dropped while one is in flight`() = runTest(dispatcher) {
        val hanging = object : ScanTransport {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            override suspend fun submit(barcode: String, format: String): Delivery {
                calls += 1
                release.await()
                return Delivery.Decided("added", null)
            }
        }
        val viewModel = ScannerViewModel(transport = hanging)

        viewModel.onFrame(frame("111"), 0L)
        viewModel.onFrame(frame("111"), 100L)
        assertEquals(SubmissionStatus.WAITING, viewModel.state.value.submission)

        viewModel.onFrame(frame("222"), 200L)
        viewModel.onFrame(frame("222"), 300L)
        advanceUntilIdle()
        assertEquals(1, hanging.calls)

        hanging.release.complete(Unit)
        advanceUntilIdle()
        assertEquals(SubmissionStatus.DECIDED, viewModel.state.value.submission)
    }

    @Test
    fun `a non-numeric payload is rejected and never submitted`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val viewModel = ScannerViewModel(transport = transport)

        viewModel.onFrame(frame("ABC-123", format = BarcodeFormat.CODE_128), 0L)
        viewModel.onFrame(frame("ABC-123", format = BarcodeFormat.CODE_128), 100L)
        advanceUntilIdle()

        assertNull(viewModel.state.value.lastCode)
        assertEquals(RejectionReason.NON_DIGITS, viewModel.state.value.rejection)
        assertTrue(transport.submitted.isEmpty())
    }

    @Test
    fun `a disabled symbology is rejected even with digits`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel()

        viewModel.onFrame(frame("12345678", format = BarcodeFormat.UPC_E), 0L)

        assertEquals(RejectionReason.FORMAT_DISABLED, viewModel.state.value.rejection)
        assertNull(viewModel.state.value.lastCode)
    }

    @Test
    fun `two different codes in one frame report a framing problem`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel()

        viewModel.onFrame(frame("111", "222"), 0L)

        assertEquals(ScannerStatus.MULTIPLE_CODES, viewModel.state.value.status)
        assertNull(viewModel.state.value.lastCode)
    }

    @Test
    fun `pause stops emitting and resume restarts`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val viewModel = ScannerViewModel(transport = transport)

        viewModel.togglePause()
        viewModel.onFrame(frame("123456"), 0L)
        viewModel.onFrame(frame("123456"), 100L)
        advanceUntilIdle()
        assertTrue(transport.submitted.isEmpty())

        viewModel.togglePause()
        viewModel.onFrame(frame("123456"), 200L)
        viewModel.onFrame(frame("123456"), 300L)
        advanceUntilIdle()

        assertEquals(listOf("123456"), transport.submitted)
    }

    @Test
    fun `torch and zoom are exposed to the screen`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel()

        viewModel.setTorch(true)
        assertTrue(viewModel.state.value.torchOn)

        viewModel.toggleZoom()
        assertTrue(viewModel.state.value.zoomOn)
    }

    @Test
    fun `camera start moves the status out of idle`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel()

        viewModel.onCameraStarted()

        assertEquals(ScannerStatus.SCANNING, viewModel.state.value.status)
    }

    @Test
    fun `the wire format matches the protocol names`() {
        assertEquals("ean_13", BarcodeFormat.EAN_13.wireName())
        assertEquals("code_128", BarcodeFormat.CODE_128.wireName())
        assertEquals("unknown", BarcodeFormat.UNKNOWN.wireName())
    }
}

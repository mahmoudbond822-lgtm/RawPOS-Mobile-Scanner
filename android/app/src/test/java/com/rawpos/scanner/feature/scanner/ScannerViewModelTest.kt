package com.rawpos.scanner.feature.scanner

import com.rawpos.scanner.core.barcode.BarcodeFormat
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.RejectionReason
import com.rawpos.scanner.core.barcode.ScanGate
import com.rawpos.scanner.core.transport.FakeTransport
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
    fun `without a transport the scan still shows on screen`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(transport = null)

        viewModel.onFrame(frame("123456"), 0L)
        viewModel.onFrame(frame("123456"), 100L)

        assertEquals("123456", viewModel.state.value.lastCode)
        assertEquals(ScannerStatus.SENT, viewModel.state.value.status)
    }

    @Test
    fun `a non-numeric payload is rejected and never emitted`() = runTest(dispatcher) {
        val transport = FakeTransport()
        val viewModel = ScannerViewModel(transport = transport)

        viewModel.onFrame(
            frame("ABC-123", format = BarcodeFormat.CODE_128),
            0L,
        )
        viewModel.onFrame(
            frame("ABC-123", format = BarcodeFormat.CODE_128),
            100L,
        )
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
        assertEquals(ScannerStatus.SENT, viewModel.state.value.status)
    }

    @Test
    fun `torch state is exposed to the screen`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel()

        viewModel.setTorch(true)
        assertTrue(viewModel.state.value.torchOn)

        viewModel.setTorch(false)
        assertEquals(false, viewModel.state.value.torchOn)
    }

    @Test
    fun `camera start moves the status out of idle`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel()

        viewModel.onCameraStarted()

        assertEquals(ScannerStatus.SCANNING, viewModel.state.value.status)
    }
}

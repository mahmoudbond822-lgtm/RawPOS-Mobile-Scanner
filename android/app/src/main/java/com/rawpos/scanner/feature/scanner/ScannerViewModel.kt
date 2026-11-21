package com.rawpos.scanner.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rawpos.scanner.core.barcode.BarcodeFormat
import com.rawpos.scanner.core.barcode.BarcodeValidator
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.RejectionReason
import com.rawpos.scanner.core.barcode.ScanDecision
import com.rawpos.scanner.core.barcode.ScanGate
import com.rawpos.scanner.core.barcode.ValidationResult
import com.rawpos.scanner.core.net.Delivery
import com.rawpos.scanner.core.transport.ScanTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScannerStatus {
    IDLE,
    SCANNING,
    PAUSED,
    SENT,
    MULTIPLE_CODES,
}

/** What the desktop said, or why it said nothing. */
enum class SubmissionStatus {
    /** No desktop is paired, so nothing leaves the phone. */
    LOCAL_ONLY,

    /** Paired and idle: a scan may be submitted. */
    IDLE,

    WAITING,
    DECIDED,
    FAILED,
}

data class ScannerUiState(
    val status: ScannerStatus = ScannerStatus.IDLE,
    val lastCode: String? = null,
    val rejection: RejectionReason? = null,
    val torchOn: Boolean = false,
    val zoomOn: Boolean = false,
    val scanCount: Int = 0,
    val submission: SubmissionStatus = SubmissionStatus.LOCAL_ONLY,
    val decidedStatus: String? = null,
    val failure: String? = null,
)

/**
 * Scanner state for the screen.
 *
 * It owns the candidate gate, the last accepted value, and an optional transport. With no transport it
 * behaves exactly as Phase 3 did: the scan is shown locally and nothing leaves the device. With one, a
 * scan is submitted and the screen reports **only what the desktop decided** — never an optimistic
 * "added" (D13).
 *
 * One scan is in flight at a time. While waiting, a new emit is dropped rather than queued (D15).
 */
class ScannerViewModel(
    private val transport: ScanTransport? = null,
    private val gate: ScanGate = ScanGate(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScannerUiState(
            submission = if (transport == null) {
                SubmissionStatus.LOCAL_ONLY
            } else {
                SubmissionStatus.IDLE
            },
        ),
    )
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    fun onCameraStarted() {
        _state.update { it.copy(status = ScannerStatus.SCANNING) }
    }

    /** One analyzed frame. Values are validated first; only accepted digits reach the gate. */
    fun onFrame(decoded: List<DecodedBarcode>, nowMillis: Long) {
        val accepted = LinkedHashMap<String, BarcodeFormat>()
        var lastRejection: RejectionReason? = null
        for (barcode in decoded) {
            when (val result = BarcodeValidator.validate(barcode.rawValue, barcode.format)) {
                is ValidationResult.Accepted -> accepted[result.value] = result.format
                is ValidationResult.Rejected -> lastRejection = result.reason
            }
        }
        val rejection = lastRejection
        if (rejection != null) {
            _state.update { it.copy(rejection = rejection) }
        }

        when (val decision = gate.onFrame(accepted.keys, nowMillis)) {
            ScanDecision.MultipleCodes ->
                _state.update { it.copy(status = ScannerStatus.MULTIPLE_CODES) }

            is ScanDecision.Emit -> {
                if (_state.value.submission == SubmissionStatus.WAITING) {
                    // One in flight. A second event here would be a hidden queue.
                    return
                }
                submit(decision.value, accepted[decision.value] ?: BarcodeFormat.UNKNOWN)
            }

            ScanDecision.None -> Unit
        }
    }

    private fun submit(value: String, format: BarcodeFormat) {
        _state.update {
            it.copy(
                lastCode = value,
                status = ScannerStatus.SENT,
                rejection = null,
                scanCount = it.scanCount + 1,
            )
        }
        val sender = transport
        if (sender == null) {
            return
        }
        _state.update { it.copy(submission = SubmissionStatus.WAITING, decidedStatus = null, failure = null) }
        viewModelScope.launch {
            val delivery = runCatching { sender.submit(value, format.wireName()) }.getOrNull()
            when (delivery) {
                is Delivery.Decided -> _state.update {
                    it.copy(submission = SubmissionStatus.DECIDED, decidedStatus = delivery.status)
                }

                is Delivery.Failed -> _state.update {
                    it.copy(submission = SubmissionStatus.FAILED, failure = delivery.error.name)
                }

                null -> _state.update {
                    it.copy(submission = SubmissionStatus.FAILED, failure = "UNCERTAIN")
                }
            }
        }
    }

    fun togglePause() {
        if (gate.isPaused) {
            gate.resume()
            _state.update { it.copy(status = ScannerStatus.SCANNING) }
        } else {
            gate.pause()
            _state.update { it.copy(status = ScannerStatus.PAUSED) }
        }
    }

    fun setTorch(enabled: Boolean) {
        _state.update { it.copy(torchOn = enabled) }
    }

    fun toggleZoom() {
        _state.update { it.copy(zoomOn = !it.zoomOn) }
    }
}

/** The wire value for a symbology, as the protocol names it. */
fun BarcodeFormat.wireName(): String = when (this) {
    BarcodeFormat.EAN_13 -> "ean_13"
    BarcodeFormat.EAN_8 -> "ean_8"
    BarcodeFormat.UPC_A -> "upc_a"
    BarcodeFormat.UPC_E -> "upc_e"
    BarcodeFormat.CODE_39 -> "code_39"
    BarcodeFormat.CODE_128 -> "code_128"
    BarcodeFormat.ITF -> "itf"
    BarcodeFormat.QR -> "qr"
    BarcodeFormat.UNKNOWN -> "unknown"
}

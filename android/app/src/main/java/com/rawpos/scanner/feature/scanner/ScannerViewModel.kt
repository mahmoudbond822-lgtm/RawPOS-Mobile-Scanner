package com.rawpos.scanner.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rawpos.scanner.core.barcode.BarcodeValidator
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.RejectionReason
import com.rawpos.scanner.core.barcode.ScanDecision
import com.rawpos.scanner.core.barcode.ScanGate
import com.rawpos.scanner.core.barcode.ValidationResult
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

data class ScannerUiState(
    val status: ScannerStatus = ScannerStatus.IDLE,
    val lastCode: String? = null,
    val rejection: RejectionReason? = null,
    val torchOn: Boolean = false,
    val zoomOn: Boolean = false,
    val scanCount: Int = 0,
)

/**
 * Scanner state for the screen.
 *
 * It owns three things: the candidate gate, the last accepted value, and an optional transport. In
 * Phase 3 [transport] is `null` — there is no network — so a scan is recorded on screen and nothing
 * leaves the device. Phase 4 supplies the real transport without changing this class.
 */
class ScannerViewModel(
    private val transport: ScanTransport? = null,
    private val gate: ScanGate = ScanGate(),
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    val isPaused: Boolean get() = gate.isPaused

    fun onCameraStarted() {
        _state.update { it.copy(status = ScannerStatus.SCANNING) }
    }

    /** One analyzed frame. Values are validated first; only accepted digits reach the gate. */
    fun onFrame(decoded: List<DecodedBarcode>, nowMillis: Long) {
        val accepted = LinkedHashSet<String>()
        var lastRejection: RejectionReason? = null
        for (barcode in decoded) {
            when (val result = BarcodeValidator.validate(barcode.rawValue, barcode.format)) {
                is ValidationResult.Accepted -> accepted += result.value
                is ValidationResult.Rejected -> lastRejection = result.reason
            }
        }
        val rejection = lastRejection
        if (rejection != null) {
            _state.update { it.copy(rejection = rejection) }
        }

        when (val decision = gate.onFrame(accepted, nowMillis)) {
            ScanDecision.MultipleCodes ->
                _state.update { it.copy(status = ScannerStatus.MULTIPLE_CODES) }

            is ScanDecision.Emit -> {
                _state.update {
                    it.copy(
                        lastCode = decision.value,
                        status = ScannerStatus.SENT,
                        rejection = null,
                        scanCount = it.scanCount + 1,
                    )
                }
                val sink = transport
                if (sink != null) {
                    viewModelScope.launch { sink.submit(decision.value) }
                }
            }

            ScanDecision.None -> Unit
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

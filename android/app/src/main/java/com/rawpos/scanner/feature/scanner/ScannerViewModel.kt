package com.rawpos.scanner.feature.scanner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rawpos.scanner.core.barcode.BarcodeFormat
import com.rawpos.scanner.core.barcode.BarcodeValidator
import com.rawpos.scanner.core.barcode.DecodedBarcode
import com.rawpos.scanner.core.barcode.RejectionReason
import com.rawpos.scanner.core.barcode.ScanDecision
import com.rawpos.scanner.core.barcode.ScanGate
import com.rawpos.scanner.core.barcode.ValidationResult
import com.rawpos.scanner.core.net.CredentialStore
import com.rawpos.scanner.core.net.Delivery
import com.rawpos.scanner.core.net.Pairer
import com.rawpos.scanner.core.net.PairingError
import com.rawpos.scanner.core.net.PairingResult
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

/** Pairing is a mode of the scanner, because the QR needs the camera. */
enum class PairingStatus {
    OFF,
    WAITING_FOR_CODE,
    PAIRING,
    PAIRED,
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
    val pairing: PairingStatus = PairingStatus.OFF,
    val pairedDesktop: String? = null,
    val pairingError: String? = null,
)

/**
 * Scanner state for the screen.
 *
 * Two modes share one screen because both need the camera:
 *
 * - **product scanning** (default) validates digits, runs the gate, and submits at most one event at a
 *   time, reporting only what the desktop decided (D13);
 * - **pairing** decodes the desktop's QR, posts it, and stores the returned credentials (D3/A5).
 *
 * With no transport configured it behaves exactly as Phase 3: local only, nothing leaves the device.
 */
class ScannerViewModel(
    private val transportProvider: () -> ScanTransport? = { null },
    private val pairingClient: Pairer? = null,
    private val credentialStore: CredentialStore? = null,
    private val gate: ScanGate = ScanGate(),
) : ViewModel() {

    private companion object {
        /** Diagnostics only: outcome and error class, never a barcode. */
        const val TAG = "RawPosScan"
    }

    private val _state = MutableStateFlow(
        ScannerUiState(
            submission = if (transportProvider() == null) {
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

    /** True only while the pairing QR is being sought; once paired, product scanning resumes. */
    fun isPairingMode(): Boolean =
        _state.value.pairing == PairingStatus.WAITING_FOR_CODE ||
            _state.value.pairing == PairingStatus.PAIRING

    fun startPairing() {
        if (pairingClient == null) {
            _state.update {
                it.copy(pairing = PairingStatus.FAILED, pairingError = PairingError.REFUSED.name)
            }
            return
        }
        _state.update {
            it.copy(pairing = PairingStatus.WAITING_FOR_CODE, pairingError = null, pairedDesktop = null)
        }
    }

    fun cancelPairing() {
        _state.update { it.copy(pairing = PairingStatus.OFF, pairingError = null) }
    }

    /** One analyzed frame. Values are validated first; only accepted digits reach the gate. */
    fun onFrame(decoded: List<DecodedBarcode>, nowMillis: Long) {
        if (isPairingMode()) {
            decoded.firstOrNull { it.rawValue.startsWith("${com.rawpos.scanner.core.net.PairingUri.SCHEME}://") }
                ?.let { pair(it.rawValue) }
            return
        }

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

    private fun pair(raw: String) {
        if (_state.value.pairing == PairingStatus.PAIRING) {
            return
        }
        val client = pairingClient ?: return
        _state.update { it.copy(pairing = PairingStatus.PAIRING, pairingError = null) }
        viewModelScope.launch {
            when (val result = client.pair(raw)) {
                is PairingResult.Paired -> {
                    credentialStore?.save(result.credentials)
                    _state.update {
                        it.copy(
                            pairing = PairingStatus.PAIRED,
                            pairedDesktop = result.credentials.desktopName,
                            submission = SubmissionStatus.IDLE,
                        )
                    }
                }

                is PairingResult.Failed -> _state.update {
                    it.copy(pairing = PairingStatus.FAILED, pairingError = result.error.name)
                }
            }
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
        val sender = transportProvider()
        if (sender == null) {
            Log.i(TAG, "scan not sent: no desktop transport")
            return
        }
        _state.update {
            it.copy(submission = SubmissionStatus.WAITING, decidedStatus = null, failure = null)
        }
        viewModelScope.launch {
            val delivery = runCatching { sender.submit(value, format.wireName()) }
                .onFailure { Log.w(TAG, "scan submission failed", it) }
                .getOrNull()
            Log.i(TAG, "delivery=$delivery")
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

    fun togglePairing() {
        if (_state.value.pairing == PairingStatus.OFF) {
            startPairing()
        } else {
            cancelPairing()
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

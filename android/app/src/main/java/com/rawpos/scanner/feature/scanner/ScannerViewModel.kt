package com.rawpos.scanner.feature.scanner

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScannerStatus {
    IDLE,
    PHASE_ONE,
}

data class ScannerUiState(
    val lastCode: String? = null,
    val status: ScannerStatus = ScannerStatus.PHASE_ONE,
    val cameraEnabled: Boolean = false,
)

class ScannerViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()
}

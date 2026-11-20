package com.rawpos.scanner.feature.connection

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus {
    NOT_PAIRED,
    PHASE_ONE,
}

data class ConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.PHASE_ONE,
    val pairingEnabled: Boolean = false,
)

class ConnectionViewModel : ViewModel() {
    private val _state = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()
}

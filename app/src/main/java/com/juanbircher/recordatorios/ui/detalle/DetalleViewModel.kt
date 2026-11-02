package com.juanbircher.recordatorios.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Busca en el repositorio el recordatorio cuyo id llegó por navegación. */
class DetalleViewModel(
    private val recordatorioId: Long,
    private val repositorio: RecordatoriosRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetalleUiState>(DetalleUiState.Cargando)
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    private fun cargar() {
        _uiState.update { DetalleUiState.Cargando }
        viewModelScope.launch {
            val estado = try {
                repositorio.obtenerRecordatorio(recordatorioId)
                    ?.let { DetalleUiState.ConDatos(it) }
                    ?: DetalleUiState.NoEncontrado
            } catch (_: IOException) {
                DetalleUiState.Error
            }
            _uiState.update { estado }
        }
    }
}

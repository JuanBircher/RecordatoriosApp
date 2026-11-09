package com.juanbircher.recordatorios.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.domain.EstadoRecordatorio
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/**
 * Observa en el repositorio el recordatorio cuyo id llegó por navegación y resuelve sus acciones
 * (completar o reabrir, eliminar). Como observa Room, la pantalla se actualiza sola cuando el registro
 * cambia, incluido su estado de sincronización.
 */
class DetalleViewModel(
    private val recordatorioId: Long,
    private val repositorio: RecordatoriosRepository,
    private val hoy: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetalleUiState>(DetalleUiState.Cargando)
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    private var observacion: Job? = null

    /** Mientras se elimina, que la fila desaparezca de la base no debe mostrarse como «no encontrado». */
    private var eliminando = false

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    /** Completa un recordatorio pendiente; si ya estaba completado o cancelado, lo vuelve a pendiente. */
    fun onAlternarCompletado() {
        val actual = _uiState.value as? DetalleUiState.ConDatos ?: return
        val recordatorio = actual.recordatorio
        val nuevoEstado = if (recordatorio.estado is EstadoRecordatorio.Pendiente) {
            EstadoRecordatorio.Completado(hoy())
        } else {
            EstadoRecordatorio.Pendiente
        }
        viewModelScope.launch {
            try {
                repositorio.actualizarRecordatorio(recordatorio.copy(estado = nuevoEstado))
            } catch (_: IOException) {
                avisarErrorDeAccion()
            }
        }
    }

    fun onEliminar() {
        if (_uiState.value !is DetalleUiState.ConDatos || eliminando) return
        eliminando = true
        viewModelScope.launch {
            try {
                repositorio.eliminarRecordatorio(recordatorioId)
                _uiState.update { DetalleUiState.Eliminado }
            } catch (_: IOException) {
                eliminando = false
                avisarErrorDeAccion()
            }
        }
    }

    private fun avisarErrorDeAccion() {
        _uiState.update { actual -> if (actual is DetalleUiState.ConDatos) actual.copy(errorAccion = true) else actual }
    }

    private fun cargar() {
        observacion?.cancel()
        _uiState.update { DetalleUiState.Cargando }
        observacion = viewModelScope.launch {
            try {
                repositorio.observarRecordatorio(recordatorioId).collect { recordatorio ->
                    _uiState.update { actual ->
                        when {
                            actual is DetalleUiState.Eliminado -> actual
                            recordatorio == null && eliminando -> actual
                            recordatorio == null -> DetalleUiState.NoEncontrado
                            else -> DetalleUiState.ConDatos(recordatorio)
                        }
                    }
                }
            } catch (_: IOException) {
                _uiState.update { DetalleUiState.Error }
            }
        }
    }
}

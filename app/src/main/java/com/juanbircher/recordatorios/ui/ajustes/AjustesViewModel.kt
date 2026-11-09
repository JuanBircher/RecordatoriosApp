package com.juanbircher.recordatorios.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Preferencias que se muestran y si el último cambio no pudo guardarse. */
data class AjustesUiState(
    val preferencias: Preferencias = Preferencias(),
    val errorAlGuardar: Boolean = false
)

/** Lee las preferencias y las modifica con funciones suspendidas, fuera del hilo principal. */
class AjustesViewModel(private val repositorio: RecordatoriosRepository) : ViewModel() {

    private val errorAlGuardar = MutableStateFlow(false)

    val uiState: StateFlow<AjustesUiState> = combine(repositorio.preferencias, errorAlGuardar) { prefs, error ->
        AjustesUiState(prefs, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState())

    fun onTema(tema: TemaApp) = guardar { repositorio.actualizarTema(tema) }

    fun onMostrarCompletados(mostrar: Boolean) = guardar { repositorio.actualizarMostrarCompletados(mostrar) }

    fun onOrden(orden: OrdenListado) = guardar { repositorio.actualizarOrden(orden) }

    fun onAvisoVisto() = errorAlGuardar.update { false }

    private fun guardar(operacion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operacion()
                errorAlGuardar.update { false }
            } catch (_: IOException) {
                errorAlGuardar.update { true }
            }
        }
    }
}

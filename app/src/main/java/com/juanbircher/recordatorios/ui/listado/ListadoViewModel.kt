package com.juanbircher.recordatorios.ui.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.EscenarioSimulado
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.SimuladorEscenarios
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Estado y eventos del listado. Depende de interfaces: no conoce de dónde salen los datos. */
class ListadoViewModel(
    private val repositorio: RecordatoriosRepository,
    private val simulador: SimuladorEscenarios
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListadoUiState>(ListadoUiState.Cargando)
    val uiState: StateFlow<ListadoUiState> = _uiState.asStateFlow()

    private var observacion: Job? = null

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    fun onFiltroEstado(filtro: FiltroEstado) = actualizarDatos { copy(filtroEstado = filtro) }

    fun onFiltroPrioridad(prioridad: Prioridad?) = actualizarDatos { copy(filtroPrioridad = prioridad) }

    fun onEscenarioSimulado(escenario: EscenarioSimulado) {
        simulador.aplicar(escenario)
        cargar()
    }

    private fun cargar() {
        observacion?.cancel()
        _uiState.update { ListadoUiState.Cargando }
        observacion = viewModelScope.launch {
            try {
                repositorio.observarRecordatorios().collect { lista ->
                    _uiState.update { actual -> estadoCon(lista, actual) }
                }
            } catch (_: IOException) {
                _uiState.update { ListadoUiState.Error }
            }
        }
    }

    /** Si ya había datos, conserva los filtros elegidos. */
    private fun estadoCon(lista: List<Recordatorio>, actual: ListadoUiState): ListadoUiState = when {
        lista.isEmpty() -> ListadoUiState.Vacio
        actual is ListadoUiState.ConDatos -> actual.copy(recordatorios = lista)
        else -> ListadoUiState.ConDatos(lista)
    }

    /** Los filtros solo existen cuando hay datos; en cualquier otro estado no hay nada que cambiar. */
    private fun actualizarDatos(cambio: ListadoUiState.ConDatos.() -> ListadoUiState.ConDatos) {
        _uiState.update { actual -> if (actual is ListadoUiState.ConDatos) actual.cambio() else actual }
    }
}

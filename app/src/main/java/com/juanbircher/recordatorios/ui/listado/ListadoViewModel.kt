package com.juanbircher.recordatorios.ui.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.remote.ModoRed
import com.juanbircher.recordatorios.data.remote.SimuladorServidor
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Estado y eventos del listado. Depende de interfaces: no conoce de dónde salen los datos. */
class ListadoViewModel(
    private val repositorio: RecordatoriosRepository,
    private val simulador: SimuladorServidor
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListadoUiState>(ListadoUiState.Cargando)
    val uiState: StateFlow<ListadoUiState> = _uiState.asStateFlow()

    /** Estado de la sincronización y cantidad de pendientes; se muestra en una barra sobre la lista. */
    val sincronizacion: StateFlow<InfoSincronizacion> = combine(
        repositorio.estadoSincronizacion,
        repositorio.observarResumenSincronizacion()
    ) { estado, resumen -> InfoSincronizacion(estado, resumen) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InfoSincronizacion())

    /** Cómo responde el servicio simulado en este momento. */
    val modoServidor: StateFlow<ModoRed> = simulador.modo

    private var observacion: Job? = null

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    fun onFiltroEstado(filtro: FiltroEstado) = actualizarDatos { copy(filtroEstado = filtro) }

    fun onFiltroPrioridad(prioridad: Prioridad?) = actualizarDatos { copy(filtroPrioridad = prioridad) }

    /** Pide sincronizar ya. Un problema de red no llega acá: queda en [sincronizacion]. */
    fun onSincronizar() {
        viewModelScope.launch { repositorio.sincronizar() }
    }

    fun onReintentarFallidos() {
        viewModelScope.launch { repositorio.reintentarFallidos() }
    }

    fun onModoServidor(modo: ModoRed) = simulador.fijar(modo)

    private fun cargar() {
        observacion?.cancel()
        _uiState.update { ListadoUiState.Cargando }
        observacion = viewModelScope.launch {
            try {
                combine(
                    repositorio.observarRecordatorios(),
                    repositorio.preferencias.map { it.mostrarCompletados }.distinctUntilChanged()
                ) { lista, mostrarCompletados -> lista to mostrarCompletados }
                    .collect { (lista, mostrarCompletados) ->
                        _uiState.update { actual -> estadoCon(lista, !mostrarCompletados, actual) }
                    }
            } catch (_: IOException) {
                _uiState.update { ListadoUiState.Error }
            }
        }
    }

    /**
     * Si ya había datos, conserva los filtros elegidos. «Vacío» solo cuando no hay nada de verdad: si la
     * lista está vacía porque los completados están ocultos, se sigue mostrando la lista con su explicación.
     */
    private fun estadoCon(lista: List<Recordatorio>, completadosOcultos: Boolean, actual: ListadoUiState): ListadoUiState =
        when {
            lista.isEmpty() && !completadosOcultos -> ListadoUiState.Vacio
            actual is ListadoUiState.ConDatos ->
                actual.copy(recordatorios = lista, completadosOcultos = completadosOcultos)
            else -> ListadoUiState.ConDatos(lista, completadosOcultos = completadosOcultos)
        }

    /** Los filtros solo existen cuando hay datos; en cualquier otro estado no hay nada que cambiar. */
    private fun actualizarDatos(cambio: ListadoUiState.ConDatos.() -> ListadoUiState.ConDatos) {
        _uiState.update { actual -> if (actual is ListadoUiState.ConDatos) actual.cambio() else actual }
    }
}

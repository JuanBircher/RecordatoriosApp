package com.juanbircher.recordatorios.ui.listado

import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.domain.filtrarPorEstado
import com.juanbircher.recordatorios.domain.filtrarPorPrioridad
import com.juanbircher.recordatorios.domain.ordenadosPorPrioridad

/**
 * Lo que puede mostrar el listado. Es una jerarquía cerrada: la pantalla resuelve cada caso con un
 * `when` sin `else`, y no existen combinaciones contradictorias como carga y error a la vez.
 */
sealed interface ListadoUiState {
    data object Cargando : ListadoUiState

    /** Todavía no existe ningún recordatorio. */
    data object Vacio : ListadoUiState

    data object Error : ListadoUiState

    /** [visibles] puede quedar vacía si los filtros elegidos no dejan ningún recordatorio. */
    data class ConDatos(
        val recordatorios: List<Recordatorio>,
        val filtroEstado: FiltroEstado = FiltroEstado.Todos,
        val filtroPrioridad: Prioridad? = null
    ) : ListadoUiState {
        val visibles: List<Recordatorio> = recordatorios
            .filtrarPorEstado(filtroEstado)
            .filtrarPorPrioridad(filtroPrioridad)
            .ordenadosPorPrioridad()
    }
}

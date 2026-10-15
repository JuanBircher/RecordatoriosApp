package com.juanbircher.recordatorios.ui

import com.juanbircher.recordatorios.data.CargaRecordatorios
import com.juanbircher.recordatorios.data.Descartado
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.domain.filtrarPorEstado
import com.juanbircher.recordatorios.domain.filtrarPorPrioridad
import com.juanbircher.recordatorios.domain.ordenadosPorPrioridad

/**
 * Lo que la pantalla puede mostrar. Es una jerarquía cerrada: la pantalla resuelve cada caso con
 * un `when` sin `else`, y no existen combinaciones contradictorias de booleanos.
 */
sealed interface EstadoPantalla {
    /** Los descartados viajan en ambos estados porque el aviso se muestra con o sin resultados. */
    val descartados: List<Descartado>

    data class ConDatos(
        val recordatorios: List<Recordatorio>,
        override val descartados: List<Descartado>
    ) : EstadoPantalla

    /** El filtro elegido no deja ningún recordatorio. */
    data class SinResultados(
        override val descartados: List<Descartado>
    ) : EstadoPantalla
}

/** Aplica los filtros elegidos sobre la carga y decide qué estado mostrar. */
fun estadoPantalla(
    carga: CargaRecordatorios,
    filtroEstado: FiltroEstado,
    prioridad: Prioridad?
): EstadoPantalla {
    val visibles = carga.validos
        .filtrarPorEstado(filtroEstado)
        .filtrarPorPrioridad(prioridad)
        .ordenadosPorPrioridad()

    return if (visibles.isEmpty()) {
        EstadoPantalla.SinResultados(carga.descartados)
    } else {
        EstadoPantalla.ConDatos(visibles, carga.descartados)
    }
}

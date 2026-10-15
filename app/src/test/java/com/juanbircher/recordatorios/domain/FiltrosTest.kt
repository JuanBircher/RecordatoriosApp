package com.juanbircher.recordatorios.domain

import com.juanbircher.recordatorios.domain.EstadoRecordatorio.Cancelado
import com.juanbircher.recordatorios.domain.EstadoRecordatorio.Completado
import com.juanbircher.recordatorios.domain.EstadoRecordatorio.Pendiente
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FiltrosTest {

    private val hoy = LocalDate.of(2026, 10, 15)
    private val cat = Categoria("trabajo", "Trabajo")

    private fun r(
        id: Long,
        prioridad: Prioridad,
        estado: EstadoRecordatorio = Pendiente,
        fechaLimite: LocalDate? = null
    ) = Recordatorio(id, "Recordatorio $id", cat, prioridad, hoy, estado, fechaLimite = fechaLimite)

    private val lista = listOf(
        r(1, Prioridad.Baja),
        r(2, Prioridad.Alta, Completado(hoy)),
        r(3, Prioridad.Media, Cancelado()),
        r(4, Prioridad.Alta),
        r(5, Prioridad.Media, Cancelado("Ya no hace falta"))
    )

    private fun List<Recordatorio>.ids() = map { it.id }

    @Test
    fun `filtro Todos conserva la lista completa`() {
        assertEquals(listOf(1L, 2, 3, 4, 5), lista.filtrarPorEstado(FiltroEstado.Todos).ids())
    }

    @Test
    fun `filtra por cada estado`() {
        assertEquals(listOf(1L, 4), lista.filtrarPorEstado(FiltroEstado.Pendientes).ids())
        assertEquals(listOf(2L), lista.filtrarPorEstado(FiltroEstado.Completados).ids())
        assertEquals(listOf(3L, 5), lista.filtrarPorEstado(FiltroEstado.Cancelados).ids())
    }

    @Test
    fun `filtra por prioridad y null significa todas`() {
        assertEquals(listOf(2L, 4), lista.filtrarPorPrioridad(Prioridad.Alta).ids())
        assertEquals(lista, lista.filtrarPorPrioridad(null))
    }

    @Test
    fun `combina estado y prioridad`() {
        val resultado = lista.filtrarPorEstado(FiltroEstado.Pendientes).filtrarPorPrioridad(Prioridad.Alta)
        assertEquals(listOf(4L), resultado.ids())
    }

    @Test
    fun `un filtro sin coincidencias o una lista vacia devuelven lista vacia`() {
        val sinCoincidencias = lista.filtrarPorEstado(FiltroEstado.Completados).filtrarPorPrioridad(Prioridad.Baja)
        assertEquals(emptyList<Recordatorio>(), sinCoincidencias)
        assertEquals(emptyList<Recordatorio>(), emptyList<Recordatorio>().filtrarPorEstado(FiltroEstado.Pendientes))
    }

    @Test
    fun `ordena por prioridad y luego por fecha limite con las fechas vacias al final`() {
        val desordenada = listOf(
            r(1, Prioridad.Baja),
            r(2, Prioridad.Alta),
            r(3, Prioridad.Alta, fechaLimite = hoy.plusDays(1)),
            r(4, Prioridad.Media, fechaLimite = hoy.plusDays(5)),
            r(5, Prioridad.Alta, fechaLimite = hoy)
        )
        assertEquals(listOf(5L, 3, 2, 4, 1), desordenada.ordenadosPorPrioridad().ids())
    }

    @Test
    fun `filtrar no modifica la lista original`() {
        val copia = lista.toList()
        lista.filtrarPorEstado(FiltroEstado.Pendientes)
        assertEquals(copia, lista)
    }
}

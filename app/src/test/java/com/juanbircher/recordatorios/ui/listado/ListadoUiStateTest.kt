package com.juanbircher.recordatorios.ui.listado

import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.cargarRecordatorios
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListadoUiStateTest {

    private val recordatorios = cargarRecordatorios(
        DatosSimulados.recordatorios(LocalDate.of(2026, 10, 15)),
        DatosSimulados.categorias
    ).validos

    @Test
    fun `sin filtros muestra todos los validos ordenados por prioridad`() {
        val visibles = ListadoUiState.ConDatos(recordatorios).visibles

        assertEquals(8, visibles.size)
        assertEquals(Prioridad.Alta, visibles.first().prioridad)
        assertEquals(Prioridad.Baja, visibles.last().prioridad)
    }

    @Test
    fun `aplica filtro de estado y prioridad juntos`() {
        val estado = ListadoUiState.ConDatos(recordatorios, FiltroEstado.Pendientes, Prioridad.Alta)

        assertEquals(listOf(7L, 1L), estado.visibles.map { it.id })
    }

    @Test
    fun `un filtro sin coincidencias deja vacia la lista visible y conserva los datos`() {
        val estado = ListadoUiState.ConDatos(recordatorios, FiltroEstado.Completados, Prioridad.Baja)

        assertTrue(estado.visibles.isEmpty())
        assertEquals(8, estado.recordatorios.size)
    }
}

package com.juanbircher.recordatorios.ui

import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.cargarRecordatorios
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EstadoPantallaTest {

    private val carga = cargarRecordatorios(
        DatosSimulados.recordatorios(LocalDate.of(2026, 10, 15)),
        DatosSimulados.categorias
    )

    @Test
    fun `sin filtros muestra todos los validos ordenados por prioridad`() {
        val estado = estadoPantalla(carga, FiltroEstado.Todos, prioridad = null)

        assertTrue(estado is EstadoPantalla.ConDatos)
        val lista = (estado as EstadoPantalla.ConDatos).recordatorios
        assertEquals(8, lista.size)
        assertEquals(Prioridad.Alta, lista.first().prioridad)
        assertEquals(Prioridad.Baja, lista.last().prioridad)
    }

    @Test
    fun `aplica filtro de estado y prioridad juntos`() {
        val estado = estadoPantalla(carga, FiltroEstado.Pendientes, Prioridad.Alta)

        val ids = (estado as EstadoPantalla.ConDatos).recordatorios.map { it.id }
        assertEquals(listOf(7L, 1L), ids)
    }

    @Test
    fun `un filtro sin coincidencias da SinResultados y conserva los descartados`() {
        val estado = estadoPantalla(carga, FiltroEstado.Completados, Prioridad.Baja)

        assertTrue(estado is EstadoPantalla.SinResultados)
        assertEquals(2, estado.descartados.size)
    }

    @Test
    fun `toda categoria del catalogo tiene color en el Map`() {
        assertTrue(DatosSimulados.categorias.all { it in coloresPorCategoria })
    }

    @Test
    fun `una categoria fuera del Map usa el color neutro`() {
        assertEquals(colorCategoriaDesconocida, colorDe(Categoria("ocio", "Ocio")))
        assertEquals(coloresPorCategoria.getValue(DatosSimulados.salud), colorDe(DatosSimulados.salud))
    }
}

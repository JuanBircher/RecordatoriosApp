package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.validation.ErrorValidacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DatosSimuladosTest {

    private val hoy = LocalDate.of(2026, 10, 15)

    @Test
    fun `el Set de categorias descarta la categoria repetida`() {
        assertEquals(4, DatosSimulados.categorias.size)
        assertEquals(1, DatosSimulados.categorias.count { it.id == "trabajo" })
    }

    @Test
    fun `la carga descarta los dos recordatorios invalidos con su motivo`() {
        val carga = cargarRecordatorios(DatosSimulados.recordatorios(hoy), DatosSimulados.categorias)

        assertEquals(8, carga.validos.size)
        assertEquals(listOf(9L, 10L), carga.descartados.map { it.recordatorio.id })
        assertEquals(listOf(ErrorValidacion.TituloVacio), carga.descartados[0].errores)
        assertEquals(listOf(ErrorValidacion.FechaLimiteAnteriorACreacion), carga.descartados[1].errores)
    }

    @Test
    fun `la carga conserva el orden de origen de los validos`() {
        val carga = cargarRecordatorios(DatosSimulados.recordatorios(hoy), DatosSimulados.categorias)
        assertEquals((1L..8L).toList(), carga.validos.map { it.id })
    }

    @Test
    fun `una lista vacia produce una carga vacia`() {
        val carga = cargarRecordatorios(emptyList(), DatosSimulados.categorias)
        assertTrue(carga.validos.isEmpty())
        assertTrue(carga.descartados.isEmpty())
    }

    @Test
    fun `los ids de los recordatorios simulados no se repiten`() {
        val ids = DatosSimulados.recordatorios(hoy).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}

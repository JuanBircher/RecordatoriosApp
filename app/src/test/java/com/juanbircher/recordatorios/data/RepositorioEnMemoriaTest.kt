package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RepositorioEnMemoriaTest {

    private val hoy = LocalDate.of(2026, 11, 2)
    private val repositorio = RepositorioEnMemoria(hoy = hoy)

    private val nuevo = Recordatorio(
        id = 0,
        titulo = "Llamar al banco",
        categoria = DatosSimulados.trabajo,
        prioridad = Prioridad.Alta,
        creadoEl = hoy
    )

    @Test
    fun `arranca con los recordatorios validos y sin los invalidos`() = runTest {
        val ids = repositorio.observarRecordatorios().first().map { it.id }

        assertEquals((1L..8L).toList(), ids)
    }

    @Test
    fun `guardar asigna un id nuevo y el recordatorio aparece en la lista observada`() = runTest {
        val emisiones = mutableListOf<List<Recordatorio>>()
        val observacion = launch { repositorio.observarRecordatorios().collect { emisiones += it } }
        advanceUntilIdle()

        val guardado = repositorio.guardarRecordatorio(nuevo)
        runCurrent()
        observacion.cancel()

        assertEquals(9L, guardado.id)
        assertEquals(guardado, emisiones.last().last())
    }

    @Test
    fun `obtener devuelve el recordatorio por id o null si no existe`() = runTest {
        assertEquals("Turno con el dentista", repositorio.obtenerRecordatorio(2)?.titulo)
        assertNull(repositorio.obtenerRecordatorio(999))
    }

    @Test
    fun `el error al cargar falla una sola vez para que el reintento funcione`() = runTest {
        repositorio.aplicar(EscenarioSimulado.ErrorAlCargar)

        assertFallaConIOException { repositorio.observarRecordatorios().first() }
        assertEquals(8, repositorio.observarRecordatorios().first().size)
    }

    @Test
    fun `el error al guardar falla una sola vez y no guarda nada`() = runTest {
        repositorio.aplicar(EscenarioSimulado.ErrorAlGuardar)

        assertFallaConIOException { repositorio.guardarRecordatorio(nuevo) }
        assertEquals(8, repositorio.observarRecordatorios().first().size)

        repositorio.guardarRecordatorio(nuevo)
        assertEquals(9, repositorio.observarRecordatorios().first().size)
    }

    @Test
    fun `lista vacia deja la fuente sin datos y se puede crear desde ahi`() = runTest {
        repositorio.aplicar(EscenarioSimulado.ListaVacia)
        assertTrue(repositorio.observarRecordatorios().first().isEmpty())

        val guardado = repositorio.guardarRecordatorio(nuevo)

        assertEquals(1L, guardado.id)
        assertEquals(listOf(guardado), repositorio.observarRecordatorios().first())
    }

    @Test
    fun `con datos restaura los iniciales y cancela las fallas pendientes`() = runTest {
        repositorio.aplicar(EscenarioSimulado.ListaVacia)
        repositorio.aplicar(EscenarioSimulado.ErrorAlCargar)
        repositorio.aplicar(EscenarioSimulado.ConDatos)

        assertEquals(8, repositorio.observarRecordatorios().first().size)
    }

    private suspend fun assertFallaConIOException(accion: suspend () -> Unit) {
        val fallo = try {
            accion()
            null
        } catch (e: IOException) {
            e
        }
        assertTrue("Se esperaba IOException", fallo != null)
    }
}

package com.juanbircher.recordatorios.ui.detalle

import com.juanbircher.recordatorios.domain.EstadoRecordatorio
import com.juanbircher.recordatorios.ui.ReglaDispatcherMain
import com.juanbircher.recordatorios.ui.RepositorioFalso
import com.juanbircher.recordatorios.ui.recordatorioDePrueba
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModelTest {

    @get:Rule
    val main = ReglaDispatcherMain()

    private val hoy = LocalDate.of(2026, 11, 9)
    private val recordatorio = recordatorioDePrueba(5)

    private fun viewModel(id: Long, repositorio: RepositorioFalso) = DetalleViewModel(id, repositorio) { hoy }

    @Test
    fun `busca el recordatorio por el id recibido`() = runTest(main.dispatcher) {
        val vm = viewModel(5, RepositorioFalso(listOf(recordatorioDePrueba(1), recordatorio)))
        assertEquals(DetalleUiState.Cargando, vm.uiState.value)

        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(DetalleUiState.ConDatos(recordatorio), vm.uiState.value)
    }

    @Test
    fun `un id inexistente da no encontrado`() = runTest(main.dispatcher) {
        val vm = viewModel(99, RepositorioFalso(listOf(recordatorio)))

        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(DetalleUiState.NoEncontrado, vm.uiState.value)
    }

    @Test
    fun `si la fuente falla informa el error y el reintento vuelve a buscar`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio)).apply { fallaAlObservar = true }
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)
        assertEquals(DetalleUiState.Error, vm.uiState.value)

        repositorio.fallaAlObservar = false
        vm.onReintentar()
        assertEquals(DetalleUiState.Cargando, vm.uiState.value)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(DetalleUiState.ConDatos(recordatorio), vm.uiState.value)
    }

    @Test
    fun `refleja los cambios de la base sin volver a pedir los datos`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio))
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        repositorio.lista.value = listOf(recordatorio.copy(titulo = "Título cambiado"))
        runCurrent()

        assertEquals("Título cambiado", (vm.uiState.value as DetalleUiState.ConDatos).recordatorio.titulo)
    }

    @Test
    fun `completar un pendiente lo actualiza con la fecha de hoy`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio))
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        vm.onAlternarCompletado()
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(EstadoRecordatorio.Completado(hoy), repositorio.actualizados.single().estado)
    }

    @Test
    fun `reabrir un completado lo devuelve a pendiente`() = runTest(main.dispatcher) {
        val completado = recordatorio.copy(estado = EstadoRecordatorio.Completado(hoy))
        val repositorio = RepositorioFalso(listOf(completado))
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        vm.onAlternarCompletado()
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(EstadoRecordatorio.Pendiente, repositorio.actualizados.single().estado)
    }

    @Test
    fun `si la actualizacion falla avisa y conserva el recordatorio`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio)).apply { fallaAlActualizar = true }
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        vm.onAlternarCompletado()
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(DetalleUiState.ConDatos(recordatorio, errorAccion = true), vm.uiState.value)
    }

    @Test
    fun `eliminar lo borra del repositorio y avisa para volver sin pasar por no encontrado`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio))
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        vm.onEliminar()
        advanceUntilIdle()

        assertEquals(listOf(5L), repositorio.eliminados)
        assertEquals(DetalleUiState.Eliminado, vm.uiState.value)
    }

    @Test
    fun `si el borrado falla sigue mostrando el recordatorio con el aviso`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio)).apply { fallaAlEliminar = true }
        val vm = viewModel(5, repositorio)
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        vm.onEliminar()
        advanceTimeBy(RepositorioFalso.LATENCIA + 1)

        assertEquals(DetalleUiState.ConDatos(recordatorio, errorAccion = true), vm.uiState.value)
    }
}

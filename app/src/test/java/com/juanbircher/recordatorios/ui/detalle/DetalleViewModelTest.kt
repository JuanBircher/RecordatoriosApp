package com.juanbircher.recordatorios.ui.detalle

import com.juanbircher.recordatorios.ui.ReglaDispatcherMain
import com.juanbircher.recordatorios.ui.RepositorioFalso
import com.juanbircher.recordatorios.ui.recordatorioDePrueba
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModelTest {

    @get:Rule
    val main = ReglaDispatcherMain()

    private val recordatorio = recordatorioDePrueba(5)

    @Test
    fun `busca el recordatorio por el id recibido`() = runTest(main.dispatcher) {
        val vm = DetalleViewModel(5, RepositorioFalso(listOf(recordatorioDePrueba(1), recordatorio)))
        assertEquals(DetalleUiState.Cargando, vm.uiState.value)

        advanceUntilIdle()

        assertEquals(DetalleUiState.ConDatos(recordatorio), vm.uiState.value)
    }

    @Test
    fun `un id inexistente da no encontrado`() = runTest(main.dispatcher) {
        val vm = DetalleViewModel(99, RepositorioFalso(listOf(recordatorio)))

        advanceUntilIdle()

        assertEquals(DetalleUiState.NoEncontrado, vm.uiState.value)
    }

    @Test
    fun `si la fuente falla informa el error y el reintento vuelve a buscar`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(recordatorio)).apply { fallaAlObtener = true }
        val vm = DetalleViewModel(5, repositorio)
        advanceUntilIdle()
        assertEquals(DetalleUiState.Error, vm.uiState.value)

        repositorio.fallaAlObtener = false
        vm.onReintentar()
        assertEquals(DetalleUiState.Cargando, vm.uiState.value)
        advanceUntilIdle()

        assertEquals(DetalleUiState.ConDatos(recordatorio), vm.uiState.value)
    }
}

package com.juanbircher.recordatorios.ui.ajustes

import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import com.juanbircher.recordatorios.ui.ReglaDispatcherMain
import com.juanbircher.recordatorios.ui.RepositorioFalso
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

    @get:Rule
    val main = ReglaDispatcherMain()

    private val repositorio = RepositorioFalso()

    @Test
    fun `muestra las preferencias del repositorio, con los valores por defecto al inicio`() = runTest(main.dispatcher) {
        val vm = AjustesViewModel(repositorio)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(Preferencias(), vm.uiState.value.preferencias)
        assertFalse(vm.uiState.value.errorAlGuardar)
    }

    @Test
    fun `cada cambio se guarda en el repositorio y se refleja en el estado`() = runTest(main.dispatcher) {
        val vm = AjustesViewModel(repositorio)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.onTema(TemaApp.Oscuro)
        vm.onMostrarCompletados(false)
        vm.onOrden(OrdenListado.FechaLimite)
        advanceUntilIdle()

        assertEquals(Preferencias(TemaApp.Oscuro, false, OrdenListado.FechaLimite), vm.uiState.value.preferencias)
    }

    @Test
    fun `si no se puede guardar avisa y el aviso se puede descartar`() = runTest(main.dispatcher) {
        repositorio.fallaAlGuardarPreferencia = true
        val vm = AjustesViewModel(repositorio)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.onTema(TemaApp.Claro)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errorAlGuardar)
        assertEquals(TemaApp.Sistema, vm.uiState.value.preferencias.tema)

        vm.onAvisoVisto()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.errorAlGuardar)
    }
}

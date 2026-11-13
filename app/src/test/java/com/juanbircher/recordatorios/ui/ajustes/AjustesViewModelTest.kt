package com.juanbircher.recordatorios.ui.ajustes

import com.juanbircher.recordatorios.data.ResultadoPruebaAcceso
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import com.juanbircher.recordatorios.data.ResumenSincronizacion
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.ui.AuthFalso
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
    private val ana = EstadoSesion.Activa(uid = "uid-ana", correo = "ana@prueba.com")
    private val auth = AuthFalso(ana)

    @Test
    fun `muestra las preferencias del repositorio, con los valores por defecto al inicio`() = runTest(main.dispatcher) {
        val vm = AjustesViewModel(repositorio, auth)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(Preferencias(), vm.uiState.value.preferencias)
        assertFalse(vm.uiState.value.errorAlGuardar)
    }

    @Test
    fun `cada cambio se guarda en el repositorio y se refleja en el estado`() = runTest(main.dispatcher) {
        val vm = AjustesViewModel(repositorio, auth)
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
        val vm = AjustesViewModel(repositorio, auth)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.onTema(TemaApp.Claro)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errorAlGuardar)
        assertEquals(TemaApp.Sistema, vm.uiState.value.preferencias.tema)

        vm.onAvisoVisto()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.errorAlGuardar)
    }

    // ---- cuenta ----------------------------------------------------------------------------------

    @Test
    fun `muestra la cuenta activa y cuantos cambios todavia no llegaron al servicio`() = runTest(main.dispatcher) {
        repositorio.resumen.value = ResumenSincronizacion(pendientes = 2, conError = 1)
        val vm = AjustesViewModel(repositorio, auth)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ana, vm.uiState.value.cuenta)
        assertEquals(3, vm.uiState.value.cambiosSinEnviar)
    }

    @Test
    fun `cerrar sesion se delega en la autenticacion`() = runTest(main.dispatcher) {
        val vm = AjustesViewModel(repositorio, auth)

        vm.onCerrarSesion()
        advanceUntilIdle()

        assertEquals(1, auth.cierres)
    }

    @Test
    fun `la prueba de acceso a otra cuenta muestra la respuesta del servicio`() = runTest(main.dispatcher) {
        repositorio.resultadoPruebaAcceso = ResultadoPruebaAcceso.Rechazado(CausaFallo.NoAutorizado)
        val vm = AjustesViewModel(repositorio, auth, permitirPruebaAcceso = true)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.onProbarAcceso("  uid-beto ")
        advanceUntilIdle()

        assertEquals(listOf("uid-beto"), repositorio.pruebasAcceso)
        assertEquals(PruebaAcceso.Terminada(ResultadoPruebaAcceso.Rechazado(CausaFallo.NoAutorizado)), vm.uiState.value.pruebaAcceso)
    }

    @Test
    fun `fuera del ambiente de desarrollo la prueba de acceso no existe`() = runTest(main.dispatcher) {
        val vm = AjustesViewModel(repositorio, auth, permitirPruebaAcceso = false)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }

        vm.onProbarAcceso("uid-beto")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.mostrarPruebaAcceso)
        assertTrue(repositorio.pruebasAcceso.isEmpty())
    }

    @Test
    fun `si falla la lectura de pendientes, la cuenta y las preferencias siguen funcionando`() = runTest(main.dispatcher) {
        repositorio.fallaAlObservarResumen = true
        val vm = AjustesViewModel(repositorio, auth)
        backgroundScope.launch(main.dispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onTema(TemaApp.Oscuro)
        advanceUntilIdle()

        assertEquals(ana, vm.uiState.value.cuenta)
        assertEquals(TemaApp.Oscuro, vm.uiState.value.preferencias.tema)
        assertEquals(0, vm.uiState.value.cambiosSinEnviar)
    }
}

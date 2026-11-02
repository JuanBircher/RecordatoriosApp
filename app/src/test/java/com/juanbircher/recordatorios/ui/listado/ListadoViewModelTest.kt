package com.juanbircher.recordatorios.ui.listado

import com.juanbircher.recordatorios.data.EscenarioSimulado
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.ui.ReglaDispatcherMain
import com.juanbircher.recordatorios.ui.RepositorioFalso
import com.juanbircher.recordatorios.ui.recordatorioDePrueba
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListadoViewModelTest {

    @get:Rule
    val main = ReglaDispatcherMain()

    private val baja = recordatorioDePrueba(1, Prioridad.Baja)
    private val alta = recordatorioDePrueba(2, Prioridad.Alta)

    private fun viewModel(repositorio: RepositorioFalso) = ListadoViewModel(repositorio, repositorio)

    /** La observación no termina nunca, así que se avanza solo lo que tarda la carga. */
    private fun TestScope.esperarCarga() = advanceTimeBy(RepositorioFalso.LATENCIA + 1)

    @Test
    fun `arranca cargando y pasa a con datos`() = runTest(main.dispatcher) {
        val vm = viewModel(RepositorioFalso(listOf(baja, alta)))

        assertEquals(ListadoUiState.Cargando, vm.uiState.value)
        esperarCarga()

        val estado = vm.uiState.value as ListadoUiState.ConDatos
        assertEquals(listOf(alta, baja), estado.visibles)
    }

    @Test
    fun `sin recordatorios queda en vacio`() = runTest(main.dispatcher) {
        val vm = viewModel(RepositorioFalso())
        esperarCarga()

        assertEquals(ListadoUiState.Vacio, vm.uiState.value)
    }

    @Test
    fun `si la fuente falla queda en error y el reintento vuelve a cargar`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(alta)).apply { fallaAlObservar = true }
        val vm = viewModel(repositorio)
        esperarCarga()
        assertEquals(ListadoUiState.Error, vm.uiState.value)

        repositorio.fallaAlObservar = false
        vm.onReintentar()
        assertEquals(ListadoUiState.Cargando, vm.uiState.value)
        esperarCarga()

        assertTrue(vm.uiState.value is ListadoUiState.ConDatos)
    }

    @Test
    fun `un recordatorio nuevo aparece sin recargar y conserva los filtros`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(baja))
        val vm = viewModel(repositorio)
        esperarCarga()
        vm.onFiltroPrioridad(Prioridad.Alta)

        repositorio.lista.value = listOf(baja, alta)
        runCurrent()

        val estado = vm.uiState.value as ListadoUiState.ConDatos
        assertEquals(Prioridad.Alta, estado.filtroPrioridad)
        assertEquals(listOf(alta), estado.visibles)
    }

    @Test
    fun `el primer recordatorio creado saca al listado del estado vacio`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso()
        val vm = viewModel(repositorio)
        esperarCarga()

        repositorio.lista.value = listOf(alta)
        runCurrent()

        assertTrue(vm.uiState.value is ListadoUiState.ConDatos)
    }

    @Test
    fun `los filtros producen una nueva instancia con la seleccion`() = runTest(main.dispatcher) {
        val vm = viewModel(RepositorioFalso(listOf(baja, alta)))
        esperarCarga()
        val antes = vm.uiState.value

        vm.onFiltroEstado(FiltroEstado.Completados)
        vm.onFiltroPrioridad(Prioridad.Baja)

        val estado = vm.uiState.value as ListadoUiState.ConDatos
        assertEquals(FiltroEstado.Completados, estado.filtroEstado)
        assertEquals(Prioridad.Baja, estado.filtroPrioridad)
        assertTrue(estado.visibles.isEmpty())
        assertEquals(FiltroEstado.Todos, (antes as ListadoUiState.ConDatos).filtroEstado)
    }

    @Test
    fun `filtrar mientras carga no cambia el estado`() = runTest(main.dispatcher) {
        val vm = viewModel(RepositorioFalso(listOf(alta)))

        vm.onFiltroEstado(FiltroEstado.Pendientes)

        assertEquals(ListadoUiState.Cargando, vm.uiState.value)
    }

    @Test
    fun `elegir un escenario lo aplica y vuelve a cargar`() = runTest(main.dispatcher) {
        val repositorio = RepositorioFalso(listOf(alta))
        val vm = viewModel(repositorio)
        esperarCarga()

        vm.onEscenarioSimulado(EscenarioSimulado.ErrorAlCargar)

        assertEquals(listOf(EscenarioSimulado.ErrorAlCargar), repositorio.escenarios)
        assertEquals(ListadoUiState.Cargando, vm.uiState.value)
    }
}

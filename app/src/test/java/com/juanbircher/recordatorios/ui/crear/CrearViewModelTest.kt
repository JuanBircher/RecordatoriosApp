package com.juanbircher.recordatorios.ui.crear

import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.ui.ReglaDispatcherMain
import com.juanbircher.recordatorios.ui.RepositorioFalso
import com.juanbircher.recordatorios.validation.ErrorValidacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CrearViewModelTest {

    @get:Rule
    val main = ReglaDispatcherMain()

    private val hoy = LocalDate.of(2026, 11, 2)
    private val repositorio = RepositorioFalso()
    private val vm by lazy { CrearViewModel(repositorio) { hoy } }

    private fun completarFormulario() {
        vm.onTituloChange("Llamar al banco")
        vm.onCategoriaChange(DatosSimulados.trabajo)
        vm.onPrioridadChange(Prioridad.Alta)
    }

    @Test
    fun `al abrir ofrece las categorias, no permite guardar y no muestra errores`() {
        val estado = vm.uiState.value

        assertEquals(DatosSimulados.categorias.toList(), estado.categoriasDisponibles)
        assertFalse(estado.puedeGuardar)
        assertEquals(ErrorValidacion.TituloVacio, estado.errores.titulo)
        assertNull(estado.erroresVisibles.titulo)
    }

    @Test
    fun `al empezar a completar muestra los errores de todos los campos`() {
        vm.onTituloChange("ab")

        val visibles = vm.uiState.value.erroresVisibles
        assertEquals(ErrorValidacion.TituloLongitudInvalida(2), visibles.titulo)
        assertEquals(ErrorValidacion.CategoriaNoSeleccionada, visibles.categoria)
        assertEquals(ErrorValidacion.PrioridadNoSeleccionada, visibles.prioridad)
    }

    @Test
    fun `con todos los datos validos permite guardar`() {
        completarFormulario()

        assertTrue(vm.uiState.value.puedeGuardar)
    }

    @Test
    fun `una descripcion de 201 caracteres vuelve invalido el formulario`() {
        completarFormulario()

        vm.onDescripcionChange("d".repeat(201))

        assertEquals(ErrorValidacion.DescripcionDemasiadoLarga(201), vm.uiState.value.errores.descripcion)
        assertFalse(vm.uiState.value.puedeGuardar)
    }

    @Test
    fun `guardar con el formulario invalido no llama al repositorio`() = runTest(main.dispatcher) {
        vm.onTituloChange("abc")

        vm.onGuardar()
        advanceUntilIdle()

        assertTrue(repositorio.guardados.isEmpty())
        assertEquals(EstadoEnvio.Inactivo, vm.uiState.value.envio)
    }

    @Test
    fun `mientras envia bloquea guardar y editar, y un segundo toque no duplica`() = runTest(main.dispatcher) {
        completarFormulario()

        vm.onGuardar()
        runCurrent()

        assertEquals(EstadoEnvio.Enviando, vm.uiState.value.envio)
        assertFalse(vm.uiState.value.puedeGuardar)

        vm.onGuardar()
        vm.onTituloChange("Otro titulo")
        assertEquals("Llamar al banco", vm.uiState.value.titulo)

        advanceUntilIdle()
        assertEquals(1, repositorio.guardados.size)
    }

    @Test
    fun `al guardar con exito limpia los datos y los transmite completos`() = runTest(main.dispatcher) {
        vm.onTituloChange("  Llamar al banco  ")
        vm.onDescripcionChange("  Pedir el resumen  ")
        vm.onCategoriaChange(DatosSimulados.salud)
        vm.onPrioridadChange(Prioridad.Baja)

        vm.onGuardar()
        advanceUntilIdle()

        assertEquals(EstadoEnvio.Exitoso, vm.uiState.value.envio)
        val guardado = repositorio.guardados.single()
        assertEquals("Llamar al banco", guardado.titulo)
        assertEquals("Pedir el resumen", guardado.descripcion)
        assertEquals(DatosSimulados.salud, guardado.categoria)
        assertEquals(Prioridad.Baja, guardado.prioridad)
        assertEquals(hoy, guardado.creadoEl)
    }

    @Test
    fun `una descripcion en blanco se guarda como null`() = runTest(main.dispatcher) {
        completarFormulario()
        vm.onDescripcionChange("    ")

        vm.onGuardar()
        advanceUntilIdle()

        assertNull(repositorio.guardados.single().descripcion)
    }

    @Test
    fun `si falla informa el error, conserva los datos y permite reintentar`() = runTest(main.dispatcher) {
        repositorio.fallaAlGuardar = true
        completarFormulario()

        vm.onGuardar()
        advanceUntilIdle()

        assertEquals(EstadoEnvio.Error, vm.uiState.value.envio)
        assertEquals("Llamar al banco", vm.uiState.value.titulo)
        assertTrue(vm.uiState.value.puedeGuardar)

        repositorio.fallaAlGuardar = false
        vm.onGuardar()
        advanceUntilIdle()

        assertEquals(EstadoEnvio.Exitoso, vm.uiState.value.envio)
    }

    @Test
    fun `editar despues de un error quita el mensaje general`() = runTest(main.dispatcher) {
        repositorio.fallaAlGuardar = true
        completarFormulario()
        vm.onGuardar()
        advanceUntilIdle()

        vm.onTituloChange("Llamar al banco hoy")

        assertEquals(EstadoEnvio.Inactivo, vm.uiState.value.envio)
    }
}

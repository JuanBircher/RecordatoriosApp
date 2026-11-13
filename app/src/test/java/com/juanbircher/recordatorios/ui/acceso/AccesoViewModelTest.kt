package com.juanbircher.recordatorios.ui.acceso

import com.juanbircher.recordatorios.domain.ErrorAuth
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.ui.AuthFalso
import com.juanbircher.recordatorios.ui.ReglaDispatcherMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class AccesoViewModelTest {

    @get:Rule
    val main = ReglaDispatcherMain()

    private val auth = AuthFalso()
    private val vm = AccesoViewModel(auth)

    private fun completar(correo: String = "ana@prueba.com", contrasena: String = "secreta1") {
        vm.onCorreo(correo)
        vm.onContrasena(contrasena)
    }

    @Test
    fun `arranca en ingresar, vacio y sin errores a la vista`() {
        val estado = vm.uiState.value
        assertEquals(ModoAcceso.Ingresar, estado.modo)
        assertFalse(estado.errorCorreo)
        assertFalse(estado.errorContrasena)
        assertFalse(estado.enviando)
    }

    @Test
    fun `con un correo mal escrito o una contrasena corta no se llama al servicio`() = runTest(main.dispatcher) {
        completar(correo = "ana-sin-arroba", contrasena = "123")

        vm.onEnviar()
        advanceUntilIdle()

        assertTrue(auth.ingresos.isEmpty())
        assertTrue(vm.uiState.value.errorCorreo)
        assertTrue(vm.uiState.value.errorContrasena)
    }

    @Test
    fun `mientras se ingresa se muestra la carga y un segundo toque no repite el envio`() = runTest(main.dispatcher) {
        completar()

        vm.onEnviar()
        vm.onEnviar()
        runCurrent()

        assertTrue(vm.uiState.value.enviando)
        advanceUntilIdle()
        assertEquals(1, auth.ingresos.size)
        assertFalse(vm.uiState.value.enviando)
        assertTrue(auth.sesion.value is EstadoSesion.Activa)
    }

    @Test
    fun `el correo se envia sin espacios alrededor`() = runTest(main.dispatcher) {
        completar(correo = "  ana@prueba.com ")

        vm.onEnviar()
        advanceUntilIdle()

        assertEquals("ana@prueba.com", auth.ingresos.single().first)
    }

    @Test
    fun `un rechazo del servicio se muestra y se conserva lo escrito`() = runTest(main.dispatcher) {
        auth.error = ErrorAuth.CredencialesInvalidas
        completar()

        vm.onEnviar()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(ErrorAuth.CredencialesInvalidas, estado.errorAuth)
        assertEquals("ana@prueba.com", estado.correo)
        assertEquals("secreta1", estado.contrasena)
        assertFalse(estado.enviando)
    }

    @Test
    fun `en modo crear cuenta se registra en lugar de ingresar`() = runTest(main.dispatcher) {
        vm.onCambiarModo()
        completar()

        vm.onEnviar()
        advanceUntilIdle()

        assertEquals(1, auth.registros.size)
        assertTrue(auth.ingresos.isEmpty())
    }

    @Test
    fun `cambiar de modo o corregir un campo borra el error anterior`() = runTest(main.dispatcher) {
        auth.error = ErrorAuth.CorreoEnUso
        completar()
        vm.onEnviar()
        advanceUntilIdle()

        vm.onCambiarModo()
        assertNull(vm.uiState.value.errorAuth)

        vm.onEnviar()
        advanceUntilIdle()
        vm.onContrasena("otra-clave")
        assertNull(vm.uiState.value.errorAuth)
    }

    @Test
    fun `al ingresar bien el formulario se vacia para que la proxima persona no vea los datos`() = runTest(main.dispatcher) {
        completar()

        vm.onEnviar()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.correo)
        assertEquals("", vm.uiState.value.contrasena)
        assertFalse(vm.uiState.value.errorCorreo)
    }
}

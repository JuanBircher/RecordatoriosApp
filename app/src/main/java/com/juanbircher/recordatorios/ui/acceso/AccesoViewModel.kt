package com.juanbircher.recordatorios.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.auth.AuthRepository
import com.juanbircher.recordatorios.domain.ErrorAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModoAcceso { Ingresar, CrearCuenta }

/**
 * Estado del formulario de acceso.
 *
 * Las validaciones locales (formato del correo, largo mínimo de la contraseña) solo mejoran la
 * experiencia: el proveedor vuelve a validar y puede rechazar igual, y ese rechazo llega en [errorAuth].
 * Los errores de los campos se muestran recién después del primer intento ([mostrarErrores]).
 *
 * La contraseña vive solo en memoria mientras la pantalla existe: no se guarda en preferencias ni en el
 * estado que el sistema conserva al recrear la actividad.
 */
data class AccesoUiState(
    val modo: ModoAcceso = ModoAcceso.Ingresar,
    val correo: String = "",
    val contrasena: String = "",
    val enviando: Boolean = false,
    val mostrarErrores: Boolean = false,
    val errorAuth: ErrorAuth? = null
) {
    val correoValido: Boolean = FORMATO_CORREO.matches(correo.trim())
    val contrasenaValida: Boolean = contrasena.length >= LARGO_MINIMO_CONTRASENA

    val errorCorreo: Boolean = mostrarErrores && !correoValido
    val errorContrasena: Boolean = mostrarErrores && !contrasenaValida

    companion object {
        /** Firebase exige al menos 6 caracteres; acá se avisa antes de ir a la red. */
        const val LARGO_MINIMO_CONTRASENA = 6
        private val FORMATO_CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/**
 * Formulario de ingreso y registro. No navega: cuando el proveedor confirma la sesión, la raíz de la app
 * (que observa el estado de la sesión) muestra la pantalla principal.
 */
class AccesoViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AccesoUiState())
    val uiState: StateFlow<AccesoUiState> = _uiState.asStateFlow()

    fun onCorreo(correo: String) = _uiState.update { it.copy(correo = correo, errorAuth = null) }

    fun onContrasena(contrasena: String) = _uiState.update { it.copy(contrasena = contrasena, errorAuth = null) }

    fun onCambiarModo() = _uiState.update {
        val otro = if (it.modo == ModoAcceso.Ingresar) ModoAcceso.CrearCuenta else ModoAcceso.Ingresar
        it.copy(modo = otro, errorAuth = null, mostrarErrores = false)
    }

    fun onEnviar() {
        val actual = _uiState.value
        // Un segundo toque mientras se espera la respuesta no inicia otro envío.
        if (actual.enviando) return
        if (!actual.correoValido || !actual.contrasenaValida) {
            _uiState.update { it.copy(mostrarErrores = true) }
            return
        }
        _uiState.update { it.copy(enviando = true, mostrarErrores = true, errorAuth = null) }
        viewModelScope.launch {
            val correo = actual.correo.trim()
            val error = when (actual.modo) {
                ModoAcceso.Ingresar -> auth.iniciarSesion(correo, actual.contrasena)
                ModoAcceso.CrearCuenta -> auth.registrar(correo, actual.contrasena)
            }
            _uiState.update {
                if (error == null) {
                    // Sesión iniciada: el formulario sobrevive en memoria mientras exista la actividad, así que
                    // se vacía para que quien use el teléfono después no encuentre el correo ni la contraseña.
                    AccesoUiState(modo = ModoAcceso.Ingresar)
                } else {
                    it.copy(enviando = false, errorAuth = error)
                }
            }
        }
    }
}

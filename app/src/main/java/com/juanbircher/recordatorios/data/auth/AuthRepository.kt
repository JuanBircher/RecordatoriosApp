package com.juanbircher.recordatorios.data.auth

import com.juanbircher.recordatorios.domain.ErrorAuth
import com.juanbircher.recordatorios.domain.EstadoSesion
import kotlinx.coroutines.flow.StateFlow

/**
 * Autenticación de la app, independiente del proveedor (hoy, Firebase Authentication). Las pantallas y
 * los ViewModel dependen de esta interfaz: nunca llaman a Firebase.
 *
 * La app no guarda contraseñas ni tokens: la contraseña solo viaja al proveedor, y la sesión (con sus
 * tokens) la administra y renueva el SDK. Lo único que el resto de la app conoce es el uid de la cuenta.
 */
interface AuthRepository {

    /** Estado actual de la sesión; vuelve a emitir al ingresar, al salir o si la sesión deja de ser válida. */
    val sesion: StateFlow<EstadoSesion>

    /** Crea la cuenta y deja la sesión iniciada. Devuelve `null` si salió bien, o el motivo del rechazo. */
    suspend fun registrar(correo: String, contrasena: String): ErrorAuth?

    /** Inicia la sesión. Devuelve `null` si salió bien, o el motivo del rechazo. */
    suspend fun iniciarSesion(correo: String, contrasena: String): ErrorAuth?

    suspend fun cerrarSesion()

    /**
     * Consulta al proveedor si la sesión guardada sigue siendo válida. Si la cuenta se deshabilitó o se
     * borró, cierra la sesión con [com.juanbircher.recordatorios.domain.MotivoSinSesion.SesionInvalida].
     * Sin conexión no hace nada: la app sigue funcionando con los datos locales.
     */
    suspend fun verificarSesion()
}

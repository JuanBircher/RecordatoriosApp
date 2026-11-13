package com.juanbircher.recordatorios.domain

/**
 * Estado de la sesión, tal como lo informa el proveedor de autenticación. La interfaz decide qué mostrar
 * a partir de este estado, nunca a partir de qué pantalla se mostró antes.
 */
sealed interface EstadoSesion {
    /** Al abrir la app, hasta que el proveedor informa si hay una sesión guardada. */
    data object Verificando : EstadoSesion

    /** Nadie ingresó. [motivo] explica por qué, si la sesión terminó durante el uso. */
    data class SinSesion(val motivo: MotivoSinSesion? = null) : EstadoSesion

    /** Sesión activa. [uid] identifica la cuenta y asocia sus recordatorios; no es un secreto. */
    data class Activa(val uid: String, val correo: String?) : EstadoSesion
}

enum class MotivoSinSesion {
    /** La persona cerró la sesión. */
    CerroSesion,

    /** El proveedor ya no reconoce la sesión: la cuenta se deshabilitó, se borró o la credencial venció. */
    SesionInvalida
}

/** Por qué no se pudo ingresar o crear la cuenta. Es independiente del proveedor de autenticación. */
enum class ErrorAuth {
    /** Correo o contraseña incorrectos (no se distingue cuál, para no revelar qué cuentas existen). */
    CredencialesInvalidas,
    CorreoEnUso,
    ContrasenaDebil,
    CorreoInvalido,
    CuentaDeshabilitada,
    DemasiadosIntentos,
    SinConexion,
    Otro
}

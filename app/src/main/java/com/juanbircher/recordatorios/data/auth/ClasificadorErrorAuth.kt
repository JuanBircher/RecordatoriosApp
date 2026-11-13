package com.juanbircher.recordatorios.data.auth

import com.juanbircher.recordatorios.domain.ErrorAuth

/**
 * Traduce el código de error de Firebase Authentication (`FirebaseAuthException.errorCode`) a un
 * [ErrorAuth]. Es una función pura sobre el texto del código: se prueba sin Firebase.
 *
 * Contraseña incorrecta y cuenta inexistente se informan igual a propósito: un mensaje distinto le
 * diría a cualquiera qué correos tienen cuenta (los proyectos nuevos de Firebase ya devuelven un único
 * `ERROR_INVALID_CREDENTIAL`).
 */
fun clasificarCodigoAuth(codigo: String?): ErrorAuth = when (codigo) {
    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> ErrorAuth.CredencialesInvalidas
    "ERROR_EMAIL_ALREADY_IN_USE" -> ErrorAuth.CorreoEnUso
    "ERROR_WEAK_PASSWORD" -> ErrorAuth.ContrasenaDebil
    "ERROR_INVALID_EMAIL" -> ErrorAuth.CorreoInvalido
    "ERROR_USER_DISABLED" -> ErrorAuth.CuentaDeshabilitada
    "ERROR_TOO_MANY_REQUESTS" -> ErrorAuth.DemasiadosIntentos
    else -> ErrorAuth.Otro
}

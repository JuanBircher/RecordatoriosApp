package com.juanbircher.recordatorios.data.auth

import com.juanbircher.recordatorios.domain.ErrorAuth
import org.junit.Assert.assertEquals
import org.junit.Test

class ClasificadorErrorAuthTest {

    @Test
    fun `contrasena incorrecta o cuenta inexistente son el mismo error para no revelar que cuentas existen`() {
        listOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND").forEach {
            assertEquals(it, ErrorAuth.CredencialesInvalidas, clasificarCodigoAuth(it))
        }
    }

    @Test
    fun `cada error del registro tiene su motivo`() {
        assertEquals(ErrorAuth.CorreoEnUso, clasificarCodigoAuth("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(ErrorAuth.ContrasenaDebil, clasificarCodigoAuth("ERROR_WEAK_PASSWORD"))
        assertEquals(ErrorAuth.CorreoInvalido, clasificarCodigoAuth("ERROR_INVALID_EMAIL"))
    }

    @Test
    fun `cuenta deshabilitada y exceso de intentos se informan aparte`() {
        assertEquals(ErrorAuth.CuentaDeshabilitada, clasificarCodigoAuth("ERROR_USER_DISABLED"))
        assertEquals(ErrorAuth.DemasiadosIntentos, clasificarCodigoAuth("ERROR_TOO_MANY_REQUESTS"))
    }

    @Test
    fun `un codigo desconocido no se muestra crudo`() {
        assertEquals(ErrorAuth.Otro, clasificarCodigoAuth("ERROR_ALGO_NUEVO"))
        assertEquals(ErrorAuth.Otro, clasificarCodigoAuth(null))
    }
}

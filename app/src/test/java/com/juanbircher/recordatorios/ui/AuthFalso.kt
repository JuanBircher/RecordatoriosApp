package com.juanbircher.recordatorios.ui

import com.juanbircher.recordatorios.data.auth.AuthRepository
import com.juanbircher.recordatorios.domain.ErrorAuth
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.domain.MotivoSinSesion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Autenticación falsa: registra las llamadas y responde lo que la prueba le indique. */
class AuthFalso(inicial: EstadoSesion = EstadoSesion.SinSesion()) : AuthRepository {
    override val sesion = MutableStateFlow(inicial)

    /** Si no es `null`, registrar e iniciar sesión fallan con este error. */
    var error: ErrorAuth? = null
    var latenciaMs = 100L

    val ingresos = mutableListOf<Pair<String, String>>()
    val registros = mutableListOf<Pair<String, String>>()
    var verificaciones = 0
    var cierres = 0

    override suspend fun registrar(correo: String, contrasena: String): ErrorAuth? {
        registros += correo to contrasena
        return responder(correo)
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): ErrorAuth? {
        ingresos += correo to contrasena
        return responder(correo)
    }

    private suspend fun responder(correo: String): ErrorAuth? {
        delay(latenciaMs)
        error?.let { return it }
        sesion.value = EstadoSesion.Activa(uid = "uid-$correo", correo = correo)
        return null
    }

    override suspend fun cerrarSesion() {
        cierres++
        sesion.value = EstadoSesion.SinSesion(MotivoSinSesion.CerroSesion)
    }

    override suspend fun verificarSesion() {
        verificaciones++
    }
}

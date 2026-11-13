package com.juanbircher.recordatorios.data.sync

import android.util.Log
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.auth.AuthRepository
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.domain.EstadoSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Une la sesión, la conectividad y los datos. Es el único lugar que decide **cuándo** sincronizar; el
 * repositorio decide **cómo**.
 *
 * - Al abrir la app comprueba con el proveedor que la sesión guardada siga siendo válida.
 * - Cuando hay una cuenta activa, prepara sus datos locales (aunque no haya conexión) y, si hay red,
 *   sincroniza. Lo mismo al recuperar la conexión o al cambiar de cuenta.
 * - Si el servicio rechaza la identidad ([CausaFallo.NoAutorizado]), vuelve a comprobar la sesión: una
 *   cuenta deshabilitada termina en la pantalla de acceso con el aviso correspondiente.
 */
class CoordinadorSesion(
    private val auth: AuthRepository,
    private val repositorio: RecordatoriosRepository,
    private val conectividad: Conectividad
) {
    fun iniciar(alcance: CoroutineScope) {
        alcance.launch { auth.verificarSesion() }

        alcance.launch {
            auth.sesion
                .map { (it as? EstadoSesion.Activa)?.uid }
                .distinctUntilChanged()
                .combine(conectividad.conexion) { uid, conectado -> uid to conectado }
                .distinctUntilChanged()
                .collectLatest { (uid, conectado) ->
                    if (uid == null) return@collectLatest
                    try {
                        repositorio.prepararSesion(uid)
                        if (conectado) repositorio.sincronizar()
                    } catch (e: IOException) {
                        // Falla del almacenamiento local: se reintenta con el próximo evento.
                        Log.w(ETIQUETA, "No se pudo preparar o sincronizar la cuenta", e)
                    }
                }
        }

        alcance.launch {
            repositorio.estadoSincronizacion
                .filter { it == EstadoSync.Fallo(CausaFallo.NoAutorizado) }
                .collect { auth.verificarSesion() }
        }
    }

    private companion object {
        const val ETIQUETA = "CoordinadorSesion"
    }
}

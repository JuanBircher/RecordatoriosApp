package com.juanbircher.recordatorios.data.sync

import com.juanbircher.recordatorios.data.aDto
import com.juanbircher.recordatorios.data.aEntidad
import com.juanbircher.recordatorios.data.local.RecordatorioDao
import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.data.remote.RecordatoriosRemoteDataSource
import com.juanbircher.recordatorios.data.remote.ResultadoRemoto
import com.juanbircher.recordatorios.data.remote.causaDeFallo
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/** Qué está haciendo la sincronización en este momento; lo muestra la interfaz. */
sealed interface EstadoSync {
    data object Inactivo : EstadoSync
    data object Sincronizando : EstadoSync

    /** La última sincronización no pudo completarse. Los datos locales siguen intactos. */
    data class Fallo(val causa: CausaFallo) : EstadoSync
}

/** Cómo terminó una corrida de sincronización. */
sealed interface ResultadoSincronizacion {
    data class Completa(val enviados: Int, val recibidos: Int) : ResultadoSincronizacion
    data class Incompleta(val causa: CausaFallo) : ResultadoSincronizacion

    /** Ya había otra corrida; el pedido se anotó y se atiende cuando esa termine. */
    data object YaEnCurso : ResultadoSincronizacion

    /**
     * No hay una sesión activa, o cambió durante la corrida. No se envía nada con una identidad que ya no
     * corresponde: lo pendiente espera a que su cuenta vuelva a ingresar.
     */
    data object SinSesion : ResultadoSincronizacion
}

/**
 * Sincronización entre Room y la colección de la cuenta en Firestore, en dos pasos: primero **envía** lo
 * pendiente y después **descarga** lo que hay en el servicio.
 *
 * Cómo evita duplicar:
 * 1. **Una sola corrida a la vez** ([mutex]): si varios eventos piden sincronizar juntos (recuperar la
 *    conexión, abrir la app, guardar un cambio), solo uno corre; los demás anotan que hay que volver a pasar.
 * 2. **Id estable**: el documento remoto se llama como la `claveCliente` del registro. Crear y modificar son
 *    la misma operación (reemplazar ese documento), así que reintentar nunca genera una copia.
 * 3. **Estado por registro**: un registro solo pasa a `Sincronizado` cuando el servicio lo confirmó.
 * 4. **La descarga busca por `claveCliente`**: lo que ya existe se actualiza, no se inserta de nuevo.
 *
 * Cómo respeta la identidad:
 * - El uid se toma al empezar ([uidActual]) y todo se hace en nombre de esa cuenta. Si la sesión cambia o
 *   se cierra en el medio, la corrida se corta: no se sigue enviando ni descargando con la cuenta anterior.
 * - Si el servicio responde [ResultadoRemoto.NoAutorizado], se corta sin tocar los registros: no es culpa
 *   del dato, y quedan pendientes hasta que haya una sesión válida.
 *
 * Los datos locales nunca se pierden por un fallo: el peor caso es que sigan pendientes.
 *
 * @param uidActual La cuenta con sesión activa en este momento, o `null` si no hay sesión.
 */
class SincronizadorRecordatorios(
    private val dao: RecordatorioDao,
    private val remoto: RecordatoriosRemoteDataSource,
    private val categorias: Set<Categoria>,
    private val uidActual: () -> String?,
    private val maxIntentos: Int = 3
) {
    private val mutex = Mutex()
    private val otraPasada = AtomicBoolean(false)
    private val _estado = MutableStateFlow<EstadoSync>(EstadoSync.Inactivo)
    val estado: StateFlow<EstadoSync> = _estado.asStateFlow()

    suspend fun sincronizar(): ResultadoSincronizacion {
        if (uidActual() == null) return ResultadoSincronizacion.SinSesion
        if (!mutex.tryLock()) {
            otraPasada.set(true)
            return ResultadoSincronizacion.YaEnCurso
        }
        try {
            var resultado: ResultadoSincronizacion
            do {
                otraPasada.set(false)
                _estado.value = EstadoSync.Sincronizando
                val uid = uidActual()
                resultado = if (uid == null) ResultadoSincronizacion.SinSesion else ejecutar(uid)
                // Se vuelve a pasar si alguien lo pidió mientras tanto y la corrida terminó bien, o si se cortó
                // porque cambió la cuenta: el pedido de la cuenta nueva no se pierde.
            } while (otraPasada.get() && (resultado is ResultadoSincronizacion.Completa || resultado == ResultadoSincronizacion.SinSesion))
            _estado.value = when (resultado) {
                is ResultadoSincronizacion.Incompleta -> EstadoSync.Fallo(resultado.causa)
                else -> EstadoSync.Inactivo
            }
            return resultado
        } catch (e: Throwable) {
            _estado.value = EstadoSync.Inactivo
            throw e
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Olvida el resultado de la última corrida. Se llama al ingresar otra cuenta, para que no vea el fallo de
     * sincronización de la anterior. Si hay una corrida en curso, ella misma actualizará el estado.
     */
    fun reiniciarEstado() {
        if (!mutex.isLocked) _estado.value = EstadoSync.Inactivo
    }

    private fun sigueLaSesion(uid: String) = uidActual() == uid

    private suspend fun ejecutar(uid: String): ResultadoSincronizacion {
        var causa: CausaFallo? = null
        var enviados = 0

        for (pendiente in dao.obtenerPendientes(uid)) {
            if (!sigueLaSesion(uid)) return ResultadoSincronizacion.SinSesion
            val resultado = enviar(uid, pendiente)
            if (resultado is ResultadoRemoto.Exito) {
                enviados++
                continue
            }
            if (resultado == ResultadoRemoto.NoAutorizado) return ResultadoSincronizacion.Incompleta(CausaFallo.NoAutorizado)
            causa = causa ?: resultado.causaDeFallo()
            registrarFallo(uid, pendiente, resultado)
            // Un fallo transitorio (sin red, timeout, servicio caído) afecta a todo el resto de la cola.
            if (resultado.esTransitorio) break
        }
        if (causa != null && causa in CAUSAS_TRANSITORIAS) return ResultadoSincronizacion.Incompleta(causa)
        if (!sigueLaSesion(uid)) return ResultadoSincronizacion.SinSesion

        val recibidos = when (val descarga = descargar(uid)) {
            null -> return ResultadoSincronizacion.SinSesion
            is ResultadoRemoto.Exito -> descarga.valor
            else -> return ResultadoSincronizacion.Incompleta(descarga.causaDeFallo() ?: CausaFallo.RespuestaInvalida)
        }
        return if (causa != null) {
            ResultadoSincronizacion.Incompleta(causa)
        } else {
            ResultadoSincronizacion.Completa(enviados, recibidos)
        }
    }

    // ---- envío -----------------------------------------------------------------------------

    /** Envía una operación pendiente. Si la completa, deja la base al día. */
    private suspend fun enviar(uid: String, pendiente: RecordatorioEntity): ResultadoRemoto<Unit> =
        if (pendiente.eliminado) enviarBorrado(uid, pendiente) else enviarGuardado(uid, pendiente)

    private suspend fun enviarBorrado(uid: String, pendiente: RecordatorioEntity): ResultadoRemoto<Unit> {
        if (!pendiente.enRemoto) {
            // Nunca llegó al servicio: no hay nada que borrar allá.
            dao.eliminar(pendiente.id)
            return ResultadoRemoto.Exito(Unit)
        }
        val resultado = remoto.eliminar(uid, pendiente.claveCliente)
        if (resultado is ResultadoRemoto.Exito) dao.eliminar(pendiente.id)
        return resultado
    }

    private suspend fun enviarGuardado(uid: String, pendiente: RecordatorioEntity): ResultadoRemoto<Unit> {
        val resultado = remoto.guardar(uid, pendiente.claveCliente, pendiente.aDto())
        if (resultado is ResultadoRemoto.Exito) confirmar(uid, pendiente)
        return resultado
    }

    /**
     * Anota la confirmación del servicio. Si la persona cambió el registro mientras se enviaba, no se
     * marca como sincronizado: queda pendiente con su versión nueva (pero ya consta que existe en remoto).
     */
    private suspend fun confirmar(uid: String, enviado: RecordatorioEntity) {
        val actual = dao.obtenerPorClave(uid, enviado.claveCliente) ?: return
        val sinCambios = actual.copy(enRemoto = enviado.enRemoto) == enviado
        dao.actualizar(
            if (sinCambios) {
                actual.copy(enRemoto = true, sincronizacion = EstadoSincronizacion.Sincronizado.name, intentos = 0)
            } else {
                actual.copy(enRemoto = true)
            }
        )
    }

    /** Un fallo transitorio cuenta un intento; si se agotan, o si el servicio rechazó el dato, pasa a error. */
    private suspend fun registrarFallo(uid: String, pendiente: RecordatorioEntity, resultado: ResultadoRemoto<*>) {
        val actual = dao.obtenerPorClave(uid, pendiente.claveCliente) ?: return
        val intentos = actual.intentos + 1
        val definitivo = !resultado.esTransitorio || intentos >= maxIntentos
        dao.actualizar(
            actual.copy(
                intentos = intentos,
                // Un envío que venció por tiempo pudo llegar igual (el SDK lo termina en segundo plano): desde
                // ahora se lo trata como existente, así un borrado posterior también se pide al servicio.
                enRemoto = actual.enRemoto || resultado == ResultadoRemoto.Timeout,
                sincronizacion = if (definitivo) EstadoSincronizacion.Error.name else actual.sincronizacion
            )
        )
    }

    // ---- descarga --------------------------------------------------------------------------

    /**
     * Trae los recordatorios de la cuenta y los incorpora a Room. Devuelve cuántos cambios se aplicaron,
     * o `null` si la sesión cambió mientras tanto.
     *
     * Lo que otro dispositivo borró se detecta por ausencia: si la lista completa del servicio no trae una
     * clave que acá figura como sincronizada, ese registro ya no existe y se quita también de Room. Solo
     * se hace con una lista leída con éxito del servicio, y nunca con registros que tienen cambios locales.
     */
    private suspend fun descargar(uid: String): ResultadoRemoto<Int>? {
        val lista = when (val resultado = remoto.listar(uid)) {
            is ResultadoRemoto.Exito -> resultado.valor
            else -> return resultado.comoFallo()
        }
        var aplicados = 0
        for (dto in lista) {
            if (!sigueLaSesion(uid)) return null
            val clave = dto.claveCliente ?: continue
            val local = dao.obtenerPorClave(uid, clave)
            when {
                // Cambios locales sin confirmar: ganan los locales; ya se enviarán.
                local != null && (local.eliminado || local.sincronizacion != EstadoSincronizacion.Sincronizado.name) -> Unit
                local == null -> dto.aEntidad(uid, categorias)?.let { dao.insertar(it); aplicados++ }
                else -> dto.aEntidad(uid, categorias, local)?.takeIf { it != local }?.let { dao.actualizar(it); aplicados++ }
            }
        }
        // Las claves de todos los documentos, incluso los inválidos: un documento dañado no es un borrado.
        val clavesRemotas = lista.mapNotNull { it.claveCliente }.toSet()
        for (local in dao.obtenerSincronizadosEnRemoto(uid)) {
            if (!sigueLaSesion(uid)) return null
            if (local.claveCliente !in clavesRemotas) {
                dao.eliminar(local.id)
                aplicados++
            }
        }
        return ResultadoRemoto.Exito(aplicados)
    }

    private fun ResultadoRemoto<*>.comoFallo(): ResultadoRemoto<Nothing> = when (this) {
        is ResultadoRemoto.SolicitudInvalida -> this
        is ResultadoRemoto.ErrorServidor -> this
        ResultadoRemoto.Timeout -> ResultadoRemoto.Timeout
        ResultadoRemoto.SinConexion -> ResultadoRemoto.SinConexion
        ResultadoRemoto.NoAutorizado -> ResultadoRemoto.NoAutorizado
        else -> ResultadoRemoto.RespuestaInvalida
    }

    private companion object {
        val CAUSAS_TRANSITORIAS = setOf(CausaFallo.SinConexion, CausaFallo.Timeout, CausaFallo.ErrorServidor)
    }
}

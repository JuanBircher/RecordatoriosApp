package com.juanbircher.recordatorios.data.sync

import com.juanbircher.recordatorios.data.aDto
import com.juanbircher.recordatorios.data.aEntidad
import com.juanbircher.recordatorios.data.local.RecordatorioDao
import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.data.remote.RecordatorioDto
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
}

/**
 * Sincronización entre Room y el servicio remoto, en dos pasos: primero **envía** lo pendiente y después
 * **descarga** lo que hay en el servicio.
 *
 * Cómo evita duplicar:
 * 1. **Una sola corrida a la vez** ([mutex]): si varios eventos piden sincronizar juntos (recuperar la
 *    conexión, abrir la app, guardar un cambio), solo uno corre; los demás anotan que hay que volver a pasar.
 * 2. **Clave de idempotencia**: cada creación viaja con la `claveCliente` del registro. Si el servicio ya la
 *    recibió, devuelve el registro existente en lugar de crear otro; reintentar es seguro.
 * 3. **Estado por registro**: un registro solo pasa a `Sincronizado` cuando el servicio lo confirmó, y
 *    conserva su `claveCliente` hasta entonces.
 * 4. **La descarga busca por `claveCliente`**: lo que ya existe se actualiza, no se inserta de nuevo.
 *
 * Los datos locales nunca se pierden por un fallo: el peor caso es que sigan pendientes.
 */
class SincronizadorRecordatorios(
    private val dao: RecordatorioDao,
    private val remoto: RecordatoriosRemoteDataSource,
    private val categorias: Set<Categoria>,
    private val maxIntentos: Int = 3
) {
    private val mutex = Mutex()
    private val otraPasada = AtomicBoolean(false)
    private val _estado = MutableStateFlow<EstadoSync>(EstadoSync.Inactivo)
    val estado: StateFlow<EstadoSync> = _estado.asStateFlow()

    suspend fun sincronizar(): ResultadoSincronizacion {
        if (!mutex.tryLock()) {
            otraPasada.set(true)
            return ResultadoSincronizacion.YaEnCurso
        }
        try {
            var resultado: ResultadoSincronizacion
            do {
                otraPasada.set(false)
                _estado.value = EstadoSync.Sincronizando
                resultado = ejecutar()
            } while (otraPasada.get() && resultado is ResultadoSincronizacion.Completa)
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

    private suspend fun ejecutar(): ResultadoSincronizacion {
        var causa: CausaFallo? = null
        var enviados = 0

        for (pendiente in dao.obtenerPendientes()) {
            val resultado = enviar(pendiente)
            if (resultado is ResultadoRemoto.Exito || resultado == ResultadoRemoto.NoEncontrado) {
                enviados++
                continue
            }
            causa = causa ?: resultado.causaDeFallo()
            registrarFallo(pendiente, resultado)
            // Un fallo transitorio (sin red, timeout, servicio caído) afecta a todo el resto de la cola.
            if (resultado.esTransitorio) break
        }
        if (causa != null && causa in CAUSAS_TRANSITORIAS) return ResultadoSincronizacion.Incompleta(causa)

        val recibidos = when (val descarga = descargar()) {
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
    private suspend fun enviar(pendiente: RecordatorioEntity): ResultadoRemoto<*> = when {
        pendiente.eliminado -> enviarBorrado(pendiente)
        pendiente.idRemoto == null -> enviarCreacion(pendiente)
        else -> enviarActualizacion(pendiente, pendiente.idRemoto)
    }

    private suspend fun enviarBorrado(pendiente: RecordatorioEntity): ResultadoRemoto<*> {
        val idRemoto = pendiente.idRemoto
        if (idRemoto == null) {
            // Nunca llegó al servicio: no hay nada que borrar allá.
            dao.eliminar(pendiente.id)
            return ResultadoRemoto.Exito(Unit)
        }
        val resultado = remoto.eliminar(idRemoto)
        // 404: el servicio ya no lo tiene, que es lo que se buscaba.
        if (resultado is ResultadoRemoto.Exito || resultado == ResultadoRemoto.NoEncontrado) dao.eliminar(pendiente.id)
        return resultado
    }

    private suspend fun enviarCreacion(pendiente: RecordatorioEntity): ResultadoRemoto<*> {
        val resultado = remoto.crear(pendiente.claveCliente, pendiente.aDto())
        if (resultado is ResultadoRemoto.Exito) confirmar(pendiente, resultado.valor)
        return resultado
    }

    private suspend fun enviarActualizacion(pendiente: RecordatorioEntity, idRemoto: Long): ResultadoRemoto<*> {
        val resultado = remoto.actualizar(idRemoto, pendiente.aDto())
        return when {
            resultado is ResultadoRemoto.Exito -> resultado.also { confirmar(pendiente, resultado.valor) }
            // El servicio perdió el registro: se vuelve a crear con la misma clave, sin duplicar.
            resultado == ResultadoRemoto.NoEncontrado -> enviarCreacion(pendiente.copy(idRemoto = null))
            else -> resultado
        }
    }

    /**
     * Anota la confirmación del servicio. Si la persona cambió el registro mientras se enviaba, no se
     * marca como sincronizado: conserva el id remoto y sigue pendiente con su versión nueva.
     */
    private suspend fun confirmar(enviado: RecordatorioEntity, respuesta: RecordatorioDto) {
        val idRemoto = respuesta.id ?: return
        val actual = dao.obtenerPorClave(enviado.claveCliente) ?: return
        val sinCambios = actual.copy(idRemoto = enviado.idRemoto) == enviado
        dao.actualizar(
            if (sinCambios) {
                actual.copy(idRemoto = idRemoto, sincronizacion = EstadoSincronizacion.Sincronizado.name, intentos = 0)
            } else {
                actual.copy(idRemoto = idRemoto)
            }
        )
    }

    /** Un fallo transitorio cuenta un intento; si se agotan, o si el servicio rechazó el dato, pasa a error. */
    private suspend fun registrarFallo(pendiente: RecordatorioEntity, resultado: ResultadoRemoto<*>) {
        val actual = dao.obtenerPorClave(pendiente.claveCliente) ?: return
        val intentos = actual.intentos + 1
        val definitivo = !resultado.esTransitorio || intentos >= maxIntentos
        dao.actualizar(
            actual.copy(
                intentos = intentos,
                sincronizacion = if (definitivo) EstadoSincronizacion.Error.name else actual.sincronizacion
            )
        )
    }

    // ---- descarga --------------------------------------------------------------------------

    /** Trae los recordatorios del servicio y los incorpora a Room. Devuelve cuántos se aplicaron. */
    private suspend fun descargar(): ResultadoRemoto<Int> {
        val lista = when (val resultado = remoto.listar()) {
            is ResultadoRemoto.Exito -> resultado.valor
            ResultadoRemoto.NoEncontrado -> return ResultadoRemoto.RespuestaInvalida
            else -> return resultado.comoFallo()
        }
        var aplicados = 0
        for (dto in lista) {
            val clave = dto.claveCliente ?: continue
            val local = dao.obtenerPorClave(clave)
            when {
                // Cambios locales sin confirmar: ganan los locales; ya se enviarán.
                local != null && (local.eliminado || local.sincronizacion != EstadoSincronizacion.Sincronizado.name) -> Unit
                local == null -> dto.aEntidad(categorias)?.let { dao.insertar(it); aplicados++ }
                else -> dto.aEntidad(categorias, local)?.takeIf { it != local }?.let { dao.actualizar(it); aplicados++ }
            }
        }
        return ResultadoRemoto.Exito(aplicados)
    }

    private fun ResultadoRemoto<*>.comoFallo(): ResultadoRemoto<Nothing> = when (this) {
        is ResultadoRemoto.SolicitudInvalida -> this
        is ResultadoRemoto.ErrorServidor -> this
        ResultadoRemoto.Timeout -> ResultadoRemoto.Timeout
        ResultadoRemoto.SinConexion -> ResultadoRemoto.SinConexion
        else -> ResultadoRemoto.RespuestaInvalida
    }

    private companion object {
        val CAUSAS_TRANSITORIAS = setOf(CausaFallo.SinConexion, CausaFallo.Timeout, CausaFallo.ErrorServidor)
    }
}

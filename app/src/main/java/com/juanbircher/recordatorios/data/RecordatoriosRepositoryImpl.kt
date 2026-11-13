package com.juanbircher.recordatorios.data

import android.database.sqlite.SQLiteException
import com.juanbircher.recordatorios.data.local.RecordatorioDao
import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.data.preferences.PreferenciasDataSource
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.data.remote.RecordatoriosRemoteDataSource
import com.juanbircher.recordatorios.data.remote.ResultadoRemoto
import com.juanbircher.recordatorios.data.remote.causaDeFallo
import com.juanbircher.recordatorios.data.sync.EstadoSync
import com.juanbircher.recordatorios.data.sync.ResultadoSincronizacion
import com.juanbircher.recordatorios.data.sync.SincronizadorRecordatorios
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.domain.TemaApp
import com.juanbircher.recordatorios.domain.ordenadosPor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Repositorio local-first, separado por cuenta.
 *
 * - **Lecturas**: siempre desde Room y siempre de la cuenta con sesión activa ([uidActivo]). Cuando la
 *   sesión cambia, cada flujo pasa a leer las filas de la nueva cuenta; sin sesión, no hay nada visible.
 * - **Escrituras**: primero en Room, a nombre de la cuenta activa y marcadas como pendientes; recién
 *   después se pide una sincronización en segundo plano ([alcance]). Si no hay conexión, quedan pendientes
 *   y se envían cuando vuelva.
 * - **Preferencias**: se leen de DataStore y condicionan lo que se consulta a Room (completados, orden).
 *   Son del dispositivo, no de la cuenta: no contienen datos personales.
 *
 * @param uidActivo La cuenta con sesión activa, o `null` si nadie ingresó. Lo deriva el repositorio de
 *   autenticación; acá no se conoce Firebase.
 * @param alcance Alcance de vida de la app, para que la sincronización no se cancele al salir de una pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordatoriosRepositoryImpl(
    private val dao: RecordatorioDao,
    private val preferenciasFuente: PreferenciasDataSource,
    private val sincronizador: SincronizadorRecordatorios,
    override val categorias: Set<Categoria>,
    private val alcance: CoroutineScope,
    private val uidActivo: StateFlow<String?>,
    private val remoto: RecordatoriosRemoteDataSource,
    private val nuevaClave: () -> String = { UUID.randomUUID().toString() }
) : RecordatoriosRepository {

    override val preferencias: Flow<Preferencias> = preferenciasFuente.preferencias

    override val estadoSincronizacion: StateFlow<EstadoSync> = sincronizador.estado

    override fun observarResumenSincronizacion(): Flow<ResumenSincronizacion> =
        porCuenta(ResumenSincronizacion()) { uid ->
            combine(
                dao.contarPorSincronizacion(uid, EstadoSincronizacion.Pendiente.name),
                dao.contarPorSincronizacion(uid, EstadoSincronizacion.Error.name)
            ) { pendientes, conError -> ResumenSincronizacion(pendientes, conError) }
        }
            .distinctUntilChanged()
            .comoIOException()

    override fun observarRecordatorios(): Flow<List<Recordatorio>> =
        combine(
            preferenciasFuente.preferencias.map { it.mostrarCompletados to it.orden }.distinctUntilChanged(),
            uidActivo
        ) { (mostrarCompletados, orden), uid -> Triple(uid, mostrarCompletados, orden) }
            .distinctUntilChanged()
            .flatMapLatest { (uid, mostrarCompletados, orden) ->
                if (uid == null) return@flatMapLatest flowOf(emptyList())
                val filas = if (mostrarCompletados) dao.observarTodos(uid) else dao.observarSinCompletados(uid)
                filas.map { lista -> lista.map(RecordatorioEntity::aDominio).ordenadosPor(orden) }
            }
            .comoIOException()

    override fun observarRecordatorio(id: Long): Flow<Recordatorio?> =
        porCuenta(null) { uid -> dao.observarPorId(uid, id).map { it?.aDominio() } }.comoIOException()

    override suspend fun obtenerRecordatorio(id: Long): Recordatorio? {
        val uid = uidActivo.value ?: return null
        return almacenamiento { dao.obtenerPorId(uid, id)?.aDominio() }
    }

    override suspend fun guardarRecordatorio(recordatorio: Recordatorio): Recordatorio {
        val uid = cuentaActiva()
        val id = almacenamiento { dao.insertar(recordatorio.aEntidadNueva(nuevaClave(), uid)) }
        pedirSincronizacion()
        return recordatorio.copy(id = id, sincronizacion = EstadoSincronizacion.Pendiente)
    }

    override suspend fun actualizarRecordatorio(recordatorio: Recordatorio) {
        val uid = cuentaActiva()
        val actualizado = almacenamiento {
            val existente = dao.obtenerPorId(uid, recordatorio.id) ?: return@almacenamiento false
            dao.actualizar(existente.conCambiosDe(recordatorio))
            true
        }
        if (actualizado) pedirSincronizacion()
    }

    override suspend fun eliminarRecordatorio(id: Long) {
        val uid = cuentaActiva()
        val marcado = almacenamiento {
            val existente = dao.obtenerPorId(uid, id) ?: return@almacenamiento false
            // Se oculta ya; la fila se borra de verdad cuando el servicio confirme (o enseguida, si nunca llegó).
            dao.actualizar(
                existente.copy(eliminado = true, sincronizacion = EstadoSincronizacion.Pendiente.name, intentos = 0)
            )
            true
        }
        if (marcado) pedirSincronizacion()
    }

    override suspend fun sincronizar(): ResultadoSincronizacion = almacenamiento { sincronizador.sincronizar() }

    override suspend fun reintentarFallidos(): ResultadoSincronizacion {
        val uid = cuentaActiva()
        almacenamiento { dao.reencolarErrores(uid) }
        return sincronizar()
    }

    override suspend fun prepararSesion(uid: String) {
        sincronizador.reiniciarEstado()
        almacenamiento { dao.adoptarHuerfanas(uid) }
    }

    override suspend fun probarAccesoAOtraCuenta(uid: String): ResultadoPruebaAcceso =
        when (val resultado = remoto.listar(uid)) {
            is ResultadoRemoto.Exito -> ResultadoPruebaAcceso.Permitido(resultado.valor.size)
            else -> ResultadoPruebaAcceso.Rechazado(resultado.causaDeFallo() ?: CausaFallo.RespuestaInvalida)
        }

    override suspend fun actualizarTema(tema: TemaApp) = preferenciasFuente.actualizarTema(tema)

    override suspend fun actualizarMostrarCompletados(mostrar: Boolean) =
        preferenciasFuente.actualizarMostrarCompletados(mostrar)

    override suspend fun actualizarOrden(orden: OrdenListado) = preferenciasFuente.actualizarOrden(orden)

    private fun pedirSincronizacion() {
        alcance.launch { sincronizador.sincronizar() }
    }

    /**
     * La cuenta a cuyo nombre se escribe. Sin sesión se rechaza la escritura con [IOException], el error
     * que los ViewModel ya saben mostrar: un registro nunca queda sin dueño ni a nombre de otra cuenta.
     */
    private fun cuentaActiva(): String = uidActivo.value ?: throw IOException("No hay una sesión activa")

    /** Lee de la cuenta activa y vuelve a leer cada vez que la sesión cambia; sin sesión emite [sinSesion]. */
    private fun <T> porCuenta(sinSesion: T, lectura: (String) -> Flow<T>): Flow<T> =
        uidActivo.flatMapLatest { uid -> if (uid == null) flowOf(sinSesion) else lectura(uid) }

    /** Un error de SQLite sale como [IOException]: es el contrato de la interfaz y lo que los ViewModel esperan. */
    private inline fun <T> almacenamiento(operacion: () -> T): T =
        try {
            operacion()
        } catch (e: SQLiteException) {
            throw IOException("Falló el almacenamiento local", e)
        }

    private fun <T> Flow<T>.comoIOException(): Flow<T> = catch { causa ->
        throw if (causa is SQLiteException) IOException("Falló el almacenamiento local", causa) else causa
    }
}

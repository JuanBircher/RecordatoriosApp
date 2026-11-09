package com.juanbircher.recordatorios.data

import android.database.sqlite.SQLiteException
import com.juanbircher.recordatorios.data.local.RecordatorioDao
import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.data.preferences.PreferenciasDataSource
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Repositorio local-first.
 *
 * - **Lecturas**: siempre desde Room. La interfaz no espera a la red ni depende de ella.
 * - **Escrituras**: primero en Room, marcadas como pendientes; recién después se pide una sincronización
 *   en segundo plano ([alcance]). Si no hay conexión, quedan pendientes y se envían cuando vuelva.
 * - **Preferencias**: se leen de DataStore y condicionan lo que se consulta a Room (completados, orden).
 *
 * @param alcance Alcance de vida de la app, para que la sincronización no se cancele al salir de una pantalla.
 */
class RecordatoriosRepositoryImpl(
    private val dao: RecordatorioDao,
    private val preferenciasFuente: PreferenciasDataSource,
    private val sincronizador: SincronizadorRecordatorios,
    override val categorias: Set<Categoria>,
    private val alcance: CoroutineScope,
    private val nuevaClave: () -> String = { UUID.randomUUID().toString() }
) : RecordatoriosRepository {

    override val preferencias: Flow<Preferencias> = preferenciasFuente.preferencias

    override val estadoSincronizacion: StateFlow<EstadoSync> = sincronizador.estado

    override fun observarResumenSincronizacion(): Flow<ResumenSincronizacion> = combine(
        dao.contarPorSincronizacion(EstadoSincronizacion.Pendiente.name),
        dao.contarPorSincronizacion(EstadoSincronizacion.Error.name)
    ) { pendientes, conError -> ResumenSincronizacion(pendientes, conError) }
        .distinctUntilChanged()
        .comoIOException()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarRecordatorios(): Flow<List<Recordatorio>> =
        preferenciasFuente.preferencias
            .map { it.mostrarCompletados to it.orden }
            .distinctUntilChanged()
            .flatMapLatest { (mostrarCompletados, orden) ->
                val filas = if (mostrarCompletados) dao.observarTodos() else dao.observarSinCompletados()
                filas.map { lista -> lista.map(RecordatorioEntity::aDominio).ordenadosPor(orden) }
            }
            .comoIOException()

    override fun observarRecordatorio(id: Long): Flow<Recordatorio?> =
        dao.observarPorId(id).map { it?.aDominio() }.comoIOException()

    override suspend fun obtenerRecordatorio(id: Long): Recordatorio? =
        almacenamiento { dao.obtenerPorId(id)?.aDominio() }

    override suspend fun guardarRecordatorio(recordatorio: Recordatorio): Recordatorio {
        val id = almacenamiento { dao.insertar(recordatorio.aEntidadNueva(nuevaClave())) }
        pedirSincronizacion()
        return recordatorio.copy(id = id, sincronizacion = EstadoSincronizacion.Pendiente)
    }

    override suspend fun actualizarRecordatorio(recordatorio: Recordatorio) {
        val actualizado = almacenamiento {
            val existente = dao.obtenerPorId(recordatorio.id) ?: return@almacenamiento false
            dao.actualizar(existente.conCambiosDe(recordatorio))
            true
        }
        if (actualizado) pedirSincronizacion()
    }

    override suspend fun eliminarRecordatorio(id: Long) {
        val marcado = almacenamiento {
            val existente = dao.obtenerPorId(id) ?: return@almacenamiento false
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
        almacenamiento { dao.reencolarErrores() }
        return sincronizar()
    }

    override suspend fun actualizarTema(tema: TemaApp) = preferenciasFuente.actualizarTema(tema)

    override suspend fun actualizarMostrarCompletados(mostrar: Boolean) =
        preferenciasFuente.actualizarMostrarCompletados(mostrar)

    override suspend fun actualizarOrden(orden: OrdenListado) = preferenciasFuente.actualizarOrden(orden)

    private fun pedirSincronizacion() {
        alcance.launch { sincronizador.sincronizar() }
    }

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

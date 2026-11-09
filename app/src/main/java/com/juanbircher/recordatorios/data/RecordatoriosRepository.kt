package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.data.sync.EstadoSync
import com.juanbircher.recordatorios.data.sync.ResultadoSincronizacion
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.domain.TemaApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Cuántos recordatorios esperan enviarse y cuántos necesitan un nuevo intento manual. */
data class ResumenSincronizacion(val pendientes: Int = 0, val conError: Int = 0)

/**
 * Única puerta de la interfaz hacia los datos. Coordina Room (fuente de verdad), DataStore (preferencias)
 * y el servicio remoto. Los ViewModel dependen de esta interfaz: nunca ven el DAO, los DTO ni Retrofit.
 *
 * Todo lo que devuelve son modelos de dominio. Si el almacenamiento local falla, las operaciones lanzan
 * [java.io.IOException]; los problemas de red **no** se propagan: quedan reflejados en
 * [estadoSincronizacion] y en el estado de sincronización de cada recordatorio.
 */
interface RecordatoriosRepository {

    /** Las categorías que se pueden elegir al crear un recordatorio. */
    val categorias: Set<Categoria>

    /** Preferencias actuales; vuelve a emitir cuando cambian. */
    val preferencias: Flow<Preferencias>

    /** Qué hace la sincronización ahora y si la última corrida falló. */
    val estadoSincronizacion: StateFlow<EstadoSync>

    fun observarResumenSincronizacion(): Flow<ResumenSincronizacion>

    /**
     * Los recordatorios guardados en Room, ordenados según la preferencia y respetando la de «mostrar
     * completados». Emite de nuevo cada vez que la base cambia, aunque no haya conexión.
     */
    fun observarRecordatorios(): Flow<List<Recordatorio>>

    /** Emite el recordatorio y cada cambio; `null` si no existe o fue eliminado. */
    fun observarRecordatorio(id: Long): Flow<Recordatorio?>

    /** Devuelve el recordatorio con ese [id], o `null` si no existe. */
    suspend fun obtenerRecordatorio(id: Long): Recordatorio?

    /** Guarda un recordatorio nuevo en Room (queda pendiente de envío) y lo devuelve con su id. */
    suspend fun guardarRecordatorio(recordatorio: Recordatorio): Recordatorio

    /** Aplica los cambios en Room y los deja pendientes de envío. No hace nada si el recordatorio ya no existe. */
    suspend fun actualizarRecordatorio(recordatorio: Recordatorio)

    /** Lo oculta de inmediato; el borrado en el servicio ocurre cuando haya conexión. */
    suspend fun eliminarRecordatorio(id: Long)

    /** Envía lo pendiente y descarga lo del servicio. Nunca lanza por problemas de red. */
    suspend fun sincronizar(): ResultadoSincronizacion

    /** Vuelve a poner en cola los recordatorios que quedaron en error y sincroniza. */
    suspend fun reintentarFallidos(): ResultadoSincronizacion

    suspend fun actualizarTema(tema: TemaApp)
    suspend fun actualizarMostrarCompletados(mostrar: Boolean)
    suspend fun actualizarOrden(orden: OrdenListado)
}

package com.juanbircher.recordatorios.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla de recordatorios. Las consultas que usa la interfaz devuelven [Flow]: Room vuelve a
 * emitir cada vez que la tabla cambia, así las pantallas se actualizan sin pedir los datos de nuevo.
 * Las funciones `suspend` se ejecutan fuera del hilo principal.
 *
 * Es una interfaz a propósito: las pruebas la reemplazan por una versión en memoria.
 */
@Dao
interface RecordatorioDao {

    /** Listado completo, sin los recordatorios marcados para borrar. */
    @Query("SELECT * FROM recordatorios WHERE eliminado = 0 ORDER BY id")
    fun observarTodos(): Flow<List<RecordatorioEntity>>

    /** Filtro por estado: todos menos los completados. Lo usa la preferencia «mostrar completados». */
    @Query("SELECT * FROM recordatorios WHERE eliminado = 0 AND estadoTipo != 'COMPLETADO' ORDER BY id")
    fun observarSinCompletados(): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE id = :id AND eliminado = 0")
    fun observarPorId(id: Long): Flow<RecordatorioEntity?>

    @Query("SELECT * FROM recordatorios WHERE id = :id AND eliminado = 0")
    suspend fun obtenerPorId(id: Long): RecordatorioEntity?

    @Query("SELECT * FROM recordatorios WHERE claveCliente = :clave")
    suspend fun obtenerPorClave(clave: String): RecordatorioEntity?

    /** Los que hay que enviar al servicio, del más antiguo al más nuevo. */
    @Query("SELECT * FROM recordatorios WHERE sincronizacion = 'Pendiente' ORDER BY id")
    suspend fun obtenerPendientes(): List<RecordatorioEntity>

    /** Cuántos registros (incluidos los marcados para borrar) están en esa situación de sincronización. */
    @Query("SELECT COUNT(*) FROM recordatorios WHERE sincronizacion = :sincronizacion")
    fun contarPorSincronizacion(sincronizacion: String): Flow<Int>

    /** Un fallo definitivo vuelve a la cola cuando la persona pide reintentar. */
    @Query("UPDATE recordatorios SET sincronizacion = 'Pendiente', intentos = 0 WHERE sincronizacion = 'Error'")
    suspend fun reencolarErrores(): Int

    @Insert
    suspend fun insertar(recordatorio: RecordatorioEntity): Long

    @Update
    suspend fun actualizar(recordatorio: RecordatorioEntity)

    @Query("DELETE FROM recordatorios WHERE id = :id")
    suspend fun eliminar(id: Long)
}

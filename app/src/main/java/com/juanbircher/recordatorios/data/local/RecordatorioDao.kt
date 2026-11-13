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
 * **Todas las lecturas reciben el `uid` de la cuenta**: no existe una consulta que devuelva registros de
 * cualquier dueño, así que el repositorio no puede olvidarse del filtro.
 *
 * Es una interfaz a propósito: las pruebas la reemplazan por una versión en memoria.
 */
@Dao
interface RecordatorioDao {

    /** Listado completo de la cuenta, sin los recordatorios marcados para borrar. */
    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND eliminado = 0 ORDER BY id")
    fun observarTodos(uid: String): Flow<List<RecordatorioEntity>>

    /** Filtro por estado: todos menos los completados. Lo usa la preferencia «mostrar completados». */
    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND eliminado = 0 AND estadoTipo != 'COMPLETADO' ORDER BY id")
    fun observarSinCompletados(uid: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND id = :id AND eliminado = 0")
    fun observarPorId(uid: String, id: Long): Flow<RecordatorioEntity?>

    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND id = :id AND eliminado = 0")
    suspend fun obtenerPorId(uid: String, id: Long): RecordatorioEntity?

    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND claveCliente = :clave")
    suspend fun obtenerPorClave(uid: String, clave: String): RecordatorioEntity?

    /** Los que hay que enviar al servicio, del más antiguo al más nuevo. */
    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND sincronizacion = 'Pendiente' ORDER BY id")
    suspend fun obtenerPendientes(uid: String): List<RecordatorioEntity>

    /** Copias confirmadas por el servicio: si el servicio ya no las tiene, se borraron en otro dispositivo. */
    @Query("SELECT * FROM recordatorios WHERE uid = :uid AND sincronizacion = 'Sincronizado' AND enRemoto = 1 AND eliminado = 0")
    suspend fun obtenerSincronizadosEnRemoto(uid: String): List<RecordatorioEntity>

    /** Cuántos registros de la cuenta (incluidos los marcados para borrar) están en esa situación. */
    @Query("SELECT COUNT(*) FROM recordatorios WHERE uid = :uid AND sincronizacion = :sincronizacion")
    fun contarPorSincronizacion(uid: String, sincronizacion: String): Flow<Int>

    /** Un fallo definitivo vuelve a la cola cuando la persona pide reintentar. */
    @Query("UPDATE recordatorios SET sincronizacion = 'Pendiente', intentos = 0 WHERE uid = :uid AND sincronizacion = 'Error'")
    suspend fun reencolarErrores(uid: String): Int

    /**
     * Los registros creados antes de que existieran las cuentas (versión 3) pasan a la cuenta que ingresa
     * primero en este dispositivo. Después ya no quedan filas sin dueño, así que no vuelve a tener efecto.
     */
    @Query("UPDATE recordatorios SET uid = :uid WHERE uid = ''")
    suspend fun adoptarHuerfanas(uid: String): Int

    @Insert
    suspend fun insertar(recordatorio: RecordatorioEntity): Long

    @Update
    suspend fun actualizar(recordatorio: RecordatorioEntity)

    @Query("DELETE FROM recordatorios WHERE id = :id")
    suspend fun eliminar(id: Long)
}

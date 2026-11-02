package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.flow.Flow

/**
 * Operaciones que la app necesita sobre los recordatorios. Los ViewModel dependen de esta interfaz
 * y no de una lista concreta: la fuente se puede reemplazar sin modificar las pantallas.
 *
 * Si la fuente falla, las operaciones lanzan [java.io.IOException].
 */
interface RecordatoriosRepository {

    /** Las categorías que se pueden elegir al crear un recordatorio. */
    val categorias: Set<Categoria>

    /** Emite la lista actual y vuelve a emitir cada vez que cambia. */
    fun observarRecordatorios(): Flow<List<Recordatorio>>

    /** Devuelve el recordatorio con ese [id], o `null` si no existe. */
    suspend fun obtenerRecordatorio(id: Long): Recordatorio?

    /** Guarda un recordatorio nuevo y lo devuelve con el id que le asignó la fuente. */
    suspend fun guardarRecordatorio(recordatorio: Recordatorio): Recordatorio
}

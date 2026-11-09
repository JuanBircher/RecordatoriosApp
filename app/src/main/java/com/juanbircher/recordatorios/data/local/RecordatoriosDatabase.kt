package com.juanbircher.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos local: la fuente de verdad de la interfaz. */
@Database(entities = [RecordatorioEntity::class], version = 1, exportSchema = false)
abstract class RecordatoriosDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao

    companion object {
        private const val NOMBRE = "recordatorios.db"

        /** Crea la instancia. Se llama una sola vez: la guarda [com.juanbircher.recordatorios.RecordatoriosApp]. */
        fun crear(contexto: Context): RecordatoriosDatabase =
            Room.databaseBuilder(contexto.applicationContext, RecordatoriosDatabase::class.java, NOMBRE).build()
    }
}

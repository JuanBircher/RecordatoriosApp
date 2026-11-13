package com.juanbircher.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local: la fuente de verdad de la interfaz.
 *
 * Versión 2 (Actividad 4): cada fila tiene dueño (`uid`), y el id que asignaba el servicio REST se
 * reemplaza por `enRemoto`, porque en Firestore el id del documento es la propia `claveCliente`. El
 * esquema de cada versión se exporta a `app/schemas/`.
 */
@Database(entities = [RecordatorioEntity::class], version = 2, exportSchema = true)
abstract class RecordatoriosDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao

    companion object {
        const val NOMBRE = "recordatorios.db"

        /** Crea la instancia. Se llama una sola vez: la guarda [com.juanbircher.recordatorios.RecordatoriosApp]. */
        fun crear(contexto: Context): RecordatoriosDatabase =
            Room.databaseBuilder(contexto.applicationContext, RecordatoriosDatabase::class.java, NOMBRE)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * De la versión 3 de la app (base v1) a la 4 (base v2). El SQLite de minSdk 26 no permite quitar
 * columnas, así que la tabla se recrea y se copian las filas:
 *
 * - quedan sin dueño (`uid = ''`): la primera cuenta que ingresa en el dispositivo las adopta;
 * - vuelven a `Pendiente`, con `enRemoto = 0`: el servicio REST de la versión anterior ya no existe, así
 *   que su «sincronizado» no significa nada para Firestore y deben subirse a la cuenta;
 * - se descartan los borrados pendientes y los datos de ejemplo del servidor simulado (`semilla-…`).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recordatorios_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uid` TEXT NOT NULL, `claveCliente` TEXT NOT NULL, `enRemoto` INTEGER NOT NULL, " +
                "`titulo` TEXT NOT NULL, `descripcion` TEXT, `categoriaId` TEXT NOT NULL, " +
                "`categoriaNombre` TEXT NOT NULL, `prioridad` INTEGER NOT NULL, `estadoTipo` TEXT NOT NULL, " +
                "`completadoEl` TEXT, `motivoCancelacion` TEXT, `creadoEl` TEXT NOT NULL, `fechaLimite` TEXT, " +
                "`ubicacion` TEXT, `sincronizacion` TEXT NOT NULL, `intentos` INTEGER NOT NULL, " +
                "`eliminado` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `recordatorios_v2` (id, uid, claveCliente, enRemoto, titulo, descripcion, categoriaId, " +
                "categoriaNombre, prioridad, estadoTipo, completadoEl, motivoCancelacion, creadoEl, fechaLimite, " +
                "ubicacion, sincronizacion, intentos, eliminado) " +
                "SELECT id, '', claveCliente, 0, titulo, descripcion, categoriaId, categoriaNombre, prioridad, " +
                "estadoTipo, completadoEl, motivoCancelacion, creadoEl, fechaLimite, ubicacion, 'Pendiente', 0, 0 " +
                "FROM `recordatorios` WHERE eliminado = 0 AND claveCliente NOT LIKE 'semilla-%'"
        )
        db.execSQL("DROP TABLE `recordatorios`")
        db.execSQL("ALTER TABLE `recordatorios_v2` RENAME TO `recordatorios`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recordatorios_claveCliente` ON `recordatorios` (`claveCliente`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordatorios_uid` ON `recordatorios` (`uid`)")
    }
}

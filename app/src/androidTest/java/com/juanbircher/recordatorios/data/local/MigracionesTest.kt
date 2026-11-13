package com.juanbircher.recordatorios.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migración de la base de la versión 3 de la app (Room v1) a la versión 4 (Room v2), contra SQLite real.
 *
 * La v1 no exportaba su esquema, así que no se puede usar MigrationTestHelper: la base vieja se arma con el
 * SQL exacto que generaba Room en la versión 3 y después se abre con Room y [MIGRATION_1_2]. Room valida el
 * esquema resultante contra el de la entidad actual: si la migración dejara una columna o un índice
 * distinto, la apertura fallaría.
 */
@RunWith(AndroidJUnit4::class)
class MigracionesTest {

    private lateinit var contexto: Context

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        contexto.deleteDatabase(NOMBRE)
    }

    @After
    fun limpiar() {
        contexto.deleteDatabase(NOMBRE)
    }

    private fun crearBaseVersion1() {
        val ruta = contexto.getDatabasePath(NOMBRE).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(ruta, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recordatorios` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`claveCliente` TEXT NOT NULL, `idRemoto` INTEGER, `titulo` TEXT NOT NULL, `descripcion` TEXT, " +
                    "`categoriaId` TEXT NOT NULL, `categoriaNombre` TEXT NOT NULL, `prioridad` INTEGER NOT NULL, " +
                    "`estadoTipo` TEXT NOT NULL, `completadoEl` TEXT, `motivoCancelacion` TEXT, `creadoEl` TEXT NOT NULL, " +
                    "`fechaLimite` TEXT, `ubicacion` TEXT, `sincronizacion` TEXT NOT NULL, `intentos` INTEGER NOT NULL, " +
                    "`eliminado` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recordatorios_claveCliente` ON `recordatorios` (`claveCliente`)")
            fun fila(clave: String, idRemoto: Long?, sincronizacion: String, eliminado: Int, intentos: Int = 0) = db.execSQL(
                "INSERT INTO recordatorios (claveCliente, idRemoto, titulo, categoriaId, categoriaNombre, prioridad, " +
                    "estadoTipo, creadoEl, sincronizacion, intentos, eliminado) " +
                    "VALUES (?, ?, ?, 'estudio', 'Estudio', 3, 'PENDIENTE', '2026-11-09', ?, ?, ?)",
                arrayOf(clave, idRemoto, "Recordatorio $clave", sincronizacion, intentos, eliminado)
            )
            fila("sincronizada", idRemoto = 7, sincronizacion = "Sincronizado", eliminado = 0)
            fila("pendiente", idRemoto = null, sincronizacion = "Error", eliminado = 0, intentos = 3)
            fila("borrada", idRemoto = 8, sincronizacion = "Pendiente", eliminado = 1)
            fila("semilla-1", idRemoto = 1, sincronizacion = "Sincronizado", eliminado = 0)
            db.version = 1
        }
    }

    @Test
    fun la_base_de_la_version_3_se_migra_sin_perder_los_recordatorios_reales() = runBlocking {
        crearBaseVersion1()

        val base = Room.databaseBuilder(contexto, RecordatoriosDatabase::class.java, NOMBRE)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val dao = base.recordatorioDao()
            val filas = dao.observarTodos("").first()

            assertEquals(
                "se descartan el borrado pendiente y la semilla del servidor simulado",
                listOf("sincronizada", "pendiente"),
                filas.map { it.claveCliente }
            )
            assertTrue("quedan sin dueño hasta que ingrese la primera cuenta", filas.all { it.uid == "" })
            assertTrue("deben subirse a Firestore", filas.all { it.sincronizacion == "Pendiente" && it.intentos == 0 })
            assertFalse(filas.any { it.enRemoto })
            assertEquals("conserva el id local", 1L, filas.first().id)

            assertEquals(2, dao.adoptarHuerfanas("uid-ana"))
            assertEquals(2, dao.obtenerPendientes("uid-ana").size)
        } finally {
            base.close()
        }
    }

    @Test
    fun la_clave_sigue_siendo_unica_despues_de_migrar() = runBlocking {
        crearBaseVersion1()
        val base = Room.databaseBuilder(contexto, RecordatoriosDatabase::class.java, NOMBRE)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val dao = base.recordatorioDao()
            val repetida = dao.observarTodos("").first().first().copy(id = 0)

            assertTrue(runCatching { dao.insertar(repetida) }.isFailure)
        } finally {
            base.close()
        }
    }

    private companion object {
        const val NOMBRE = "prueba-migracion.db"
    }
}

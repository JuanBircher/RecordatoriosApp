package com.juanbircher.recordatorios.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas del DAO contra Room y SQLite reales, en el emulador o dispositivo. */
@RunWith(AndroidJUnit4::class)
class RecordatorioDaoTest {

    private lateinit var contexto: Context
    private lateinit var base: RecordatoriosDatabase
    private lateinit var dao: RecordatorioDao

    @Before
    fun abrir() {
        contexto = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexto, RecordatoriosDatabase::class.java).build()
        dao = base.recordatorioDao()
    }

    @After
    fun cerrar() = base.close()

    private fun entidad(
        clave: String,
        titulo: String = "Recordatorio $clave",
        estado: String = RecordatorioEntity.PENDIENTE,
        sincronizacion: String = "Pendiente",
        uid: String = ANA
    ) = RecordatorioEntity(
        uid = uid,
        claveCliente = clave,
        titulo = titulo,
        categoriaId = "estudio",
        categoriaNombre = "Estudio",
        prioridad = 2,
        estadoTipo = estado,
        completadoEl = if (estado == RecordatorioEntity.COMPLETADO) "2026-11-09" else null,
        creadoEl = "2026-11-09",
        sincronizacion = sincronizacion
    )

    @Test
    fun insertar_asigna_un_id_estable_y_se_puede_consultar() = runBlocking {
        val id = dao.insertar(entidad("a"))

        assertTrue(id > 0)
        assertEquals("Recordatorio a", dao.obtenerPorId(ANA, id)?.titulo)
        assertEquals("Recordatorio a", dao.observarPorId(ANA, id).first()?.titulo)
    }

    @Test
    fun el_listado_completo_se_emite_y_se_actualiza_solo() = runBlocking {
        dao.insertar(entidad("a"))
        assertEquals(1, dao.observarTodos(ANA).first().size)

        dao.insertar(entidad("b"))

        assertEquals(listOf("a", "b"), dao.observarTodos(ANA).first().map { it.claveCliente })
    }

    @Test
    fun actualizar_modifica_la_fila_y_conserva_el_id() = runBlocking {
        val id = dao.insertar(entidad("a"))

        dao.actualizar(dao.obtenerPorId(ANA, id)!!.copy(titulo = "Título nuevo"))

        assertEquals("Título nuevo", dao.obtenerPorId(ANA, id)?.titulo)
        assertEquals(1, dao.observarTodos(ANA).first().size)
    }

    @Test
    fun eliminar_quita_la_fila() = runBlocking {
        val id = dao.insertar(entidad("a"))

        dao.eliminar(id)

        assertNull(dao.obtenerPorId(ANA, id))
        assertTrue(dao.observarTodos(ANA).first().isEmpty())
    }

    @Test
    fun el_filtro_por_estado_deja_afuera_los_completados() = runBlocking {
        dao.insertar(entidad("pendiente"))
        dao.insertar(entidad("hecho", estado = RecordatorioEntity.COMPLETADO))
        dao.insertar(entidad("cancelado", estado = RecordatorioEntity.CANCELADO))

        val sinCompletados = dao.observarSinCompletados(ANA).first().map { it.claveCliente }

        assertEquals(listOf("pendiente", "cancelado"), sinCompletados)
        assertEquals(3, dao.observarTodos(ANA).first().size)
    }

    @Test
    fun los_marcados_para_borrar_no_aparecen_en_las_consultas_de_la_interfaz() = runBlocking {
        val id = dao.insertar(entidad("a"))
        dao.actualizar(dao.obtenerPorId(ANA, id)!!.copy(eliminado = true))

        assertTrue(dao.observarTodos(ANA).first().isEmpty())
        assertNull(dao.obtenerPorId(ANA, id))
        assertNull(dao.observarPorId(ANA, id).first())
        assertEquals("pero la fila sigue para poder avisar al servicio", id, dao.obtenerPorClave(ANA, "a")?.id)
    }

    @Test
    fun la_clave_del_cliente_es_unica_y_no_permite_duplicar() = runBlocking {
        dao.insertar(entidad("a"))

        val resultado = runCatching { dao.insertar(entidad("a", titulo = "Duplicado")) }

        assertTrue(resultado.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(1, dao.observarTodos(ANA).first().size)
    }

    @Test
    fun las_consultas_de_sincronizacion_cuentan_y_reencolan() = runBlocking {
        dao.insertar(entidad("p1"))
        dao.insertar(entidad("p2"))
        dao.insertar(entidad("e1", sincronizacion = "Error"))
        dao.insertar(entidad("s1", sincronizacion = "Sincronizado"))

        assertEquals(listOf("p1", "p2"), dao.obtenerPendientes(ANA).map { it.claveCliente })
        assertEquals(2, dao.contarPorSincronizacion(ANA, "Pendiente").first())
        assertEquals(1, dao.contarPorSincronizacion(ANA, "Error").first())

        assertEquals(1, dao.reencolarErrores(ANA))

        assertEquals(3, dao.contarPorSincronizacion(ANA, "Pendiente").first())
        assertEquals(0, dao.contarPorSincronizacion(ANA, "Error").first())
    }

    @Test
    fun los_datos_sobreviven_a_cerrar_y_volver_a_abrir_la_base() = runBlocking {
        val nombre = "prueba-persistencia.db"
        contexto.deleteDatabase(nombre)
        val primera = Room.databaseBuilder(contexto, RecordatoriosDatabase::class.java, nombre).build()
        primera.recordatorioDao().insertar(entidad("a", titulo = "Sigue después de cerrar"))
        primera.close()

        val segunda = Room.databaseBuilder(contexto, RecordatoriosDatabase::class.java, nombre).build()
        try {
            assertEquals("Sigue después de cerrar", segunda.recordatorioDao().observarTodos(ANA).first().single().titulo)
        } finally {
            segunda.close()
            contexto.deleteDatabase(nombre)
        }
    }

    // ---- datos por cuenta ----------------------------------------------------------------------

    @Test
    fun cada_cuenta_ve_solo_sus_filas_aunque_compartan_la_base() = runBlocking {
        val deAna = dao.insertar(entidad("de-ana", uid = ANA))
        dao.insertar(entidad("de-beto", uid = BETO))

        assertEquals(listOf("de-ana"), dao.observarTodos(ANA).first().map { it.claveCliente })
        assertEquals(listOf("de-beto"), dao.observarTodos(BETO).first().map { it.claveCliente })
        assertNull("Beto no abre por id una fila de Ana", dao.obtenerPorId(BETO, deAna))
        assertNull(dao.obtenerPorClave(BETO, "de-ana"))
        assertEquals(listOf("de-beto"), dao.obtenerPendientes(BETO).map { it.claveCliente })
        assertEquals(1, dao.contarPorSincronizacion(ANA, "Pendiente").first())
    }

    @Test
    fun las_filas_sin_duenio_se_adoptan_una_sola_vez() = runBlocking {
        dao.insertar(entidad("vieja", uid = ""))

        assertEquals(1, dao.adoptarHuerfanas(ANA))
        assertEquals(0, dao.adoptarHuerfanas(BETO))

        assertEquals(listOf("vieja"), dao.observarTodos(ANA).first().map { it.claveCliente })
        assertTrue(dao.observarTodos(BETO).first().isEmpty())
    }

    @Test
    fun solo_las_confirmadas_en_el_servicio_son_candidatas_a_borrado_remoto() = runBlocking {
        dao.insertar(entidad("confirmada", sincronizacion = "Sincronizado").copy(enRemoto = true))
        dao.insertar(entidad("pendiente").copy(enRemoto = true))
        dao.insertar(entidad("nunca-subio", sincronizacion = "Sincronizado"))

        assertEquals(listOf("confirmada"), dao.obtenerSincronizadosEnRemoto(ANA).map { it.claveCliente })
    }

    private companion object {
        const val ANA = "uid-ana"
        const val BETO = "uid-beto"
    }
}

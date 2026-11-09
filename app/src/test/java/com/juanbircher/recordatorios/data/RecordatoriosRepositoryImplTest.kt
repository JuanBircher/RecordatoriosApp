package com.juanbircher.recordatorios.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.juanbircher.recordatorios.data.preferences.PreferenciasDataSource
import com.juanbircher.recordatorios.data.remote.ResultadoRemoto
import com.juanbircher.recordatorios.data.sync.ResultadoSincronizacion
import com.juanbircher.recordatorios.data.sync.SincronizadorRecordatorios
import com.juanbircher.recordatorios.domain.EstadoRecordatorio
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RecordatoriosRepositoryImplTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val alcanceDataStore = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao = DaoFalso()
    private val remoto = RemotoFalso()
    private val preferencias = PreferenciasDataSource(
        PreferenceDataStoreFactory.create(scope = alcanceDataStore) { File(carpeta.root, "p.preferences_pb") }
    )
    private var contador = 0

    @After
    fun cerrar() = alcanceDataStore.cancel()

    private fun TestScope.repositorio() = RecordatoriosRepositoryImpl(
        dao = dao,
        preferenciasFuente = preferencias,
        sincronizador = SincronizadorRecordatorios(dao, remoto, DatosSimulados.categorias),
        categorias = DatosSimulados.categorias,
        alcance = this, // no backgroundScope: advanceUntilIdle() no espera tareas de fondo
        nuevaClave = { "clave-${++contador}" }
    )

    private val hoy = LocalDate.of(2026, 11, 9)

    private fun nuevo(titulo: String = "Entregar la Actividad 3", prioridad: Prioridad = Prioridad.Alta) = Recordatorio(
        id = 0,
        titulo = titulo,
        categoria = DatosSimulados.estudio,
        prioridad = prioridad,
        creadoEl = hoy
    )

    // ---- local-first ---------------------------------------------------------------------------

    @Test
    fun `sin conexion lo creado se guarda en Room como pendiente y se ve de inmediato`() = runTest {
        remoto.fallo = ResultadoRemoto.SinConexion
        val repositorio = repositorio()

        val guardado = repositorio.guardarRecordatorio(nuevo())
        advanceUntilIdle()

        val visibles = repositorio.observarRecordatorios().first()
        assertEquals(listOf(guardado.id), visibles.map { it.id })
        assertEquals(EstadoSincronizacion.Pendiente, visibles.single().sincronizacion)
        assertTrue(remoto.servidor.isEmpty())
    }

    @Test
    fun `al volver la conexion el pendiente se envia sin crear un segundo registro`() = runTest {
        remoto.fallo = ResultadoRemoto.SinConexion
        val repositorio = repositorio()
        repositorio.guardarRecordatorio(nuevo())
        advanceUntilIdle()

        remoto.fallo = null
        val resultado = repositorio.sincronizar()

        assertTrue(resultado is ResultadoSincronizacion.Completa)
        val visibles = repositorio.observarRecordatorios().first()
        assertEquals(1, visibles.size)
        assertEquals(EstadoSincronizacion.Sincronizado, visibles.single().sincronizacion)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun `con conexion guardar tambien dispara la sincronizacion en segundo plano`() = runTest {
        val repositorio = repositorio()

        repositorio.guardarRecordatorio(nuevo())
        advanceUntilIdle()

        assertEquals(1, remoto.servidor.size)
        assertEquals(EstadoSincronizacion.Sincronizado, repositorio.observarRecordatorios().first().single().sincronizacion)
    }

    @Test
    fun `las lecturas vienen siempre de Room aunque el servicio falle`() = runTest {
        remoto.fallo = ResultadoRemoto.ErrorServidor(500)
        val repositorio = repositorio()
        repositorio.guardarRecordatorio(nuevo("Primero"))
        repositorio.guardarRecordatorio(nuevo("Segundo"))
        advanceUntilIdle()

        assertEquals(2, repositorio.observarRecordatorios().first().size)
    }

    // ---- modificar y eliminar ------------------------------------------------------------------

    @Test
    fun `actualizar guarda el cambio, lo deja pendiente y conserva el id`() = runTest {
        val repositorio = repositorio()
        val creado = repositorio.guardarRecordatorio(nuevo())
        advanceUntilIdle()
        remoto.fallo = ResultadoRemoto.Timeout

        repositorio.actualizarRecordatorio(creado.copy(titulo = "Título corregido", estado = EstadoRecordatorio.Completado(hoy)))
        advanceUntilIdle()

        val leido = repositorio.observarRecordatorio(creado.id).first()!!
        assertEquals("Título corregido", leido.titulo)
        assertEquals(EstadoRecordatorio.Completado(hoy), leido.estado)
        assertEquals(EstadoSincronizacion.Pendiente, leido.sincronizacion)
    }

    @Test
    fun `actualizar uno que ya no existe no hace nada ni lo vuelve a crear`() = runTest {
        val repositorio = repositorio()

        repositorio.actualizarRecordatorio(nuevo().copy(id = 55))
        advanceUntilIdle()

        assertTrue(repositorio.observarRecordatorios().first().isEmpty())
        assertEquals(0, remoto.llamadasCrear)
    }

    @Test
    fun `eliminar lo oculta de inmediato y despues lo borra tambien del servicio`() = runTest {
        val repositorio = repositorio()
        val creado = repositorio.guardarRecordatorio(nuevo())
        advanceUntilIdle()
        remoto.fallo = ResultadoRemoto.SinConexion

        repositorio.eliminarRecordatorio(creado.id)
        advanceUntilIdle()

        assertTrue(repositorio.observarRecordatorios().first().isEmpty())
        assertNull(repositorio.observarRecordatorio(creado.id).first())
        assertEquals("el borrado sigue pendiente", 1, remoto.servidor.size)

        remoto.fallo = null
        repositorio.sincronizar()
        assertTrue(remoto.servidor.isEmpty())
        assertTrue(dao.filas.value.isEmpty())
    }

    @Test
    fun `reintentar fallidos vuelve a poner en cola lo que quedo en error`() = runTest {
        remoto.fallo = ResultadoRemoto.SolicitudInvalida(400)
        val repositorio = repositorio()
        repositorio.guardarRecordatorio(nuevo())
        advanceUntilIdle()
        assertEquals(1, repositorio.observarResumenSincronizacion().first().conError)

        remoto.fallo = null
        repositorio.reintentarFallidos()

        assertEquals(ResumenSincronizacion(0, 0), repositorio.observarResumenSincronizacion().first())
        assertEquals(1, remoto.servidor.size)
    }

    // ---- preferencias coordinadas con Room -----------------------------------------------------

    @Test
    fun `ocultar completados deja afuera los completados`() = runTest {
        val repositorio = repositorio()
        repositorio.guardarRecordatorio(nuevo("Pendiente uno"))
        repositorio.guardarRecordatorio(nuevo("Ya completado").copy(estado = EstadoRecordatorio.Completado(hoy)))
        advanceUntilIdle()
        assertEquals(2, repositorio.observarRecordatorios().first().size)

        repositorio.actualizarMostrarCompletados(false)

        assertEquals(listOf("Pendiente uno"), repositorio.observarRecordatorios().first().map { it.titulo })
    }

    @Test
    fun `el orden elegido se aplica al listado`() = runTest {
        val repositorio = repositorio()
        repositorio.guardarRecordatorio(nuevo("Baja", Prioridad.Baja).copy(creadoEl = hoy.minusDays(1)))
        repositorio.guardarRecordatorio(nuevo("Alta", Prioridad.Alta).copy(creadoEl = hoy.minusDays(5)))
        advanceUntilIdle()

        repositorio.actualizarOrden(OrdenListado.Prioridad)
        assertEquals(listOf("Alta", "Baja"), repositorio.observarRecordatorios().first().map { it.titulo })

        repositorio.actualizarOrden(OrdenListado.MasRecientes)
        assertEquals(listOf("Baja", "Alta"), repositorio.observarRecordatorios().first().map { it.titulo })
    }
}

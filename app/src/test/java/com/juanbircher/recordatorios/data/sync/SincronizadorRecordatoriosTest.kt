package com.juanbircher.recordatorios.data.sync

import com.juanbircher.recordatorios.data.DaoFalso
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.RemotoFalso
import com.juanbircher.recordatorios.data.aDto
import com.juanbircher.recordatorios.data.dtoDePrueba
import com.juanbircher.recordatorios.data.entidadDePrueba
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.data.remote.ResultadoRemoto
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SincronizadorRecordatoriosTest {

    private val dao = DaoFalso()
    private val remoto = RemotoFalso()
    private val sincronizador = SincronizadorRecordatorios(dao, remoto, DatosSimulados.categorias)

    private suspend fun fila(clave: String) = dao.obtenerPorClave(clave)!!

    // ---- envío de pendientes ----------------------------------------------------------------

    @Test
    fun `un recordatorio pendiente se crea en el servicio y queda sincronizado con su id remoto`() = runTest {
        dao.insertar(entidadDePrueba("a"))

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Completa(enviados = 1, recibidos = 0), resultado)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
        assertEquals(100L, fila("a").idRemoto)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun `un cambio sobre un recordatorio ya creado se envia como actualizacion, no como alta`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        sincronizador.sincronizar()
        dao.actualizar(fila("a").copy(titulo = "Título corregido", sincronizacion = EstadoSincronizacion.Pendiente.name))

        sincronizador.sincronizar()

        assertEquals(1, remoto.llamadasCrear)
        assertEquals(1, remoto.llamadasActualizar)
        assertEquals("Título corregido", remoto.servidor.single().titulo)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
    }

    @Test
    fun `si se actualiza un recordatorio que el servicio ya no tiene, se vuelve a crear sin duplicar`() = runTest {
        dao.insertar(entidadDePrueba("a", sincronizacion = EstadoSincronizacion.Pendiente, idRemoto = 77))

        sincronizador.sincronizar()

        assertEquals(1, remoto.servidor.size)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
        assertEquals(remoto.servidor.single().id, fila("a").idRemoto)
    }

    // ---- fallos -----------------------------------------------------------------------------

    @Test
    fun `sin conexion el recordatorio sigue pendiente y no se pierde`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.SinConexion

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.SinConexion), resultado)
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("a").sincronizacion)
        assertEquals(1, fila("a").intentos)
        assertEquals(EstadoSync.Fallo(CausaFallo.SinConexion), sincronizador.estado.value)
    }

    @Test
    fun `reintentar despues de un fallo transitorio no duplica el registro`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.Timeout
        sincronizador.sincronizar()

        remoto.fallo = null
        val resultado = sincronizador.sincronizar()

        assertTrue(resultado is ResultadoSincronizacion.Completa)
        assertEquals(1, remoto.servidor.size)
        assertEquals(EstadoSync.Inactivo, sincronizador.estado.value)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
    }

    @Test
    fun `una creacion confirmada cuyo resultado se perdio se reenvia con la misma clave y no duplica`() = runTest {
        // El servicio recibió la creación, pero la app no llegó a anotar la respuesta (cierre, corte...).
        dao.insertar(entidadDePrueba("a"))
        remoto.crear("a", fila("a").aDto())

        sincronizador.sincronizar()

        assertEquals(1, remoto.servidor.size)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
    }

    @Test
    fun `una solicitud invalida deja el recordatorio en error y no se reintenta sola`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.SolicitudInvalida(400)

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.SolicitudInvalida), resultado)
        assertEquals(EstadoSincronizacion.Error.name, fila("a").sincronizacion)

        remoto.fallo = null
        sincronizador.sincronizar()
        assertEquals("sigue en error hasta que la persona pida reintentar", 0, remoto.servidor.size)
        assertEquals(1, remoto.llamadasCrear)

        dao.reencolarErrores()
        sincronizador.sincronizar()
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun `tras agotar los intentos por fallos transitorios el recordatorio pasa a error`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.ErrorServidor(500)

        repeat(3) { sincronizador.sincronizar() }

        assertEquals(EstadoSincronizacion.Error.name, fila("a").sincronizacion)
    }

    @Test
    fun `un fallo transitorio corta el envio y no se insiste con el resto de la cola`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        dao.insertar(entidadDePrueba("b"))
        remoto.fallo = ResultadoRemoto.SinConexion

        sincronizador.sincronizar()

        assertEquals(1, remoto.llamadasCrear)
    }

    @Test
    fun `sin conexion no se intenta traer datos del servicio`() = runTest {
        remoto.fallo = ResultadoRemoto.SinConexion

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.SinConexion), resultado)
    }

    // ---- duplicaciones ----------------------------------------------------------------------

    @Test
    fun `varios eventos simultaneos de sincronizacion envian la operacion una sola vez`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.retrasoMs = 500

        val resultados = (1..5).map { async { sincronizador.sincronizar() } }.awaitAll()

        assertEquals(1, remoto.llamadasCrear)
        assertEquals(1, remoto.servidor.size)
        assertEquals(4, resultados.count { it == ResultadoSincronizacion.YaEnCurso })
    }

    @Test
    fun `un cambio hecho mientras se enviaba no se pierde ni se marca como sincronizado`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.retrasoMs = 500

        val corrida = async { sincronizador.sincronizar() }
        testScheduler.advanceTimeBy(100)
        dao.actualizar(fila("a").copy(titulo = "Editado durante el envío"))
        corrida.await()

        assertEquals(EstadoSincronizacion.Pendiente.name, fila("a").sincronizacion)
        assertEquals(100L, fila("a").idRemoto)
    }

    // ---- borrado ----------------------------------------------------------------------------

    @Test
    fun `borrar un recordatorio que nunca llego al servicio solo lo quita de la base`() = runTest {
        dao.insertar(entidadDePrueba("a", eliminado = true))

        sincronizador.sincronizar()

        assertNull(dao.obtenerPorClave("a"))
        assertEquals(0, remoto.llamadasEliminar)
    }

    @Test
    fun `borrar un recordatorio sincronizado lo elimina en el servicio y despues de la base`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        sincronizador.sincronizar()
        dao.actualizar(fila("a").copy(eliminado = true, sincronizacion = EstadoSincronizacion.Pendiente.name))

        sincronizador.sincronizar()

        assertEquals(0, remoto.servidor.size)
        assertNull(dao.obtenerPorClave("a"))
    }

    @Test
    fun `si el borrado falla, la marca de borrado se conserva para reintentar`() = runTest {
        dao.insertar(entidadDePrueba("a", sincronizacion = EstadoSincronizacion.Pendiente, idRemoto = 100, eliminado = true))
        remoto.servidor += dtoDePrueba(id = 100, clave = "a")
        remoto.fallo = ResultadoRemoto.SinConexion

        sincronizador.sincronizar()

        assertTrue(fila("a").eliminado)
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("a").sincronizacion)
    }

    // ---- descarga ---------------------------------------------------------------------------

    @Test
    fun `los recordatorios del servicio se transforman y se guardan en la base local`() = runTest {
        remoto.servidor += dtoDePrueba(id = 500, clave = "remota-1")

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Completa(enviados = 0, recibidos = 1), resultado)
        val guardado = fila("remota-1")
        assertEquals("Recordatorio del servidor", guardado.titulo)
        assertEquals(500L, guardado.idRemoto)
        assertEquals(EstadoSincronizacion.Sincronizado.name, guardado.sincronizacion)
    }

    @Test
    fun `sincronizar dos veces seguidas no duplica lo descargado`() = runTest {
        remoto.servidor += dtoDePrueba(id = 500, clave = "remota-1")

        sincronizador.sincronizar()
        sincronizador.sincronizar()

        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun `un dato invalido del servicio se descarta y no llega a la base`() = runTest {
        remoto.servidor += dtoDePrueba(id = 501, clave = "sin-titulo", titulo = "ab")
        remoto.servidor += dtoDePrueba(id = 502, clave = "categoria-rara", categoriaId = "otra")
        remoto.servidor += dtoDePrueba(id = 503, clave = "valida")

        sincronizador.sincronizar()

        assertEquals(listOf("valida"), dao.filas.value.map { it.claveCliente })
    }

    @Test
    fun `la descarga no pisa un cambio local pendiente`() = runTest {
        dao.insertar(entidadDePrueba("a", titulo = "Versión local", sincronizacion = EstadoSincronizacion.Sincronizado, idRemoto = 100))
        remoto.servidor += dtoDePrueba(id = 100, clave = "a", titulo = "Versión del servicio")
        dao.actualizar(fila("a").copy(titulo = "Versión local editada", sincronizacion = EstadoSincronizacion.Pendiente.name))
        remoto.fallo = null

        sincronizador.sincronizar()

        assertEquals("el cambio local gana y se envía", "Versión local editada", remoto.servidor.single().titulo)
    }

    @Test
    fun `la descarga actualiza un recordatorio sincronizado que cambio en el servicio`() = runTest {
        dao.insertar(entidadDePrueba("a", titulo = "Título viejo", sincronizacion = EstadoSincronizacion.Sincronizado, idRemoto = 100))
        remoto.servidor += dtoDePrueba(id = 100, clave = "a", titulo = "Título nuevo del servicio")

        sincronizador.sincronizar()

        assertEquals("Título nuevo del servicio", fila("a").titulo)
        assertEquals("conserva el id local", 1L, fila("a").id)
    }

    @Test
    fun `una respuesta ilegible al descargar es un fallo recuperable`() = runTest {
        remoto.resultadoListar = ResultadoRemoto.RespuestaInvalida

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.RespuestaInvalida), resultado)
    }
}

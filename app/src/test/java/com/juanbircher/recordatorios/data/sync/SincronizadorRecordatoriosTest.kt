package com.juanbircher.recordatorios.data.sync

import com.juanbircher.recordatorios.data.ANA
import com.juanbircher.recordatorios.data.BETO
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SincronizadorRecordatoriosTest {

    private val dao = DaoFalso()
    private val remoto = RemotoFalso()

    /** Sesión activa que ve el sincronizador; `null` = nadie ingresó. */
    private var sesion: String? = ANA
    private val sincronizador = SincronizadorRecordatorios(dao, remoto, DatosSimulados.categorias, uidActual = { sesion })

    private suspend fun fila(clave: String, uid: String = ANA) = dao.obtenerPorClave(uid, clave)!!

    // ---- envío de pendientes ----------------------------------------------------------------

    @Test
    fun `un recordatorio pendiente se guarda en la coleccion de su cuenta y queda sincronizado`() = runTest {
        dao.insertar(entidadDePrueba("a"))

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Completa(enviados = 1, recibidos = 0), resultado)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
        assertTrue(fila("a").enRemoto)
        assertEquals(setOf("a"), remoto.de(ANA).keys)
    }

    @Test
    fun `un cambio sobre un recordatorio ya enviado reemplaza el mismo documento`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        sincronizador.sincronizar()
        dao.actualizar(fila("a").copy(titulo = "Título corregido", sincronizacion = EstadoSincronizacion.Pendiente.name))

        sincronizador.sincronizar()

        assertEquals(2, remoto.llamadasGuardar)
        assertEquals("Título corregido", remoto.de(ANA).values.single().titulo)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
    }

    @Test
    fun `solo se envian los pendientes de la cuenta activa`() = runTest {
        dao.insertar(entidadDePrueba("de-ana", uid = ANA))
        dao.insertar(entidadDePrueba("de-beto", uid = BETO))

        sincronizador.sincronizar()

        assertEquals(setOf("de-ana"), remoto.de(ANA).keys)
        assertTrue("lo de otra cuenta no viaja con esta identidad", remoto.de(BETO).isEmpty())
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("de-beto", BETO).sincronizacion)
    }

    // ---- sesión -----------------------------------------------------------------------------

    @Test
    fun `sin sesion no se llama al servicio y los pendientes se conservan`() = runTest {
        sesion = null
        dao.insertar(entidadDePrueba("a"))

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.SinSesion, resultado)
        assertEquals(0, remoto.llamadasGuardar + remoto.llamadasListar)
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("a").sincronizacion)
    }

    @Test
    fun `NoAutorizado corta la corrida y deja el recordatorio pendiente sin gastar intentos`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        dao.insertar(entidadDePrueba("b"))
        remoto.denegados += ANA

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.NoAutorizado), resultado)
        assertEquals(1, remoto.llamadasGuardar)
        assertEquals(0, remoto.llamadasListar)
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("a").sincronizacion)
        assertEquals(0, fila("a").intentos)
        assertEquals(EstadoSync.Fallo(CausaFallo.NoAutorizado), sincronizador.estado.value)
    }

    @Test
    fun `si la sesion cambia durante la corrida no se sigue enviando con la identidad anterior`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        dao.insertar(entidadDePrueba("b"))
        remoto.retrasoMs = 500

        val corrida = async { sincronizador.sincronizar() }
        testScheduler.advanceTimeBy(100)
        sesion = BETO
        val resultado = corrida.await()

        assertEquals(ResultadoSincronizacion.SinSesion, resultado)
        assertEquals("solo el envío que ya estaba en curso", 1, remoto.llamadasGuardar)
        assertEquals(0, remoto.llamadasListar)
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("b").sincronizacion)
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
        assertEquals(1, remoto.de(ANA).size)
        assertEquals(EstadoSync.Inactivo, sincronizador.estado.value)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
    }

    @Test
    fun `una escritura que llego al servicio pero cuya confirmacion se perdio se reenvia sin duplicar`() = runTest {
        // El servicio recibió el documento, pero la app no llegó a anotar la respuesta (cierre, corte...).
        dao.insertar(entidadDePrueba("a"))
        remoto.guardar(ANA, "a", fila("a").aDto())

        sincronizador.sincronizar()

        assertEquals(1, remoto.de(ANA).size)
        assertEquals(EstadoSincronizacion.Sincronizado.name, fila("a").sincronizacion)
    }

    @Test
    fun `una solicitud invalida deja el recordatorio en error y no se reintenta sola`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.SolicitudInvalida("INVALID_ARGUMENT")

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.SolicitudInvalida), resultado)
        assertEquals(EstadoSincronizacion.Error.name, fila("a").sincronizacion)

        remoto.fallo = null
        sincronizador.sincronizar()
        assertTrue("sigue en error hasta que la persona pida reintentar", remoto.de(ANA).isEmpty())
        assertEquals(1, remoto.llamadasGuardar)

        dao.reencolarErrores(ANA)
        sincronizador.sincronizar()
        assertEquals(1, remoto.de(ANA).size)
    }

    @Test
    fun `tras agotar los intentos por fallos transitorios el recordatorio pasa a error`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.ErrorServidor("UNAVAILABLE")

        repeat(3) { sincronizador.sincronizar() }

        assertEquals(EstadoSincronizacion.Error.name, fila("a").sincronizacion)
    }

    @Test
    fun `un fallo transitorio corta el envio y no se insiste con el resto de la cola`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        dao.insertar(entidadDePrueba("b"))
        remoto.fallo = ResultadoRemoto.SinConexion

        sincronizador.sincronizar()

        assertEquals(1, remoto.llamadasGuardar)
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

        assertEquals(1, remoto.llamadasGuardar)
        assertEquals(1, remoto.de(ANA).size)
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
        assertTrue("ya existe en el servicio: un borrado posterior necesita red", fila("a").enRemoto)
    }

    // ---- borrado ----------------------------------------------------------------------------

    @Test
    fun `borrar un recordatorio que nunca llego al servicio solo lo quita de la base`() = runTest {
        dao.insertar(entidadDePrueba("a", eliminado = true))

        sincronizador.sincronizar()

        assertNull(dao.obtenerPorClave(ANA, "a"))
        assertEquals(0, remoto.llamadasEliminar)
    }

    @Test
    fun `borrar un recordatorio sincronizado lo elimina en el servicio y despues de la base`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        sincronizador.sincronizar()
        dao.actualizar(fila("a").copy(eliminado = true, sincronizacion = EstadoSincronizacion.Pendiente.name))

        sincronizador.sincronizar()

        assertTrue(remoto.de(ANA).isEmpty())
        assertNull(dao.obtenerPorClave(ANA, "a"))
    }

    @Test
    fun `si el borrado falla, la marca de borrado se conserva para reintentar`() = runTest {
        dao.insertar(entidadDePrueba("a", enRemoto = true, eliminado = true))
        remoto.agregar(dtoDePrueba(clave = "a"))
        remoto.fallo = ResultadoRemoto.SinConexion

        sincronizador.sincronizar()

        assertTrue(fila("a").eliminado)
        assertEquals(EstadoSincronizacion.Pendiente.name, fila("a").sincronizacion)
    }

    // ---- descarga ---------------------------------------------------------------------------

    @Test
    fun `lo que la cuenta tiene en el servicio se transforma y se guarda en Room a su nombre`() = runTest {
        remoto.agregar(dtoDePrueba(clave = "remota-1"))

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Completa(enviados = 0, recibidos = 1), resultado)
        val guardado = fila("remota-1")
        assertEquals("Recordatorio del servidor", guardado.titulo)
        assertEquals(ANA, guardado.uid)
        assertTrue(guardado.enRemoto)
        assertEquals(EstadoSincronizacion.Sincronizado.name, guardado.sincronizacion)
    }

    @Test
    fun `solo se descarga la coleccion de la cuenta activa`() = runTest {
        remoto.agregar(dtoDePrueba(clave = "de-beto"), uid = BETO)

        sincronizador.sincronizar()

        assertTrue(dao.filas.value.isEmpty())
    }

    @Test
    fun `sincronizar dos veces seguidas no duplica lo descargado`() = runTest {
        remoto.agregar(dtoDePrueba(clave = "remota-1"))

        sincronizador.sincronizar()
        sincronizador.sincronizar()

        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun `un dato invalido del servicio se descarta y no llega a la base`() = runTest {
        remoto.agregar(dtoDePrueba(clave = "sin-titulo", titulo = "ab"))
        remoto.agregar(dtoDePrueba(clave = "categoria-rara", categoriaId = "otra"))
        remoto.agregar(dtoDePrueba(clave = "valida"))

        sincronizador.sincronizar()

        assertEquals(listOf("valida"), dao.filas.value.map { it.claveCliente })
    }

    @Test
    fun `la descarga no pisa un cambio local pendiente`() = runTest {
        dao.insertar(entidadDePrueba("a", titulo = "Versión local editada", enRemoto = true))
        remoto.agregar(dtoDePrueba(clave = "a", titulo = "Versión del servicio"))

        sincronizador.sincronizar()

        assertEquals("el cambio local gana y se envía", "Versión local editada", remoto.de(ANA)["a"]!!.titulo)
    }

    @Test
    fun `la descarga actualiza un recordatorio sincronizado que cambio en otro dispositivo`() = runTest {
        dao.insertar(entidadDePrueba("a", titulo = "Título viejo", sincronizacion = EstadoSincronizacion.Sincronizado, enRemoto = true))
        remoto.agregar(dtoDePrueba(clave = "a", titulo = "Título nuevo del servicio"))

        sincronizador.sincronizar()

        assertEquals("Título nuevo del servicio", fila("a").titulo)
        assertEquals("conserva el id local", 1L, fila("a").id)
    }

    @Test
    fun `lo borrado desde otro dispositivo tambien se quita de este`() = runTest {
        dao.insertar(entidadDePrueba("borrado-afuera", sincronizacion = EstadoSincronizacion.Sincronizado, enRemoto = true))
        dao.insertar(entidadDePrueba("sigue", sincronizacion = EstadoSincronizacion.Sincronizado, enRemoto = true))
        remoto.agregar(fila("sigue").aDto())

        val resultado = sincronizador.sincronizar()

        assertEquals(listOf("sigue"), dao.filas.value.map { it.claveCliente })
        assertEquals(ResultadoSincronizacion.Completa(enviados = 0, recibidos = 1), resultado)
    }

    @Test
    fun `un cambio local pendiente no se borra aunque el servicio ya no lo tenga`() = runTest {
        dao.insertar(entidadDePrueba("editado-aca", enRemoto = true))

        sincronizador.sincronizar()

        assertTrue("gana el cambio local: se vuelve a subir", "editado-aca" in remoto.de(ANA))
        assertFalse(fila("editado-aca").eliminado)
    }

    @Test
    fun `si falla la descarga no se borra nada local`() = runTest {
        dao.insertar(entidadDePrueba("a", sincronizacion = EstadoSincronizacion.Sincronizado, enRemoto = true))
        remoto.resultadoListar = ResultadoRemoto.ErrorServidor("UNAVAILABLE")

        sincronizador.sincronizar()

        assertEquals(listOf("a"), dao.filas.value.map { it.claveCliente })
    }

    @Test
    fun `un documento invalido en el servicio no hace borrar la copia local valida`() = runTest {
        dao.insertar(entidadDePrueba("a", sincronizacion = EstadoSincronizacion.Sincronizado, enRemoto = true))
        remoto.agregar(dtoDePrueba(clave = "a", titulo = null))

        sincronizador.sincronizar()

        assertEquals(listOf("a"), dao.filas.value.map { it.claveCliente })
    }

    @Test
    fun `una respuesta ilegible al descargar es un fallo recuperable`() = runTest {
        remoto.resultadoListar = ResultadoRemoto.RespuestaInvalida

        val resultado = sincronizador.sincronizar()

        assertEquals(ResultadoSincronizacion.Incompleta(CausaFallo.RespuestaInvalida), resultado)
    }

    // ---- hallazgos de la revisión ----------------------------------------------------------

    @Test
    fun `si otra cuenta pide sincronizar durante una corrida, al terminar se atiende la cuenta nueva`() = runTest {
        dao.insertar(entidadDePrueba("de-ana", uid = ANA))
        dao.insertar(entidadDePrueba("de-beto", uid = BETO))
        remoto.retrasoMs = 500

        val corridaDeAna = async { sincronizador.sincronizar() }
        testScheduler.advanceTimeBy(100)
        sesion = BETO
        assertEquals(ResultadoSincronizacion.YaEnCurso, sincronizador.sincronizar())
        corridaDeAna.await()

        assertTrue("el pedido de Beto no se pierde", "de-beto" in remoto.de(BETO))
    }

    @Test
    fun `un envio que vencio por tiempo pudo llegar, asi que borrarlo tambien se pide al servicio`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.Timeout
        sincronizador.sincronizar()
        // El SDK terminó de escribir el documento después de que venciera el plazo.
        remoto.fallo = null
        remoto.agregar(fila("a").aDto().copy(claveCliente = "a"))
        dao.actualizar(fila("a").copy(eliminado = true, sincronizacion = EstadoSincronizacion.Pendiente.name))

        sincronizador.sincronizar()

        assertEquals(1, remoto.llamadasEliminar)
        assertTrue(remoto.de(ANA).isEmpty())
        assertTrue("no resucita en la descarga", dao.filas.value.isEmpty())
    }

    @Test
    fun `al cambiar de cuenta se olvida el estado de la corrida anterior`() = runTest {
        dao.insertar(entidadDePrueba("a"))
        remoto.fallo = ResultadoRemoto.ErrorServidor("UNAVAILABLE")
        sincronizador.sincronizar()
        assertTrue(sincronizador.estado.value is EstadoSync.Fallo)

        sincronizador.reiniciarEstado()

        assertEquals(EstadoSync.Inactivo, sincronizador.estado.value)
    }
}

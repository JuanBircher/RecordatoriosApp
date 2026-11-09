package com.juanbircher.recordatorios.data.remote

import com.juanbircher.recordatorios.data.ConectividadFalsa
import com.juanbircher.recordatorios.data.dtoDePrueba
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Prueba Retrofit + OkHttp + Gson reales contra el servicio simulado, sin tocar la red. */
class RetrofitRemoteDataSourceTest {

    private val servidor = ServidorSimulado(AlmacenEnMemoria())
    private var modo = ModoRed.Normal
    private val conectividad = ConectividadFalsa()

    private val remoto: RecordatoriosRemoteDataSource = run {
        val cliente = OkHttpClient.Builder()
            .addInterceptor(InterceptorServidorSimulado(servidor, { modo }, latenciaMs = 0))
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.recordatorios.test/v1/")
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecordatoriosApi::class.java)
        RetrofitRemoteDataSource(api, conectividad)
    }

    private val nuevo = dtoDePrueba(id = 0, clave = "c-1").copy(id = null, titulo = "Pagar el seguro")

    // ---- camino feliz -------------------------------------------------------------------------

    @Test
    fun `crear devuelve el recordatorio con el id que asigna el servicio`() = runBlocking {
        val resultado = remoto.crear("c-1", nuevo)

        val creado = (resultado as ResultadoRemoto.Exito).valor
        assertEquals(1L, creado.id)
        assertEquals("Pagar el seguro", creado.titulo)
    }

    @Test
    fun `reenviar la misma clave devuelve el mismo registro y no crea otro`() = runBlocking {
        val primero = (remoto.crear("c-1", nuevo) as ResultadoRemoto.Exito).valor
        val segundo = (remoto.crear("c-1", nuevo) as ResultadoRemoto.Exito).valor
        val lista = (remoto.listar() as ResultadoRemoto.Exito).valor

        assertEquals(primero.id, segundo.id)
        assertEquals(1, lista.size)
    }

    @Test
    fun `listar actualizar y eliminar funcionan de punta a punta`() = runBlocking {
        val creado = (remoto.crear("c-1", nuevo) as ResultadoRemoto.Exito).valor

        val actualizado = remoto.actualizar(creado.id!!, creado.copy(titulo = "Pagar el seguro del auto"))
        assertEquals("Pagar el seguro del auto", (actualizado as ResultadoRemoto.Exito).valor.titulo)

        assertEquals(ResultadoRemoto.Exito(Unit), remoto.eliminar(creado.id!!))
        assertTrue((remoto.listar() as ResultadoRemoto.Exito).valor.isEmpty())
    }

    // ---- errores HTTP --------------------------------------------------------------------------

    @Test
    fun `actualizar o eliminar algo que no existe es no encontrado`() = runBlocking {
        assertEquals(ResultadoRemoto.NoEncontrado, remoto.actualizar(99, nuevo))
        assertEquals(ResultadoRemoto.NoEncontrado, remoto.eliminar(99))
    }

    @Test
    fun `un dato que el servicio rechaza es una solicitud invalida y no transitoria`() = runBlocking {
        val resultado = remoto.crear("c-2", nuevo.copy(titulo = "ab"))

        assertEquals(ResultadoRemoto.SolicitudInvalida(400), resultado)
        assertEquals(false, resultado.esTransitorio)
    }

    @Test
    fun `una falla del servidor es transitoria`() = runBlocking {
        modo = ModoRed.ErrorServidor

        val resultado = remoto.listar()

        assertEquals(ResultadoRemoto.ErrorServidor(500), resultado)
        assertTrue(resultado.esTransitorio)
    }

    @Test
    fun `el modo solicitud invalida rechaza lo que se escribe pero deja leer`() = runBlocking {
        modo = ModoRed.SolicitudInvalida

        assertEquals(ResultadoRemoto.SolicitudInvalida(400), remoto.crear("c-3", nuevo))
        assertTrue(remoto.listar() is ResultadoRemoto.Exito)
    }

    // ---- errores de red ------------------------------------------------------------------------

    @Test
    fun `un timeout se distingue de la falta de conexion`() = runBlocking {
        modo = ModoRed.Timeout
        assertEquals(ResultadoRemoto.Timeout, remoto.listar())

        modo = ModoRed.SinConexion
        assertEquals(ResultadoRemoto.SinConexion, remoto.listar())
    }

    @Test
    fun `si el dispositivo no tiene conexion ni se intenta la llamada`() = runBlocking {
        conectividad.estado.value = false

        assertEquals(ResultadoRemoto.SinConexion, remoto.crear("c-4", nuevo))
        assertTrue("el servicio no llegó a recibir nada", (servidor.atender("GET", emptyList(), null, null)).cuerpo == "[]")
    }

    // ---- clasificación pura --------------------------------------------------------------------

    private fun respuesta(codigo: Int, cuerpo: String? = "x"): Response<String> =
        if (codigo in 200..299) Response.success(codigo, cuerpo)
        else Response.error(codigo, "{}".toResponseBody("application/json".toMediaType()))

    @Test
    fun `cada codigo HTTP se interpreta en su categoria`() {
        assertEquals(ResultadoRemoto.Exito("x"), clasificarRespuesta(respuesta(200)))
        assertEquals(ResultadoRemoto.Exito("x"), clasificarRespuesta(respuesta(201)))
        assertEquals(ResultadoRemoto.RespuestaInvalida, clasificarRespuesta(respuesta(200, cuerpo = null)))
        assertEquals(ResultadoRemoto.SolicitudInvalida(400), clasificarRespuesta(respuesta(400)))
        assertEquals(ResultadoRemoto.SolicitudInvalida(422), clasificarRespuesta(respuesta(422)))
        assertEquals(ResultadoRemoto.NoEncontrado, clasificarRespuesta(respuesta(404)))
        assertEquals(ResultadoRemoto.Timeout, clasificarRespuesta(respuesta(408)))
        assertEquals(ResultadoRemoto.ErrorServidor(429), clasificarRespuesta(respuesta(429)))
        assertEquals(ResultadoRemoto.ErrorServidor(503), clasificarRespuesta(respuesta(503)))
    }

    @Test
    fun `cada excepcion de red se interpreta en su categoria`() {
        assertEquals(ResultadoRemoto.Timeout, clasificarExcepcion(SocketTimeoutException()))
        assertEquals(ResultadoRemoto.SinConexion, clasificarExcepcion(UnknownHostException()))
        assertEquals(ResultadoRemoto.SinConexion, clasificarExcepcion(ConnectException()))
        assertEquals(ResultadoRemoto.SinConexion, clasificarExcepcion(IOException("otro")))
    }

    @Test
    fun `una respuesta que no es JSON es una respuesta invalida y no rompe la app`() = runBlocking {
        val clienteRoto = OkHttpClient.Builder().addInterceptor { cadena ->
            okhttp3.Response.Builder().request(cadena.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200)
                .message("OK").body("<html>no soy json</html>".toResponseBody("text/html".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.recordatorios.test/").client(clienteRoto)
            .addConverterFactory(GsonConverterFactory.create()).build().create(RecordatoriosApi::class.java)

        val resultado = RetrofitRemoteDataSource(api, conectividad).listar()

        assertNotNull(resultado)
        assertEquals(ResultadoRemoto.RespuestaInvalida, resultado)
    }
}

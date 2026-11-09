package com.juanbircher.recordatorios.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Cómo responde el servicio simulado; sirve para provocar cada situación de red desde la app. */
enum class ModoRed(val etiqueta: String) {
    Normal("Servicio normal"),
    SinConexion("Sin conexión"),
    Timeout("Timeout"),
    ErrorServidor("Error del servidor (500)"),
    SolicitudInvalida("Rechaza lo enviado (400)")
}

/** Permite elegir el [ModoRed] del servicio simulado. */
interface SimuladorServidor {
    val modo: StateFlow<ModoRed>
    fun fijar(modo: ModoRed)
}

class ControlServidorSimulado : SimuladorServidor {
    private val _modo = MutableStateFlow(ModoRed.Normal)
    override val modo: StateFlow<ModoRed> = _modo.asStateFlow()

    override fun fijar(modo: ModoRed) {
        _modo.value = modo
    }
}

/**
 * Interceptor de OkHttp que responde en nombre del servicio, sin salir a la red. Retrofit y OkHttp se
 * usan exactamente igual que con un servicio real: se arma la misma petición, pasa por el mismo cliente
 * y la respuesta se interpreta con la misma lógica. Solo cambia quién contesta.
 *
 * - `SinConexion` lanza [UnknownHostException] y `Timeout` lanza [SocketTimeoutException], como lo haría OkHttp.
 * - `ErrorServidor` responde 500 a todo; `SolicitudInvalida` responde 400 a lo que escribe (POST, PUT, DELETE).
 * - [latenciaMs] simula el tiempo de respuesta de una red real.
 */
class InterceptorServidorSimulado(
    private val servidor: ServidorSimulado,
    private val modo: () -> ModoRed,
    private val latenciaMs: Long = 600
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()
        esperar()

        val modoActual = modo()
        return when {
            modoActual == ModoRed.SinConexion -> throw UnknownHostException("Servicio simulado sin conexión")
            modoActual == ModoRed.Timeout -> throw SocketTimeoutException("Timeout simulado")
            modoActual == ModoRed.ErrorServidor -> responder(peticion, RespuestaSimulada(500, """{"error":"Error simulado"}"""))
            modoActual == ModoRed.SolicitudInvalida && peticion.method != "GET" ->
                responder(peticion, RespuestaSimulada(400, """{"error":"Solicitud rechazada (simulado)"}"""))

            else -> {
                val segmentos = peticion.url.pathSegments.dropWhile { it != "recordatorios" }.drop(1).filter { it.isNotEmpty() }
                val cuerpo = peticion.body?.let { Buffer().also(it::writeTo).readUtf8() }
                responder(
                    peticion,
                    servidor.atender(peticion.method, segmentos, peticion.header("Idempotency-Key"), cuerpo)
                )
            }
        }
    }

    private fun esperar() {
        if (latenciaMs <= 0) return
        try {
            Thread.sleep(latenciaMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Llamada cancelada")
        }
    }

    private fun responder(peticion: Request, respuesta: RespuestaSimulada): Response =
        Response.Builder()
            .request(peticion)
            .protocol(Protocol.HTTP_1_1)
            .code(respuesta.codigo)
            .message(mensajeHttp(respuesta.codigo))
            .body((respuesta.cuerpo ?: "").toResponseBody("application/json".toMediaType()))
            .build()

    private fun mensajeHttp(codigo: Int) = when (codigo) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "Server Error"
    }
}

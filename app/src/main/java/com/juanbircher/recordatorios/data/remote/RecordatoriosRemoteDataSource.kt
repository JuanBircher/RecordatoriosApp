package com.juanbircher.recordatorios.data.remote

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.juanbircher.recordatorios.data.sync.Conectividad
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.InterruptedIOException
import java.io.IOException

/**
 * Operaciones remotas que necesita la capa de datos. Cada una devuelve un [ResultadoRemoto]: nunca lanza
 * por un problema de red o de HTTP, así quien la usa decide con un `when` exhaustivo.
 */
interface RecordatoriosRemoteDataSource {
    suspend fun listar(): ResultadoRemoto<List<RecordatorioDto>>
    suspend fun crear(claveIdempotencia: String, recordatorio: RecordatorioDto): ResultadoRemoto<RecordatorioDto>
    suspend fun actualizar(id: Long, recordatorio: RecordatorioDto): ResultadoRemoto<RecordatorioDto>
    suspend fun eliminar(id: Long): ResultadoRemoto<Unit>
}

/**
 * Implementación sobre Retrofit. Antes de llamar consulta [conectividad]: sin red no se intenta, y el
 * resultado es [ResultadoRemoto.SinConexion]. Todo lo demás (códigos HTTP, timeouts, cuerpos ilegibles)
 * se resuelve acá, en la capa de datos.
 */
class RetrofitRemoteDataSource(
    private val api: RecordatoriosApi,
    private val conectividad: Conectividad
) : RecordatoriosRemoteDataSource {

    override suspend fun listar() = ejecutar { api.listar() }

    override suspend fun crear(claveIdempotencia: String, recordatorio: RecordatorioDto) =
        ejecutar { api.crear(claveIdempotencia, recordatorio) }

    override suspend fun actualizar(id: Long, recordatorio: RecordatorioDto) =
        ejecutar { api.actualizar(id, recordatorio) }

    override suspend fun eliminar(id: Long) = ejecutar { api.eliminar(id) }

    private suspend fun <T> ejecutar(llamada: suspend () -> Response<T>): ResultadoRemoto<T> {
        if (!conectividad.estaConectado()) return ResultadoRemoto.SinConexion
        return try {
            clasificarRespuesta(llamada())
        } catch (e: CancellationException) {
            throw e
        } catch (e: JsonParseException) {
            ResultadoRemoto.RespuestaInvalida
        } catch (e: MalformedJsonException) {
            // Gson lo lanza como IOException: se distingue antes que un error de red.
            ResultadoRemoto.RespuestaInvalida
        } catch (e: IOException) {
            clasificarExcepcion(e)
        }
    }
}

/** Interpreta el código HTTP de una respuesta. Función pura: se prueba sin red. */
fun <T> clasificarRespuesta(respuesta: Response<T>): ResultadoRemoto<T> {
    val codigo = respuesta.code()
    return when {
        // 204/205 no traen cuerpo: es el éxito normal de una operación sin resultado (DELETE → Response<Unit>).
        respuesta.isSuccessful && (codigo == 204 || codigo == 205) ->
            @Suppress("UNCHECKED_CAST")
            ResultadoRemoto.Exito(Unit as T)

        respuesta.isSuccessful -> respuesta.body()
            ?.let { ResultadoRemoto.Exito(it) }
            ?: ResultadoRemoto.RespuestaInvalida

        codigo == 404 -> ResultadoRemoto.NoEncontrado
        codigo == 408 -> ResultadoRemoto.Timeout
        codigo == 429 || codigo in 500..599 -> ResultadoRemoto.ErrorServidor(codigo)
        codigo in 400..499 -> ResultadoRemoto.SolicitudInvalida(codigo)
        else -> ResultadoRemoto.RespuestaInvalida
    }
}

/** Distingue un timeout de la falta de conexión. Cualquier otro error de E/S se trata como sin conexión. */
fun clasificarExcepcion(excepcion: IOException): ResultadoRemoto<Nothing> = when (excepcion) {
    is InterruptedIOException -> ResultadoRemoto.Timeout // incluye SocketTimeoutException
    else -> ResultadoRemoto.SinConexion
}

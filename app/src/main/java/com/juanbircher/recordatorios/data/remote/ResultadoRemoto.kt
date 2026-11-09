package com.juanbircher.recordatorios.data.remote

/**
 * Resultado de una operación remota, ya interpretado. La capa de datos decide qué hacer con cada caso;
 * las pantallas no conocen códigos HTTP ni excepciones de red.
 *
 * [esTransitorio] indica si esperar y reintentar puede resolverlo (red caída, timeout, falla del
 * servidor) o si el problema está en lo que se envió y no se arregla reintentando.
 */
sealed interface ResultadoRemoto<out T> {
    val esTransitorio: Boolean get() = false

    data class Exito<out T>(val valor: T) : ResultadoRemoto<T>

    /** 4xx salvo 404: el servicio rechazó lo que se envió. */
    data class SolicitudInvalida(val codigo: Int) : ResultadoRemoto<Nothing>

    /** 404: el recurso no existe en el servicio. */
    data object NoEncontrado : ResultadoRemoto<Nothing>

    /** 5xx: falla del servicio. */
    data class ErrorServidor(val codigo: Int) : ResultadoRemoto<Nothing> {
        override val esTransitorio: Boolean get() = true
    }

    /** Respuesta exitosa pero ilegible o sin el contenido esperado. */
    data object RespuestaInvalida : ResultadoRemoto<Nothing>

    data object Timeout : ResultadoRemoto<Nothing> {
        override val esTransitorio: Boolean get() = true
    }

    data object SinConexion : ResultadoRemoto<Nothing> {
        override val esTransitorio: Boolean get() = true
    }
}

/** Por qué una sincronización no pudo completarse. Se muestra a la persona como un aviso recuperable. */
enum class CausaFallo { SinConexion, Timeout, ErrorServidor, SolicitudInvalida, RespuestaInvalida }

/** Traduce un resultado fallido a su causa; `null` si el resultado no es un fallo. */
fun ResultadoRemoto<*>.causaDeFallo(): CausaFallo? = when (this) {
    is ResultadoRemoto.Exito, ResultadoRemoto.NoEncontrado -> null
    is ResultadoRemoto.SolicitudInvalida -> CausaFallo.SolicitudInvalida
    is ResultadoRemoto.ErrorServidor -> CausaFallo.ErrorServidor
    ResultadoRemoto.RespuestaInvalida -> CausaFallo.RespuestaInvalida
    ResultadoRemoto.Timeout -> CausaFallo.Timeout
    ResultadoRemoto.SinConexion -> CausaFallo.SinConexion
}

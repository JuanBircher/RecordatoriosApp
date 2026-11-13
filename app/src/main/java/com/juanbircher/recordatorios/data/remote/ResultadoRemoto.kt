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

    /** El servicio rechazó lo que se envió. [codigo] es el código del proveedor, para el registro. */
    data class SolicitudInvalida(val codigo: String) : ResultadoRemoto<Nothing>

    /**
     * El servicio no reconoce la identidad o sus reglas no permiten la operación. No se arregla esperando
     * ni reintentando con la misma sesión: la operación queda pendiente hasta que haya una sesión válida.
     */
    data object NoAutorizado : ResultadoRemoto<Nothing>

    /** Falla del servicio; [codigo] es el código del proveedor. */
    data class ErrorServidor(val codigo: String) : ResultadoRemoto<Nothing> {
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
enum class CausaFallo { SinConexion, Timeout, ErrorServidor, SolicitudInvalida, RespuestaInvalida, NoAutorizado }

/** Traduce un resultado fallido a su causa; `null` si el resultado no es un fallo. */
fun ResultadoRemoto<*>.causaDeFallo(): CausaFallo? = when (this) {
    is ResultadoRemoto.Exito -> null
    is ResultadoRemoto.SolicitudInvalida -> CausaFallo.SolicitudInvalida
    is ResultadoRemoto.ErrorServidor -> CausaFallo.ErrorServidor
    ResultadoRemoto.RespuestaInvalida -> CausaFallo.RespuestaInvalida
    ResultadoRemoto.Timeout -> CausaFallo.Timeout
    ResultadoRemoto.SinConexion -> CausaFallo.SinConexion
    ResultadoRemoto.NoAutorizado -> CausaFallo.NoAutorizado
}

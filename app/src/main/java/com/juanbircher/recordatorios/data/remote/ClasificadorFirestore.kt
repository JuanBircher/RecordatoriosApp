package com.juanbircher.recordatorios.data.remote

/*
 * Traducción de los errores de Firestore al vocabulario propio de la capa de datos. Ni el sincronizador ni
 * la interfaz conocen las excepciones de Firebase: solo ven un ResultadoRemoto.
 *
 * Se clasifica por el NOMBRE del código (FirebaseFirestoreException.Code.name) y no por el enum: el enum
 * de Firebase usa clases de Android al inicializarse y no se puede cargar en una prueba de la JVM. Así esta
 * función queda pura y se prueba sin emulador.
 */

/** Clasifica un código de error de Firestore por su nombre (por ejemplo, `"PERMISSION_DENIED"`). */
fun clasificarCodigoFirestore(codigo: String): ResultadoRemoto<Nothing> = when (codigo) {
    // Las reglas de seguridad rechazaron la operación o la sesión ya no es válida.
    "PERMISSION_DENIED", "UNAUTHENTICATED" -> ResultadoRemoto.NoAutorizado

    "DEADLINE_EXCEEDED" -> ResultadoRemoto.Timeout

    // Lo enviado no es aceptable: reintentar lo mismo daría el mismo resultado.
    "INVALID_ARGUMENT", "FAILED_PRECONDITION", "OUT_OF_RANGE", "ALREADY_EXISTS", "NOT_FOUND",
    "UNIMPLEMENTED" -> ResultadoRemoto.SolicitudInvalida(codigo)

    // Firestore no informa OK como error; si llegara, la respuesta no es confiable.
    "OK" -> ResultadoRemoto.RespuestaInvalida

    // UNAVAILABLE, RESOURCE_EXHAUSTED, ABORTED, INTERNAL, UNKNOWN, CANCELLED, DATA_LOSS y cualquier código
    // nuevo: falla del servicio o de la conexión con él; esperar y reintentar puede resolverla.
    else -> ResultadoRemoto.ErrorServidor(codigo)
}

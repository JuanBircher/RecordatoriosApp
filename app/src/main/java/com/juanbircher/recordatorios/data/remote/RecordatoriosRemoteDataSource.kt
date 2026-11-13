package com.juanbircher.recordatorios.data.remote

/**
 * Operaciones remotas que necesita la capa de datos, independientes del proveedor (hoy, Cloud Firestore).
 * Cada una devuelve un [ResultadoRemoto]: nunca lanza por un problema de red, de permisos o del servicio,
 * así quien la usa decide con un `when` exhaustivo.
 *
 * Todas reciben el [uid] de la cuenta: los datos de cada persona viven en su propia colección
 * (`users/{uid}/reminders/{clave}`). El servicio vuelve a comprobar con sus reglas que la sesión
 * corresponda a ese uid; pasar otro uid no da acceso, devuelve [ResultadoRemoto.NoAutorizado].
 */
interface RecordatoriosRemoteDataSource {

    /** Todos los recordatorios de la cuenta, leídos del servicio (nunca de una caché local). */
    suspend fun listar(uid: String): ResultadoRemoto<List<RecordatorioDto>>

    /**
     * Crea o reemplaza el documento [clave] de la cuenta. Es idempotente: enviarlo dos veces deja un
     * único documento con el último contenido.
     */
    suspend fun guardar(uid: String, clave: String, recordatorio: RecordatorioDto): ResultadoRemoto<Unit>

    /** Borra el documento [clave]. Borrar uno que ya no existe también es un éxito. */
    suspend fun eliminar(uid: String, clave: String): ResultadoRemoto<Unit>
}

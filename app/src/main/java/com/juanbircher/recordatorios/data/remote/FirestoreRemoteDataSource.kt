package com.juanbircher.recordatorios.data.remote

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.juanbircher.recordatorios.data.sync.Conectividad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fuente remota sobre Cloud Firestore. Cada cuenta tiene su colección en `users/{uid}/reminders`, y cada
 * documento se llama como la `claveCliente` del recordatorio: el mismo id que usa Room.
 *
 * Decisiones:
 * - **Room es la única fuente local.** Firestore se configura con caché en memoria (ver
 *   [com.juanbircher.recordatorios.RecordatoriosApp]) y [listar] lee siempre del servidor
 *   ([Source.SERVER]): una lectura de la caché sin conexión parecería una lista completa y podría
 *   confundirse con borrados hechos en otro dispositivo.
 * - **Las esperas tienen límite.** Sin red no se intenta nada ([Conectividad]). Con red, cada operación
 *   espera como máximo [plazo]: el SDK no completa una escritura hasta que el servidor la confirma, y sin
 *   límite la sincronización quedaría colgada. Si el plazo vence, el SDK puede terminar de enviarla más
 *   tarde: escribe el mismo documento con el mismo contenido, así que no duplica, y el sincronizador marca
 *   ese registro como posiblemente existente para que un borrado posterior también llegue al servicio.
 * - **Ninguna excepción de Firebase sale de acá**: todo se traduce a [ResultadoRemoto].
 * - **`actualizadoEl` lo pone el servidor** (`serverTimestamp`), y las reglas exigen que coincida con la
 *   hora de la petición: la app no puede inventar la fecha de modificación.
 */
class FirestoreRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val conectividad: Conectividad,
    private val plazo: Duration = 10.seconds
) : RecordatoriosRemoteDataSource {

    private fun coleccion(uid: String) =
        firestore.collection(COLECCION_USUARIOS).document(uid).collection(COLECCION_RECORDATORIOS)

    override suspend fun listar(uid: String): ResultadoRemoto<List<RecordatorioDto>> = ejecutar {
        coleccion(uid).get(Source.SERVER).await().documents.map { documento ->
            dtoDesdeDocumentoFirestore(documento.id, documento.data.orEmpty())
        }
    }

    override suspend fun guardar(uid: String, clave: String, recordatorio: RecordatorioDto): ResultadoRemoto<Unit> =
        ejecutar {
            val documento = recordatorio.aDocumentoFirestore() + (CAMPO_ACTUALIZADO_EL to FieldValue.serverTimestamp())
            coleccion(uid).document(clave).set(documento).await()
            Unit
        }

    override suspend fun eliminar(uid: String, clave: String): ResultadoRemoto<Unit> = ejecutar {
        coleccion(uid).document(clave).delete().await()
        Unit
    }

    private suspend fun <T> ejecutar(operacion: suspend () -> T): ResultadoRemoto<T> {
        if (!conectividad.estaConectado()) return ResultadoRemoto.SinConexion
        return try {
            // withTimeoutOrNull y no withTimeout: el vencimiento no debe confundirse con una cancelación real.
            withTimeoutOrNull(plazo) { ResultadoRemoto.Exito(operacion()) } ?: ResultadoRemoto.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            clasificarCodigoFirestore(e.code.name)
        } catch (e: FirebaseNetworkException) {
            ResultadoRemoto.SinConexion
        } catch (e: RuntimeException) {
            // Algo inesperado del SDK: se registra (sin datos de la persona) y se trata como respuesta no confiable.
            Log.w(ETIQUETA, "Error inesperado de Firestore: ${e.javaClass.simpleName}")
            ResultadoRemoto.RespuestaInvalida
        }
    }

    companion object {
        const val COLECCION_USUARIOS = "users"
        const val COLECCION_RECORDATORIOS = "reminders"
        private const val ETIQUETA = "FirestoreRemoto"
    }
}

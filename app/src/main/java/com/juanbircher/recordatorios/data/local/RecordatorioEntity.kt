package com.juanbircher.recordatorios.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fila de la tabla `recordatorios`. Responde al esquema de Room: usa tipos simples (texto y números)
 * para que no hagan falta convertidores. El resto de la app no la ve: la capa de datos la transforma
 * en [com.juanbircher.recordatorios.domain.Recordatorio] mediante los mapeos.
 *
 * - [id] es el identificador local; la interfaz navega y compara con él.
 * - [uid] es la cuenta dueña del registro (el uid de Firebase Authentication). Todas las consultas filtran
 *   por la cuenta con sesión activa: otra persona que ingrese en el mismo teléfono no ve estas filas.
 *   Vale `""` solo para registros de la versión anterior, que todavía no tienen dueño.
 * - [claveCliente] identifica el registro de punta a punta (UUID generado al crearlo). Es también el id
 *   del documento en Firestore (`users/{uid}/reminders/{claveCliente}`): reenviar el mismo cambio
 *   reemplaza ese documento y nunca crea otro.
 * - [enRemoto] indica que el documento ya existe en Firestore. Sirve para saber si un borrado necesita la
 *   red o si alcanza con quitar la fila.
 * - [eliminado] marca un borrado local que todavía debe comunicarse al servicio; la fila se quita
 *   cuando el servicio lo confirma.
 */
@Entity(
    tableName = "recordatorios",
    indices = [Index(value = ["claveCliente"], unique = true), Index(value = ["uid"])]
)
data class RecordatorioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val claveCliente: String,
    val enRemoto: Boolean = false,
    val titulo: String,
    val descripcion: String? = null,
    val categoriaId: String,
    val categoriaNombre: String,
    /** Nivel de la prioridad (3 alta, 2 media, 1 baja). */
    val prioridad: Int,
    /** `PENDIENTE`, `COMPLETADO` o `CANCELADO`. */
    val estadoTipo: String,
    /** Fecha ISO de cumplimiento; solo para `COMPLETADO`. */
    val completadoEl: String? = null,
    /** Motivo de la cancelación; solo para `CANCELADO`. */
    val motivoCancelacion: String? = null,
    val creadoEl: String,
    val fechaLimite: String? = null,
    val ubicacion: String? = null,
    /** Nombre de [com.juanbircher.recordatorios.domain.EstadoSincronizacion]. */
    val sincronizacion: String,
    /** Envíos que fallaron por causas transitorias desde el último cambio. */
    val intentos: Int = 0,
    val eliminado: Boolean = false
) {
    companion object {
        const val PENDIENTE = "PENDIENTE"
        const val COMPLETADO = "COMPLETADO"
        const val CANCELADO = "CANCELADO"
    }
}

package com.juanbircher.recordatorios.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fila de la tabla `recordatorios`. Responde al esquema de Room: usa tipos simples (texto y números)
 * para que no hagan falta convertidores. El resto de la app no la ve: la capa de datos la transforma
 * en [com.juanbircher.recordatorios.domain.Recordatorio] mediante los mapeos.
 *
 * - [id] es el identificador estable local; la interfaz navega y compara con él.
 * - [claveCliente] identifica el registro de punta a punta (UUID generado al crearlo). Se envía al
 *   servicio como clave de idempotencia: reenviar la misma creación nunca produce un segundo registro.
 * - [idRemoto] es el id que asignó el servicio; `null` mientras la creación no fue confirmada.
 * - [eliminado] marca un borrado local que todavía debe comunicarse al servicio; la fila se quita
 *   cuando el servicio lo confirma.
 */
@Entity(
    tableName = "recordatorios",
    indices = [Index(value = ["claveCliente"], unique = true)]
)
data class RecordatorioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val claveCliente: String,
    val idRemoto: Long? = null,
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

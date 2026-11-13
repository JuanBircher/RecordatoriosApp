package com.juanbircher.recordatorios.data.remote

/**
 * Contrato del servicio: así viaja un recordatorio a la nube. Es independiente de la entidad de Room y
 * del modelo de dominio, y nunca llega a las pantallas. Solo tiene lo que se comparte entre dispositivos:
 * nada del estado de sincronización ni del id local.
 *
 * Todos los campos son nullables a propósito: un documento escrito por otra versión de la app (o editado a
 * mano en la consola) puede no traer un campo, y eso no debe romper la app. El mapeo valida cada DTO y
 * descarta los inválidos.
 *
 * - [claveCliente]: UUID que genera la app; en Firestore es el id del documento, no un campo.
 * - [prioridad]: `ALTA`, `MEDIA` o `BAJA`. [estado]: `PENDIENTE`, `COMPLETADO` o `CANCELADO`.
 * - Las fechas viajan en formato ISO (`2026-11-09`).
 */
data class RecordatorioDto(
    val claveCliente: String? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val categoriaId: String? = null,
    val prioridad: String? = null,
    val estado: String? = null,
    val completadoEl: String? = null,
    val motivoCancelacion: String? = null,
    val creadoEl: String? = null,
    val fechaLimite: String? = null,
    val ubicacion: String? = null
)

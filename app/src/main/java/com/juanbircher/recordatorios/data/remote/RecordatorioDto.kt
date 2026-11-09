package com.juanbircher.recordatorios.data.remote

/**
 * Contrato del servicio: así viaja un recordatorio por la red. Es independiente de la entidad de Room y
 * del modelo de dominio, y nunca llega a las pantallas.
 *
 * Todos los campos son nullables a propósito: Gson no respeta la nulabilidad de Kotlin, y un servicio
 * que omite un campo no debe romper la app. El mapeo valida cada DTO y descarta los inválidos.
 *
 * - [id]: lo asigna el servicio. `null` al crear.
 * - [claveCliente]: UUID que genera la app; permite reconocer el mismo registro en ambos lados.
 * - [prioridad]: `ALTA`, `MEDIA` o `BAJA`. [estado]: `PENDIENTE`, `COMPLETADO` o `CANCELADO`.
 * - Las fechas viajan en formato ISO (`2026-11-09`).
 */
data class RecordatorioDto(
    val id: Long? = null,
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

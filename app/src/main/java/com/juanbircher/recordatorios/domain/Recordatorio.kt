package com.juanbircher.recordatorios.domain

import java.time.LocalDate

/**
 * Recordatorio de la aplicación. Es inmutable: todas sus propiedades son `val`, y un cambio
 * (por ejemplo, completarlo) se expresa creando una copia con `copy()`.
 *
 * Los datos opcionales son nullable y por defecto valen `null`.
 */
data class Recordatorio(
    val id: Long,
    val titulo: String,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val creadoEl: LocalDate,
    val estado: EstadoRecordatorio = EstadoRecordatorio.Pendiente,
    val descripcion: String? = null,
    val fechaLimite: LocalDate? = null,
    /** Lugar asociado al aviso; se usará cuando la app incorpore ubicación. */
    val ubicacion: String? = null
)

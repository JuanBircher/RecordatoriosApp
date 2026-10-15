package com.juanbircher.recordatorios.domain

import java.time.LocalDate

/**
 * Estado de un recordatorio. Cada variante transporta solo la información que le corresponde:
 * un recordatorio pendiente no tiene fecha de cumplimiento, y solo uno cancelado puede tener motivo.
 */
sealed interface EstadoRecordatorio {
    data object Pendiente : EstadoRecordatorio

    data class Completado(val completadoEl: LocalDate) : EstadoRecordatorio

    /** El motivo es opcional: el usuario puede cancelar sin explicar por qué. */
    data class Cancelado(val motivo: String? = null) : EstadoRecordatorio
}

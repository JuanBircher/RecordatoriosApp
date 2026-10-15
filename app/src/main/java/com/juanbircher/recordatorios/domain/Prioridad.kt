package com.juanbircher.recordatorios.domain

/**
 * Prioridad de un recordatorio. Es una jerarquía cerrada: solo existen estas tres, y un `when`
 * sobre Prioridad no necesita rama `else`.
 *
 * [nivel] permite ordenar (mayor = más urgente) sin depender del texto de la etiqueta.
 */
sealed class Prioridad(val nivel: Int, val etiqueta: String) {
    data object Alta : Prioridad(nivel = 3, etiqueta = "Alta")
    data object Media : Prioridad(nivel = 2, etiqueta = "Media")
    data object Baja : Prioridad(nivel = 1, etiqueta = "Baja")

    companion object {
        /** Todas las prioridades, de la más urgente a la menos urgente. */
        val todas: List<Prioridad> by lazy { listOf(Alta, Media, Baja) }
    }
}

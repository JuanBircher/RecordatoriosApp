package com.juanbircher.recordatorios.domain

/** Filtro por estado que ofrece la pantalla. */
sealed class FiltroEstado(val etiqueta: String) {
    data object Todos : FiltroEstado("Todos")
    data object Pendientes : FiltroEstado("Pendientes")
    data object Completados : FiltroEstado("Completados")
    data object Cancelados : FiltroEstado("Cancelados")

    fun admite(estado: EstadoRecordatorio): Boolean = when (this) {
        Todos -> true
        Pendientes -> estado is EstadoRecordatorio.Pendiente
        Completados -> estado is EstadoRecordatorio.Completado
        Cancelados -> estado is EstadoRecordatorio.Cancelado
    }

    companion object {
        val todos: List<FiltroEstado> by lazy { listOf(Todos, Pendientes, Completados, Cancelados) }
    }
}

/** Conserva los recordatorios cuyo estado admite el [filtro]. No modifica la lista original. */
fun List<Recordatorio>.filtrarPorEstado(filtro: FiltroEstado): List<Recordatorio> =
    filter { filtro.admite(it.estado) }

/** Conserva los recordatorios de la [prioridad] indicada; `null` significa todas. */
fun List<Recordatorio>.filtrarPorPrioridad(prioridad: Prioridad?): List<Recordatorio> =
    if (prioridad == null) this else filter { it.prioridad == prioridad }

/** Ordena de la prioridad más alta a la más baja; a igual prioridad, por fecha límite (sin fecha al final). */
fun List<Recordatorio>.ordenadosPorPrioridad(): List<Recordatorio> =
    sortedWith(
        compareByDescending<Recordatorio> { it.prioridad.nivel }
            .thenBy(nullsLast()) { it.fechaLimite }
    )

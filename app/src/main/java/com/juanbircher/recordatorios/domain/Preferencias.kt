package com.juanbircher.recordatorios.domain

/** Tema visual elegido; [Sistema] sigue el modo claro u oscuro del dispositivo. */
enum class TemaApp { Sistema, Claro, Oscuro }

/** Criterio con el que se ordena el listado. */
enum class OrdenListado(val etiqueta: String) {
    Prioridad("Por prioridad"),
    FechaLimite("Por fecha límite"),
    MasRecientes("Más recientes primero")
}

/**
 * Configuración simple de la app. Es lo único que vive en DataStore: los recordatorios van en Room.
 * Los valores por defecto son los que se usan cuando todavía no hay nada guardado o el dato es inválido.
 */
data class Preferencias(
    val tema: TemaApp = TemaApp.Sistema,
    val mostrarCompletados: Boolean = true,
    val orden: OrdenListado = OrdenListado.Prioridad
)

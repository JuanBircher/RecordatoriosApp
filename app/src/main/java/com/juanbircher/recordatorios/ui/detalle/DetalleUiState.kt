package com.juanbircher.recordatorios.ui.detalle

import com.juanbircher.recordatorios.domain.Recordatorio

/** Lo que puede mostrar el detalle de un recordatorio. */
sealed interface DetalleUiState {
    data object Cargando : DetalleUiState

    /** [errorAccion] indica que la última acción (completar, eliminar) no se pudo guardar. */
    data class ConDatos(val recordatorio: Recordatorio, val errorAccion: Boolean = false) : DetalleUiState

    data object NoEncontrado : DetalleUiState
    data object Error : DetalleUiState

    /** Se eliminó desde esta pantalla: la ruta vuelve al listado. */
    data object Eliminado : DetalleUiState
}

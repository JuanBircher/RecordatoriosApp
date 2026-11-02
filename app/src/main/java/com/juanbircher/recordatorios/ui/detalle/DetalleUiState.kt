package com.juanbircher.recordatorios.ui.detalle

import com.juanbircher.recordatorios.domain.Recordatorio

/** Lo que puede mostrar el detalle de un recordatorio. */
sealed interface DetalleUiState {
    data object Cargando : DetalleUiState
    data class ConDatos(val recordatorio: Recordatorio) : DetalleUiState
    data object NoEncontrado : DetalleUiState
    data object Error : DetalleUiState
}

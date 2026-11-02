package com.juanbircher.recordatorios.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.validation.ErrorValidacion
import com.juanbircher.recordatorios.validation.ReglasRecordatorio

/** El ViewModel informa errores tipados; recién acá se convierten en texto, porque los recursos son de la interfaz. */
@Composable
fun mensajeDe(error: ErrorValidacion): String = when (error) {
    ErrorValidacion.TituloVacio -> stringResource(R.string.error_titulo_vacio)
    is ErrorValidacion.TituloLongitudInvalida -> stringResource(
        R.string.error_titulo_longitud,
        ReglasRecordatorio.TITULO_MIN,
        ReglasRecordatorio.TITULO_MAX,
        error.actual
    )
    is ErrorValidacion.DescripcionDemasiadoLarga ->
        stringResource(R.string.error_descripcion_larga, ReglasRecordatorio.DESCRIPCION_MAX, error.actual)
    ErrorValidacion.FechaLimiteAnteriorACreacion -> stringResource(R.string.error_fecha_limite)
    is ErrorValidacion.CategoriaInexistente -> stringResource(R.string.error_categoria, error.id)
    ErrorValidacion.CategoriaNoSeleccionada -> stringResource(R.string.error_categoria_sin_elegir)
    ErrorValidacion.PrioridadNoSeleccionada -> stringResource(R.string.error_prioridad_sin_elegir)
}

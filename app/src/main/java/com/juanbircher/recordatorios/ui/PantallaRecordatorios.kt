package com.juanbircher.recordatorios.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.CargaRecordatorios
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.Descartado
import com.juanbircher.recordatorios.data.cargarRecordatorios
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme
import com.juanbircher.recordatorios.validation.ErrorValidacion
import com.juanbircher.recordatorios.validation.ReglasRecordatorio

/** Valor guardado cuando el filtro de prioridad es "Todas". */
private const val SIN_FILTRO_PRIORIDAD = -1

/**
 * Pantalla inicial: lista de recordatorios simulados con filtros por estado y prioridad.
 *
 * Los filtros se guardan como índices con rememberSaveable para que sobrevivan a la rotación
 * del teléfono (la actividad se recrea y `remember` solo perdería la selección).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRecordatorios(
    carga: CargaRecordatorios = remember {
        cargarRecordatorios(DatosSimulados.recordatorios(), DatosSimulados.categorias)
    }
) {
    var indiceEstado by rememberSaveable { mutableIntStateOf(0) }
    var indicePrioridad by rememberSaveable { mutableIntStateOf(SIN_FILTRO_PRIORIDAD) }

    val filtroEstado = FiltroEstado.todos[indiceEstado]
    val prioridad = Prioridad.todas.getOrNull(indicePrioridad)
    val estado = estadoPantalla(carga, filtroEstado, prioridad)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.titulo_pantalla)) }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FilaDeFiltros(titulo = stringResource(R.string.filtro_estado)) {
                    FiltroEstado.todos.forEachIndexed { indice, filtro ->
                        FilterChip(
                            selected = indice == indiceEstado,
                            onClick = { indiceEstado = indice },
                            label = { Text(filtro.etiqueta) }
                        )
                    }
                }
            }
            item {
                FilaDeFiltros(titulo = stringResource(R.string.filtro_prioridad)) {
                    FilterChip(
                        selected = indicePrioridad == SIN_FILTRO_PRIORIDAD,
                        onClick = { indicePrioridad = SIN_FILTRO_PRIORIDAD },
                        label = { Text(stringResource(R.string.filtro_prioridad_todas)) }
                    )
                    Prioridad.todas.forEachIndexed { indice, opcion ->
                        FilterChip(
                            selected = indice == indicePrioridad,
                            onClick = { indicePrioridad = indice },
                            label = { Text(opcion.etiqueta) }
                        )
                    }
                }
            }

            if (estado.descartados.isNotEmpty()) {
                item { AvisoDescartados(estado.descartados) }
            }

            when (estado) {
                is EstadoPantalla.ConDatos ->
                    items(estado.recordatorios, key = { it.id }) { TarjetaRecordatorio(it) }

                is EstadoPantalla.SinResultados -> item {
                    Text(
                        text = stringResource(R.string.sin_resultados),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaDeFiltros(titulo: String, chips: @Composable () -> Unit) {
    Column {
        Text(text = titulo, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) { chips() }
    }
}

@Composable
private fun AvisoDescartados(descartados: List<Descartado>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.descartados_titulo, descartados.size, descartados.size),
                style = MaterialTheme.typography.titleSmall
            )
            descartados.forEach { descartado ->
                val titulo = descartado.recordatorio.titulo.trim()
                    .ifEmpty { stringResource(R.string.descartado_sin_titulo) }
                val motivos = descartado.errores.map { mensajeDe(it) }.joinToString("; ")
                Text(text = "• $titulo: $motivos", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun mensajeDe(error: ErrorValidacion): String = when (error) {
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
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PantallaRecordatoriosPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        PantallaRecordatorios()
    }
}

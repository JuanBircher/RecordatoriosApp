package com.juanbircher.recordatorios.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.componentes.AccionMensaje
import com.juanbircher.recordatorios.ui.componentes.MensajeDeEstado
import com.juanbircher.recordatorios.ui.componentes.TipoMensaje
import com.juanbircher.recordatorios.ui.componentes.formatoFecha
import com.juanbircher.recordatorios.ui.componentes.textoEstado
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme
import java.time.LocalDate

/** Conecta el ViewModel con la pantalla. El ViewModel ya recibió el id desde la navegación. */
@Composable
fun DetalleRuta(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetalleViewModel = viewModel(factory = FabricasViewModel.detalle)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaDetalle(
        uiState = uiState,
        onVolver = onVolver,
        onReintentar = viewModel::onReintentar,
        modifier = modifier
    )
}

/** Detalle de un recordatorio: muestra toda la información y los datos opcionales que falten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalle(
    uiState: DetalleUiState,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.titulo_detalle)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (uiState) {
            DetalleUiState.Cargando -> MensajeDeEstado(
                tipo = TipoMensaje.Carga,
                mensaje = stringResource(R.string.cargando_detalle),
                modifier = Modifier.padding(innerPadding)
            )

            DetalleUiState.NoEncontrado -> MensajeDeEstado(
                tipo = TipoMensaje.Vacio,
                mensaje = stringResource(R.string.detalle_no_encontrado),
                modifier = Modifier.padding(innerPadding),
                accion = AccionMensaje(stringResource(R.string.volver), onVolver)
            )

            DetalleUiState.Error -> MensajeDeEstado(
                tipo = TipoMensaje.Error,
                mensaje = stringResource(R.string.error_detalle),
                modifier = Modifier.padding(innerPadding),
                accion = AccionMensaje(stringResource(R.string.reintentar), onReintentar)
            )

            is DetalleUiState.ConDatos -> ContenidoDetalle(
                recordatorio = uiState.recordatorio,
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun ContenidoDetalle(recordatorio: Recordatorio, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = recordatorio.titulo,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            DatoDetalle(
                etiqueta = stringResource(R.string.campo_detalle_descripcion),
                valor = recordatorio.descripcion ?: stringResource(R.string.sin_descripcion)
            )
            DatoDetalle(stringResource(R.string.campo_detalle_categoria), recordatorio.categoria.nombre)
            DatoDetalle(stringResource(R.string.campo_detalle_prioridad), recordatorio.prioridad.etiqueta)
            DatoDetalle(stringResource(R.string.campo_detalle_estado), textoEstado(recordatorio.estado))
            DatoDetalle(
                etiqueta = stringResource(R.string.campo_detalle_creado),
                valor = recordatorio.creadoEl.format(formatoFecha)
            )
            DatoDetalle(
                etiqueta = stringResource(R.string.campo_detalle_fecha_limite),
                valor = recordatorio.fechaLimite?.format(formatoFecha) ?: stringResource(R.string.sin_fecha_limite)
            )
            DatoDetalle(
                etiqueta = stringResource(R.string.campo_detalle_lugar),
                valor = recordatorio.ubicacion ?: stringResource(R.string.sin_lugar)
            )
        }
    }
}

@Composable
private fun DatoDetalle(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PantallaDetallePreview() {
    RecordatoriosTheme(dynamicColor = false) {
        PantallaDetalle(
            uiState = DetalleUiState.ConDatos(
                Recordatorio(
                    id = 2,
                    titulo = "Turno con el dentista",
                    categoria = DatosSimulados.salud,
                    prioridad = Prioridad.Media,
                    creadoEl = LocalDate.of(2026, 10, 20),
                    fechaLimite = LocalDate.of(2026, 11, 8),
                    ubicacion = "Consultorio Av. Colón 1200"
                )
            ),
            onVolver = {},
            onReintentar = {}
        )
    }
}

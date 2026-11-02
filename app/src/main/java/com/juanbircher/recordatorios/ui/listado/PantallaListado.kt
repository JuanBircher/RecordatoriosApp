package com.juanbircher.recordatorios.ui.listado

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.EscenarioSimulado
import com.juanbircher.recordatorios.data.cargarRecordatorios
import com.juanbircher.recordatorios.domain.FiltroEstado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.componentes.AccionMensaje
import com.juanbircher.recordatorios.ui.componentes.MensajeDeEstado
import com.juanbircher.recordatorios.ui.componentes.TarjetaRecordatorio
import com.juanbircher.recordatorios.ui.componentes.TipoMensaje
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme

/** Conecta el ViewModel con la pantalla. La recolección se detiene cuando la pantalla no está visible. */
@Composable
fun ListadoRuta(
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListadoViewModel = viewModel(factory = FabricasViewModel.listado)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaListado(
        uiState = uiState,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::onReintentar,
        onFiltroEstado = viewModel::onFiltroEstado,
        onFiltroPrioridad = viewModel::onFiltroPrioridad,
        onEscenarioSimulado = viewModel::onEscenarioSimulado,
        modifier = modifier
    )
}

/** Pantalla de listado: dibuja el estado recibido y avisa cada acción mediante lambdas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaListado(
    uiState: ListadoUiState,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Long) -> Unit,
    onReintentar: () -> Unit,
    onFiltroEstado: (FiltroEstado) -> Unit,
    onFiltroPrioridad: (Prioridad?) -> Unit,
    onEscenarioSimulado: (EscenarioSimulado) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.titulo_pantalla)) },
                actions = { MenuSimulacion(onEscenarioSimulado) }
            )
        },
        floatingActionButton = {
            if (uiState is ListadoUiState.ConDatos) {
                FloatingActionButton(onClick = onCrearRecordatorio) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.crear_recordatorio))
                }
            }
        }
    ) { innerPadding ->
        when (uiState) {
            ListadoUiState.Cargando -> MensajeDeEstado(
                tipo = TipoMensaje.Carga,
                mensaje = stringResource(R.string.cargando_recordatorios),
                modifier = Modifier.padding(innerPadding)
            )

            ListadoUiState.Vacio -> MensajeDeEstado(
                tipo = TipoMensaje.Vacio,
                mensaje = stringResource(R.string.listado_vacio),
                modifier = Modifier.padding(innerPadding),
                accion = AccionMensaje(stringResource(R.string.crear_recordatorio), onCrearRecordatorio)
            )

            ListadoUiState.Error -> MensajeDeEstado(
                tipo = TipoMensaje.Error,
                mensaje = stringResource(R.string.error_carga),
                modifier = Modifier.padding(innerPadding),
                accion = AccionMensaje(stringResource(R.string.reintentar), onReintentar)
            )

            is ListadoUiState.ConDatos -> ContenidoListado(
                estado = uiState,
                innerPadding = innerPadding,
                onRecordatorioClick = onRecordatorioClick,
                onFiltroEstado = onFiltroEstado,
                onFiltroPrioridad = onFiltroPrioridad
            )
        }
    }
}

@Composable
private fun ContenidoListado(
    estado: ListadoUiState.ConDatos,
    innerPadding: PaddingValues,
    onRecordatorioClick: (Long) -> Unit,
    onFiltroEstado: (FiltroEstado) -> Unit,
    onFiltroPrioridad: (Prioridad?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding(),
            // Espacio extra para que el botón flotante no tape la última tarjeta.
            bottom = innerPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FilaDeFiltros(titulo = stringResource(R.string.filtro_estado)) {
                FiltroEstado.todos.forEach { filtro ->
                    FilterChip(
                        selected = filtro == estado.filtroEstado,
                        onClick = { onFiltroEstado(filtro) },
                        label = { Text(filtro.etiqueta) }
                    )
                }
            }
        }
        item {
            FilaDeFiltros(titulo = stringResource(R.string.filtro_prioridad)) {
                FilterChip(
                    selected = estado.filtroPrioridad == null,
                    onClick = { onFiltroPrioridad(null) },
                    label = { Text(stringResource(R.string.filtro_prioridad_todas)) }
                )
                Prioridad.todas.forEach { opcion ->
                    FilterChip(
                        selected = opcion == estado.filtroPrioridad,
                        onClick = { onFiltroPrioridad(opcion) },
                        label = { Text(opcion.etiqueta) }
                    )
                }
            }
        }

        if (estado.visibles.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.sin_resultados),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(estado.visibles, key = { it.id }) { recordatorio ->
                TarjetaRecordatorio(recordatorio, onClick = { onRecordatorioClick(recordatorio.id) })
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

/** Menú para provocar cada estado sin base de datos ni servicio remoto. */
@Composable
private fun MenuSimulacion(onEscenarioSimulado: (EscenarioSimulado) -> Unit) {
    var abierto by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.simular_abrir_menu))
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.simular_titulo)) },
                onClick = {},
                enabled = false
            )
            EscenarioSimulado.entries.forEach { escenario ->
                DropdownMenuItem(
                    text = { Text(textoEscenario(escenario)) },
                    onClick = {
                        abierto = false
                        onEscenarioSimulado(escenario)
                    }
                )
            }
        }
    }
}

@Composable
private fun textoEscenario(escenario: EscenarioSimulado): String = when (escenario) {
    EscenarioSimulado.ConDatos -> stringResource(R.string.simular_con_datos)
    EscenarioSimulado.ListaVacia -> stringResource(R.string.simular_lista_vacia)
    EscenarioSimulado.ErrorAlCargar -> stringResource(R.string.simular_error_cargar)
    EscenarioSimulado.ErrorAlGuardar -> stringResource(R.string.simular_error_guardar)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PantallaListadoPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        PantallaListado(
            uiState = ListadoUiState.ConDatos(
                cargarRecordatorios(DatosSimulados.recordatorios(), DatosSimulados.categorias).validos
            ),
            onCrearRecordatorio = {},
            onRecordatorioClick = {},
            onReintentar = {},
            onFiltroEstado = {},
            onFiltroPrioridad = {},
            onEscenarioSimulado = {}
        )
    }
}

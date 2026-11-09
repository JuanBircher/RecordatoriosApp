package com.juanbircher.recordatorios.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.TemaApp
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme

/** Conecta el ViewModel con la pantalla de ajustes. */
@Composable
fun AjustesRuta(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel = viewModel(factory = FabricasViewModel.ajustes)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaAjustes(
        uiState = uiState,
        onVolver = onVolver,
        onTema = viewModel::onTema,
        onMostrarCompletados = viewModel::onMostrarCompletados,
        onOrden = viewModel::onOrden,
        onAvisoVisto = viewModel::onAvisoVisto,
        modifier = modifier
    )
}

/** Preferencias persistentes (DataStore): tema, completados visibles y orden del listado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    uiState: AjustesUiState,
    onVolver: () -> Unit,
    onTema: (TemaApp) -> Unit,
    onMostrarCompletados: (Boolean) -> Unit,
    onOrden: (OrdenListado) -> Unit,
    onAvisoVisto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferencias = uiState.preferencias

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.titulo_ajustes)) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.errorAlGuardar) {
                AvisoDeError(onAvisoVisto)
            }

            Seccion(stringResource(R.string.ajustes_tema))
            Column(Modifier.selectableGroup()) {
                OpcionExcluyente(stringResource(R.string.tema_sistema), preferencias.tema == TemaApp.Sistema) {
                    onTema(TemaApp.Sistema)
                }
                OpcionExcluyente(stringResource(R.string.tema_claro), preferencias.tema == TemaApp.Claro) {
                    onTema(TemaApp.Claro)
                }
                OpcionExcluyente(stringResource(R.string.tema_oscuro), preferencias.tema == TemaApp.Oscuro) {
                    onTema(TemaApp.Oscuro)
                }
            }

            Seccion(stringResource(R.string.ajustes_listado))
            val etiquetaCompletados = stringResource(R.string.ajustes_mostrar_completados)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = preferencias.mostrarCompletados,
                        role = Role.Switch,
                        onValueChange = onMostrarCompletados
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(etiquetaCompletados, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = preferencias.mostrarCompletados, onCheckedChange = null)
            }

            Seccion(stringResource(R.string.ajustes_orden))
            Column(Modifier.selectableGroup()) {
                OpcionExcluyente(stringResource(R.string.orden_prioridad), preferencias.orden == OrdenListado.Prioridad) {
                    onOrden(OrdenListado.Prioridad)
                }
                OpcionExcluyente(stringResource(R.string.orden_fecha_limite), preferencias.orden == OrdenListado.FechaLimite) {
                    onOrden(OrdenListado.FechaLimite)
                }
                OpcionExcluyente(stringResource(R.string.orden_recientes), preferencias.orden == OrdenListado.MasRecientes) {
                    onOrden(OrdenListado.MasRecientes)
                }
            }

            Text(
                text = stringResource(R.string.ajustes_nota),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun Seccion(titulo: String, modifier: Modifier = Modifier) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(top = 12.dp)
            .semantics { heading() }
    )
}

/** Una opción de un grupo de selección única: toda la fila es tocable, no solo el botón de radio. */
@Composable
private fun OpcionExcluyente(texto: String, seleccionada: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = seleccionada, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionada, onClick = null)
        Text(text = texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun AvisoDeError(onAvisoVisto: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.error_ajustes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAvisoVisto) { Text(stringResource(R.string.entendido)) }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PantallaAjustesPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        PantallaAjustes(
            uiState = AjustesUiState(),
            onVolver = {},
            onTema = {},
            onMostrarCompletados = {},
            onOrden = {},
            onAvisoVisto = {}
        )
    }
}

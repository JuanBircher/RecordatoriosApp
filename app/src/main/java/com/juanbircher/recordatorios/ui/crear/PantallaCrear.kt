package com.juanbircher.recordatorios.ui.crear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.componentes.CampoTexto
import com.juanbircher.recordatorios.ui.componentes.SelectorOpciones
import com.juanbircher.recordatorios.ui.mensajeDe
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme
import com.juanbircher.recordatorios.validation.ReglasRecordatorio

/** Conecta el ViewModel con la pantalla y avisa hacia afuera cuando el guardado terminó con éxito. */
@Composable
fun CrearRuta(
    onGuardado: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CrearViewModel = viewModel(factory = FabricasViewModel.crear)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.envio) {
        if (uiState.envio is EstadoEnvio.Exitoso) onGuardado()
    }

    PantallaCrear(
        uiState = uiState,
        onTituloChange = viewModel::onTituloChange,
        onDescripcionChange = viewModel::onDescripcionChange,
        onCategoriaChange = viewModel::onCategoriaChange,
        onPrioridadChange = viewModel::onPrioridadChange,
        onGuardar = viewModel::onGuardar,
        onCancelar = onCancelar,
        modifier = modifier
    )
}

/** Formulario de creación: todos los campos son controlados y cada cambio se informa al ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCrear(
    uiState: CrearUiState,
    onTituloChange: (String) -> Unit,
    onDescripcionChange: (String) -> Unit,
    onCategoriaChange: (Categoria) -> Unit,
    onPrioridadChange: (Prioridad) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errores = uiState.erroresVisibles

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.titulo_crear)) },
                navigationIcon = {
                    IconButton(onClick = onCancelar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancelar)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CampoTexto(
                valor = uiState.titulo,
                onValorChange = onTituloChange,
                etiqueta = stringResource(R.string.campo_titulo),
                mensajeError = errores.titulo?.let { mensajeDe(it) },
                habilitado = uiState.editable
            )
            CampoTexto(
                valor = uiState.descripcion,
                onValorChange = onDescripcionChange,
                etiqueta = stringResource(R.string.campo_descripcion),
                mensajeError = errores.descripcion?.let { mensajeDe(it) },
                ayuda = stringResource(
                    R.string.contador_caracteres,
                    uiState.descripcion.length,
                    ReglasRecordatorio.DESCRIPCION_MAX
                ),
                habilitado = uiState.editable,
                unaLinea = false
            )
            SelectorOpciones(
                etiqueta = stringResource(R.string.campo_categoria),
                opciones = uiState.categoriasDisponibles,
                seleccionada = uiState.categoria,
                textoDe = { it.nombre },
                onSeleccion = onCategoriaChange,
                mensajeError = errores.categoria?.let { mensajeDe(it) },
                habilitado = uiState.editable
            )
            SelectorOpciones(
                etiqueta = stringResource(R.string.campo_prioridad),
                opciones = Prioridad.todas,
                seleccionada = uiState.prioridad,
                textoDe = { it.etiqueta },
                onSeleccion = onPrioridadChange,
                mensajeError = errores.prioridad?.let { mensajeDe(it) },
                habilitado = uiState.editable
            )

            // La región viva existe siempre: se anuncia cuando cambia su contenido, no cuando aparece junto con él.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                if (uiState.envio is EstadoEnvio.Error) ErrorDeGuardado()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancelar))
                }
                Button(
                    onClick = onGuardar,
                    enabled = uiState.puedeGuardar,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.envio is EstadoEnvio.Enviando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.guardando), modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text(stringResource(R.string.guardar))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDeGuardado(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(text = stringResource(R.string.error_guardado), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PantallaCrearPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        PantallaCrear(
            uiState = CrearUiState(
                titulo = "Llamar al banco",
                categoria = DatosSimulados.trabajo,
                prioridad = Prioridad.Alta,
                categoriasDisponibles = DatosSimulados.categorias.toList()
            ),
            onTituloChange = {},
            onDescripcionChange = {},
            onCategoriaChange = {},
            onPrioridadChange = {},
            onGuardar = {},
            onCancelar = {}
        )
    }
}

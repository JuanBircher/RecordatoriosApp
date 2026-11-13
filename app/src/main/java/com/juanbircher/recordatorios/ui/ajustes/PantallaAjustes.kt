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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.juanbircher.recordatorios.data.ResultadoPruebaAcceso
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.TemaApp
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.textoCausa
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
        onCerrarSesion = viewModel::onCerrarSesion,
        onProbarAcceso = viewModel::onProbarAcceso,
        modifier = modifier
    )
}

/**
 * Cuenta (correo, cierre de sesión) y preferencias persistentes (DataStore): tema, completados visibles y
 * orden del listado. En el ambiente de desarrollo agrega la prueba de autorización contra otra cuenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    uiState: AjustesUiState,
    onVolver: () -> Unit,
    onTema: (TemaApp) -> Unit,
    onMostrarCompletados: (Boolean) -> Unit,
    onOrden: (OrdenListado) -> Unit,
    onAvisoVisto: () -> Unit,
    onCerrarSesion: () -> Unit,
    onProbarAcceso: (String) -> Unit,
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

            uiState.cuenta?.let { cuenta ->
                SeccionCuenta(cuenta, uiState.cambiosSinEnviar, onCerrarSesion)
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

            if (uiState.mostrarPruebaAcceso) {
                SeccionPruebaAcceso(uiState.pruebaAcceso, onProbarAcceso)
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

/** Quién tiene la sesión, qué quedó sin enviar y el cierre de sesión (con confirmación). */
@Composable
private fun SeccionCuenta(cuenta: EstadoSesion.Activa, cambiosSinEnviar: Int, onCerrarSesion: () -> Unit) {
    var confirmando by rememberSaveable { mutableStateOf(false) }

    Seccion(stringResource(R.string.ajustes_cuenta))
    cuenta.correo?.let { Text(stringResource(R.string.cuenta_correo, it), style = MaterialTheme.typography.bodyLarge) }
    Text(
        text = stringResource(R.string.cuenta_uid, cuenta.uid),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (cambiosSinEnviar > 0) {
        Text(
            text = pluralStringResource(R.plurals.cuenta_cambios_sin_enviar, cambiosSinEnviar, cambiosSinEnviar),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
    OutlinedButton(onClick = { confirmando = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.cuenta_cerrar_sesion))
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text(stringResource(R.string.cuenta_confirmar_titulo)) },
            text = { Text(stringResource(R.string.cuenta_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    onCerrarSesion()
                }) { Text(stringResource(R.string.cuenta_cerrar_sesion)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }
}

/**
 * Solo en desarrollo: lee del servicio la colección de otra cuenta sin pasar por la interfaz normal, para
 * demostrar que la autorización la decide el servicio (reglas de Firestore) y no la ausencia de un botón.
 */
@Composable
private fun SeccionPruebaAcceso(prueba: PruebaAcceso?, onProbarAcceso: (String) -> Unit) {
    var otroUid by rememberSaveable { mutableStateOf("") }

    Seccion(stringResource(R.string.prueba_acceso_titulo))
    Text(
        text = stringResource(R.string.prueba_acceso_explicacion),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = otroUid,
        onValueChange = { otroUid = it },
        label = { Text(stringResource(R.string.prueba_acceso_uid)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(
        onClick = { onProbarAcceso(otroUid) },
        enabled = otroUid.isNotBlank() && prueba != PruebaAcceso.EnCurso,
        modifier = Modifier.heightIn(min = 48.dp)
    ) { Text(stringResource(R.string.prueba_acceso_boton)) }

    val resultado = when (prueba) {
        null -> null
        PruebaAcceso.EnCurso -> stringResource(R.string.prueba_acceso_en_curso)
        is PruebaAcceso.Terminada -> when (val r = prueba.resultado) {
            is ResultadoPruebaAcceso.Rechazado ->
                if (r.causa == CausaFallo.NoAutorizado) {
                    stringResource(R.string.prueba_acceso_denegado)
                } else {
                    // Otro fallo (sin conexión, servicio caído): la prueba no fue concluyente.
                    stringResource(R.string.prueba_acceso_rechazado, textoCausa(r.causa))
                }
            is ResultadoPruebaAcceso.Permitido -> stringResource(R.string.prueba_acceso_permitido, r.cantidad)
        }
    }
    resultado?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
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
            uiState = AjustesUiState(
                cuenta = EstadoSesion.Activa(uid = "Xy12abCdEf34", correo = "ana@prueba.com"),
                cambiosSinEnviar = 2,
                mostrarPruebaAcceso = true
            ),
            onVolver = {},
            onTema = {},
            onMostrarCompletados = {},
            onOrden = {},
            onAvisoVisto = {},
            onCerrarSesion = {},
            onProbarAcceso = {}
        )
    }
}

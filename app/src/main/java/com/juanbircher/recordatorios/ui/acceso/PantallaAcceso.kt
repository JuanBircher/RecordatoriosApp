package com.juanbircher.recordatorios.ui.acceso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.domain.ErrorAuth
import com.juanbircher.recordatorios.domain.MotivoSinSesion
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme

/** Conecta el ViewModel con la pantalla de acceso. [motivo] explica por qué no hay sesión, si corresponde. */
@Composable
fun AccesoRuta(
    motivo: MotivoSinSesion?,
    modifier: Modifier = Modifier,
    viewModel: AccesoViewModel = viewModel(factory = FabricasViewModel.acceso)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PantallaAcceso(
        uiState = uiState,
        motivo = motivo,
        onCorreo = viewModel::onCorreo,
        onContrasena = viewModel::onContrasena,
        onEnviar = viewModel::onEnviar,
        onCambiarModo = viewModel::onCambiarModo,
        modifier = modifier
    )
}

/** Ingreso y registro con correo y contraseña. No navega: la raíz reacciona cuando la sesión queda activa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAcceso(
    uiState: AccesoUiState,
    motivo: MotivoSinSesion?,
    onCorreo: (String) -> Unit,
    onContrasena: (String) -> Unit,
    onEnviar: () -> Unit,
    onCambiarModo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ingresando = uiState.modo == ModoAcceso.Ingresar
    val editable = !uiState.enviando
    var contrasenaVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            motivo?.let { AvisoMotivo(it) }

            Text(
                text = stringResource(if (ingresando) R.string.acceso_titulo_ingresar else R.string.acceso_titulo_crear),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.acceso_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val errorCorreo = if (uiState.errorCorreo) stringResource(R.string.acceso_error_correo) else null
            OutlinedTextField(
                value = uiState.correo,
                onValueChange = onCorreo,
                label = { Text(stringResource(R.string.acceso_correo)) },
                singleLine = true,
                enabled = editable,
                isError = errorCorreo != null,
                supportingText = errorCorreo?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { if (errorCorreo != null) error(errorCorreo) }
            )

            val errorContrasena = if (uiState.errorContrasena) stringResource(R.string.acceso_error_contrasena) else null
            OutlinedTextField(
                value = uiState.contrasena,
                onValueChange = onContrasena,
                label = { Text(stringResource(R.string.acceso_contrasena)) },
                singleLine = true,
                enabled = editable,
                isError = errorContrasena != null,
                supportingText = { Text(errorContrasena ?: stringResource(R.string.acceso_contrasena_ayuda)) },
                visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onEnviar() }),
                trailingIcon = {
                    val descripcion = stringResource(
                        if (contrasenaVisible) R.string.acceso_ocultar_contrasena else R.string.acceso_mostrar_contrasena
                    )
                    TextButton(
                        onClick = { contrasenaVisible = !contrasenaVisible },
                        modifier = Modifier.semantics { contentDescription = descripcion }
                    ) {
                        Text(stringResource(if (contrasenaVisible) R.string.acceso_ocultar else R.string.acceso_mostrar))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { if (errorContrasena != null) error(errorContrasena) }
            )

            uiState.errorAuth?.let { AvisoError(textoErrorAuth(it)) }

            Button(
                onClick = onEnviar,
                enabled = editable,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                if (uiState.enviando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(stringResource(R.string.acceso_enviando), modifier = Modifier.padding(start = 12.dp))
                } else {
                    Text(stringResource(if (ingresando) R.string.acceso_boton_ingresar else R.string.acceso_boton_crear))
                }
            }

            TextButton(
                onClick = onCambiarModo,
                enabled = editable,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(if (ingresando) R.string.acceso_ir_a_crear else R.string.acceso_ir_a_ingresar))
            }
        }
    }
}

@Composable
private fun AvisoMotivo(motivo: MotivoSinSesion) {
    when (motivo) {
        MotivoSinSesion.SesionInvalida -> AvisoError(stringResource(R.string.acceso_aviso_sesion_invalida))
        MotivoSinSesion.CerroSesion -> Aviso(
            texto = stringResource(R.string.acceso_aviso_cerro_sesion),
            fondo = MaterialTheme.colorScheme.secondaryContainer,
            contenido = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun AvisoError(texto: String) = Aviso(
    texto = texto,
    fondo = MaterialTheme.colorScheme.errorContainer,
    contenido = MaterialTheme.colorScheme.onErrorContainer
)

/** Mensaje destacado; el lector de pantalla lo anuncia cuando aparece. */
@Composable
private fun Aviso(texto: String, fondo: Color, contenido: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = contenido)
    ) {
        Row(Modifier.padding(16.dp)) {
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun textoErrorAuth(error: ErrorAuth): String = stringResource(
    when (error) {
        ErrorAuth.CredencialesInvalidas -> R.string.error_auth_credenciales
        ErrorAuth.CorreoEnUso -> R.string.error_auth_correo_en_uso
        ErrorAuth.ContrasenaDebil -> R.string.error_auth_contrasena_debil
        ErrorAuth.CorreoInvalido -> R.string.error_auth_correo_invalido
        ErrorAuth.CuentaDeshabilitada -> R.string.error_auth_cuenta_deshabilitada
        ErrorAuth.DemasiadosIntentos -> R.string.error_auth_demasiados_intentos
        ErrorAuth.SinConexion -> R.string.error_auth_sin_conexion
        ErrorAuth.Otro -> R.string.error_auth_otro
    }
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PantallaAccesoPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        PantallaAcceso(
            uiState = AccesoUiState(correo = "ana@prueba.com", errorAuth = ErrorAuth.CredencialesInvalidas),
            motivo = MotivoSinSesion.CerroSesion,
            onCorreo = {},
            onContrasena = {},
            onEnviar = {},
            onCambiarModo = {}
        )
    }
}

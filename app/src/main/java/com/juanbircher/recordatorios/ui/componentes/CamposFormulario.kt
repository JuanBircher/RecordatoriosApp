package com.juanbircher.recordatorios.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Campo de texto controlado: muestra [valor] y avisa cada cambio, pero no guarda estado propio.
 * Un error se comunica con color, ícono, texto y semántica para lectores de pantalla.
 */
@Composable
fun CampoTexto(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    mensajeError: String? = null,
    ayuda: String? = null,
    habilitado: Boolean = true,
    unaLinea: Boolean = true
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        enabled = habilitado,
        singleLine = unaLinea,
        minLines = if (unaLinea) 1 else 3,
        isError = mensajeError != null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        trailingIcon = mensajeError?.let {
            { Icon(Icons.Filled.Warning, contentDescription = null) }
        },
        supportingText = (mensajeError ?: ayuda)?.let { texto ->
            { Text(texto) }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (mensajeError != null) error(mensajeError) }
    )
}

/** Selección única entre [opciones]. La opción elegida se distingue también por el ícono, no solo por el color. */
@Composable
fun <T> SelectorOpciones(
    etiqueta: String,
    opciones: List<T>,
    seleccionada: T?,
    textoDe: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    mensajeError: String? = null,
    habilitado: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = etiqueta, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier
                .selectableGroup()
                .semantics { if (mensajeError != null) error(mensajeError) },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opciones.forEach { opcion ->
                val elegida = opcion == seleccionada
                FilterChip(
                    selected = elegida,
                    onClick = { onSeleccion(opcion) },
                    enabled = habilitado,
                    label = { Text(textoDe(opcion)) },
                    leadingIcon = if (elegida) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
        mensajeError?.let { MensajeErrorCampo(it) }
    }
}

@Composable
private fun MensajeErrorCampo(mensaje: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

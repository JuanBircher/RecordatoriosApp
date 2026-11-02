package com.juanbircher.recordatorios.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme

enum class TipoMensaje { Carga, Vacio, Error }

/** Acción que acompaña a un mensaje. El texto y el clic van juntos: no puede faltar uno de los dos. */
data class AccionMensaje(val texto: String, val onClick: () -> Unit)

/** Comunica carga, vacío o error ocupando toda la pantalla, con una acción opcional. */
@Composable
fun MensajeDeEstado(
    tipo: TipoMensaje,
    mensaje: String,
    modifier: Modifier = Modifier,
    accion: AccionMensaje? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (tipo) {
                TipoMensaje.Carga -> CircularProgressIndicator()
                TipoMensaje.Vacio -> Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                TipoMensaje.Error -> Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(text = mensaje, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            accion?.let { Button(onClick = it.onClick) { Text(it.texto) } }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MensajeDeEstadoPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        MensajeDeEstado(
            tipo = TipoMensaje.Error,
            mensaje = "No se pudieron cargar los recordatorios.",
            accion = AccionMensaje("Reintentar") {}
        )
    }
}

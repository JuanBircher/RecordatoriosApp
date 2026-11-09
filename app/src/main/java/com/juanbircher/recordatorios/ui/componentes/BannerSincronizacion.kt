package com.juanbircher.recordatorios.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.ResumenSincronizacion
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.data.sync.EstadoSync
import com.juanbircher.recordatorios.ui.listado.InfoSincronizacion
import com.juanbircher.recordatorios.ui.textoCausa
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme

/**
 * Franja que informa qué pasa con la sincronización: en curso, fallida (con el motivo), con errores de envío
 * o con pendientes. No aparece cuando todo está al día. El aviso es recuperable: los datos locales siguen
 * a salvo y hay una acción de reintento.
 */
@Composable
fun BannerSincronizacion(
    info: InfoSincronizacion,
    onSincronizar: () -> Unit,
    onReintentarFallidos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (estado, resumen) = info
    val mensaje: String
    val esProblema: Boolean
    var accion = true
    when {
        estado is EstadoSync.Sincronizando -> {
            mensaje = stringResource(R.string.sync_sincronizando); esProblema = false; accion = false
        }
        estado is EstadoSync.Fallo -> {
            mensaje = stringResource(R.string.sync_fallo, textoCausa(estado.causa)); esProblema = true
        }
        resumen.conError > 0 -> {
            mensaje = pluralStringResource(R.plurals.sync_errores, resumen.conError, resumen.conError); esProblema = true
        }
        resumen.pendientes > 0 -> {
            mensaje = pluralStringResource(R.plurals.sync_pendientes, resumen.pendientes, resumen.pendientes); esProblema = false
        }
        else -> return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = if (esProblema) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (esProblema) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (estado is EstadoSync.Sincronizando) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(text = mensaje, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (accion) {
                TextButton(onClick = if (resumen.conError > 0) onReintentarFallidos else onSincronizar) {
                    Text(stringResource(R.string.sync_reintentar))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerSincronizacionPreview() {
    RecordatoriosTheme(dynamicColor = false) {
        BannerSincronizacion(
            info = InfoSincronizacion(EstadoSync.Fallo(CausaFallo.SinConexion), ResumenSincronizacion(pendientes = 2)),
            onSincronizar = {},
            onReintentarFallidos = {}
        )
    }
}

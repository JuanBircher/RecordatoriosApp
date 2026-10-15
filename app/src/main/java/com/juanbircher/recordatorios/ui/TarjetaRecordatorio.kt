package com.juanbircher.recordatorios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.domain.EstadoRecordatorio
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val formatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Tarjeta de un recordatorio: título, categoría, prioridad y estado; los opcionales solo si existen. */
@Composable
fun TarjetaRecordatorio(recordatorio: Recordatorio, modifier: Modifier = Modifier) {
    val activo = recordatorio.estado is EstadoRecordatorio.Pendiente

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = recordatorio.titulo,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (activo) null else TextDecoration.LineThrough
            )

            recordatorio.descripcion?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Etiqueta(
                    texto = recordatorio.categoria.nombre,
                    fondo = colorDe(recordatorio.categoria),
                    textoColor = Color.White
                )
                Etiqueta(
                    texto = stringResource(R.string.prioridad_etiqueta, recordatorio.prioridad.etiqueta),
                    fondo = colorPrioridad(recordatorio.prioridad),
                    textoColor = MaterialTheme.colorScheme.surface
                )
            }

            Text(
                text = textoEstado(recordatorio.estado),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            recordatorio.fechaLimite?.let {
                Text(
                    text = stringResource(R.string.vence_el, it.format(formatoFecha)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            recordatorio.ubicacion?.let {
                Text(text = stringResource(R.string.ubicacion, it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String, fondo: Color, textoColor: Color) {
    Surface(color = fondo, shape = RoundedCornerShape(50)) {
        Text(
            text = texto,
            color = textoColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun colorPrioridad(prioridad: Prioridad): Color = when (prioridad) {
    Prioridad.Alta -> MaterialTheme.colorScheme.error
    Prioridad.Media -> MaterialTheme.colorScheme.tertiary
    Prioridad.Baja -> MaterialTheme.colorScheme.outline
}

@Composable
private fun textoEstado(estado: EstadoRecordatorio): String = when (estado) {
    EstadoRecordatorio.Pendiente -> stringResource(R.string.estado_pendiente)
    is EstadoRecordatorio.Completado ->
        stringResource(R.string.estado_completado, estado.completadoEl.format(formatoFecha))
    is EstadoRecordatorio.Cancelado ->
        estado.motivo?.let { stringResource(R.string.estado_cancelado_motivo, it) }
            ?: stringResource(R.string.estado_cancelado)
}

@Preview(showBackground = true)
@Composable
private fun TarjetaRecordatorioPreview() {
    RecordatoriosTheme {
        TarjetaRecordatorio(
            Recordatorio(
                id = 1,
                titulo = "Entregar Actividad 1",
                categoria = DatosSimulados.estudio,
                prioridad = Prioridad.Alta,
                creadoEl = LocalDate.of(2026, 10, 12),
                descripcion = "PDF con capturas y enlace al repositorio",
                fechaLimite = LocalDate.of(2026, 10, 17)
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

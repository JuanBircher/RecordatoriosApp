package com.juanbircher.recordatorios.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.domain.EstadoSincronizacion

/** Textos de la sincronización: viven en la capa de interfaz, la de datos solo conoce los tipos. */
@Composable
fun textoSincronizacion(estado: EstadoSincronizacion): String = when (estado) {
    EstadoSincronizacion.Sincronizado -> stringResource(R.string.sync_estado_sincronizado)
    EstadoSincronizacion.Pendiente -> stringResource(R.string.sync_estado_pendiente)
    EstadoSincronizacion.Error -> stringResource(R.string.sync_estado_error)
}

@Composable
fun textoCausa(causa: CausaFallo): String = when (causa) {
    CausaFallo.SinConexion -> stringResource(R.string.causa_sin_conexion)
    CausaFallo.Timeout -> stringResource(R.string.causa_timeout)
    CausaFallo.ErrorServidor -> stringResource(R.string.causa_error_servidor)
    CausaFallo.SolicitudInvalida -> stringResource(R.string.causa_solicitud_invalida)
    CausaFallo.RespuestaInvalida -> stringResource(R.string.causa_respuesta_invalida)
    CausaFallo.NoAutorizado -> stringResource(R.string.causa_no_autorizado)
}

package com.juanbircher.recordatorios.data.remote

/*
 * Mapeo entre el DTO y un documento de Firestore, escrito a mano con un Map en lugar de dejar que el SDK
 * lo haga por reflexión (toObject). Así el formato del documento queda explícito en un solo lugar, coincide
 * con lo que validan las reglas de seguridad, y R8 puede optimizar el release sin reglas especiales.
 */

/** Campo que agrega la fuente remota con la hora del servidor; no forma parte del DTO. */
const val CAMPO_ACTUALIZADO_EL = "actualizadoEl"

/**
 * Documento que se escribe en Firestore. No incluye la clave (es el id del documento) y omite los campos
 * vacíos, para no guardar valores `null` que las reglas tendrían que contemplar.
 */
fun RecordatorioDto.aDocumentoFirestore(): Map<String, Any> = buildMap {
    titulo?.let { put("titulo", it) }
    descripcion?.let { put("descripcion", it) }
    categoriaId?.let { put("categoriaId", it) }
    prioridad?.let { put("prioridad", it) }
    estado?.let { put("estado", it) }
    completadoEl?.let { put("completadoEl", it) }
    motivoCancelacion?.let { put("motivoCancelacion", it) }
    creadoEl?.let { put("creadoEl", it) }
    fechaLimite?.let { put("fechaLimite", it) }
    ubicacion?.let { put("ubicacion", it) }
}

/**
 * DTO a partir de un documento leído de Firestore; [idDocumento] es la clave del recordatorio. Un campo
 * ausente o de otro tipo queda vacío: la validación del mapeo a entidad decide después si se descarta.
 */
fun dtoDesdeDocumentoFirestore(idDocumento: String, datos: Map<String, Any?>): RecordatorioDto {
    fun texto(campo: String) = datos[campo] as? String
    return RecordatorioDto(
        claveCliente = idDocumento,
        titulo = texto("titulo"),
        descripcion = texto("descripcion"),
        categoriaId = texto("categoriaId"),
        prioridad = texto("prioridad"),
        estado = texto("estado"),
        completadoEl = texto("completadoEl"),
        motivoCancelacion = texto("motivoCancelacion"),
        creadoEl = texto("creadoEl"),
        fechaLimite = texto("fechaLimite"),
        ubicacion = texto("ubicacion")
    )
}

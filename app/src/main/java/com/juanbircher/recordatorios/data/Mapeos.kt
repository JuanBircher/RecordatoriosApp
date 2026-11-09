package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.data.remote.RecordatorioDto
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.EstadoRecordatorio
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.validation.ResultadoValidacion
import com.juanbircher.recordatorios.validation.validar
import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * Mapeos explícitos entre las tres representaciones de un recordatorio:
 *   DTO (contrato del servicio)  <->  Entity (esquema de Room)  <->  Recordatorio (dominio / interfaz)
 * Ninguna capa usa el modelo de otra: todo cambio de forma pasa por estas funciones.
 */

// ---- Entity -> dominio ------------------------------------------------------------------------

/** Convierte la fila de Room en el modelo que ve el resto de la app. */
fun RecordatorioEntity.aDominio(): Recordatorio = Recordatorio(
    id = id,
    titulo = titulo,
    categoria = Categoria(categoriaId, categoriaNombre),
    prioridad = Prioridad.desdeNivel(prioridad) ?: Prioridad.Media,
    creadoEl = LocalDate.parse(creadoEl),
    estado = when (estadoTipo) {
        RecordatorioEntity.COMPLETADO -> EstadoRecordatorio.Completado(
            completadoEl = completadoEl?.let(LocalDate::parse) ?: LocalDate.parse(creadoEl)
        )
        RecordatorioEntity.CANCELADO -> EstadoRecordatorio.Cancelado(motivoCancelacion)
        else -> EstadoRecordatorio.Pendiente
    },
    descripcion = descripcion,
    fechaLimite = fechaLimite?.let(LocalDate::parse),
    ubicacion = ubicacion,
    sincronizacion = EstadoSincronizacion.entries.firstOrNull { it.name == sincronizacion }
        ?: EstadoSincronizacion.Pendiente
)

// ---- Dominio -> entity ------------------------------------------------------------------------

/** Fila nueva para un recordatorio creado en el dispositivo: queda pendiente de enviarse. */
fun Recordatorio.aEntidadNueva(claveCliente: String): RecordatorioEntity = RecordatorioEntity(
    claveCliente = claveCliente,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoria.id,
    categoriaNombre = categoria.nombre,
    prioridad = prioridad.nivel,
    estadoTipo = estado.codigo(),
    completadoEl = (estado as? EstadoRecordatorio.Completado)?.completadoEl?.toString(),
    motivoCancelacion = (estado as? EstadoRecordatorio.Cancelado)?.motivo,
    creadoEl = creadoEl.toString(),
    fechaLimite = fechaLimite?.toString(),
    ubicacion = ubicacion,
    sincronizacion = EstadoSincronizacion.Pendiente.name
)

/**
 * Aplica los cambios de [recordatorio] sobre una fila existente, conservando su identidad
 * (`id`, `claveCliente`, `idRemoto`). El cambio queda pendiente de enviarse.
 */
fun RecordatorioEntity.conCambiosDe(recordatorio: Recordatorio): RecordatorioEntity =
    recordatorio.aEntidadNueva(claveCliente).copy(id = id, idRemoto = idRemoto)

// ---- Entity -> DTO ----------------------------------------------------------------------------

/** Lo que se envía al servicio. */
fun RecordatorioEntity.aDto(): RecordatorioDto = RecordatorioDto(
    id = idRemoto,
    claveCliente = claveCliente,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoriaId,
    prioridad = Prioridad.desdeNivel(prioridad)?.codigo(),
    estado = estadoTipo,
    completadoEl = completadoEl,
    motivoCancelacion = motivoCancelacion,
    creadoEl = creadoEl,
    fechaLimite = fechaLimite,
    ubicacion = ubicacion
)

// ---- DTO -> entity ----------------------------------------------------------------------------

/**
 * Transforma un DTO recibido en una fila sincronizada. Devuelve `null` si el DTO no es válido (faltan
 * campos, fechas ilegibles, categoría desconocida o no cumple las reglas del dominio): un dato inválido
 * del servicio se descarta y nunca llega a Room.
 *
 * Si [existente] no es `null`, la fila resultante conserva su `id` local y su `claveCliente`.
 */
fun RecordatorioDto.aEntidad(
    categorias: Set<Categoria>,
    existente: RecordatorioEntity? = null
): RecordatorioEntity? {
    val idRemoto = id ?: return null
    val clave = claveCliente?.takeIf { it.isNotBlank() } ?: return null
    val categoria = categorias.firstOrNull { it.id == categoriaId } ?: return null
    val prioridadDominio = prioridad?.let { Prioridad.desdeCodigo(it) } ?: return null
    val estadoDominio = estadoDesdeDto() ?: return null

    val dominio = try {
        Recordatorio(
            id = existente?.id ?: 0,
            titulo = titulo ?: return null,
            categoria = categoria,
            prioridad = prioridadDominio,
            creadoEl = LocalDate.parse(creadoEl ?: return null),
            estado = estadoDominio,
            descripcion = descripcion,
            fechaLimite = fechaLimite?.let(LocalDate::parse),
            ubicacion = ubicacion
        )
    } catch (_: DateTimeParseException) {
        return null
    }
    if (validar(dominio, categorias) !is ResultadoValidacion.Valido) return null

    return dominio.aEntidadNueva(clave).copy(
        id = existente?.id ?: 0,
        idRemoto = idRemoto,
        sincronizacion = EstadoSincronizacion.Sincronizado.name,
        intentos = 0
    )
}

private fun RecordatorioDto.estadoDesdeDto(): EstadoRecordatorio? {
    return try {
        when (estado) {
            RecordatorioEntity.PENDIENTE -> EstadoRecordatorio.Pendiente
            RecordatorioEntity.COMPLETADO ->
                EstadoRecordatorio.Completado(LocalDate.parse(completadoEl ?: return null))
            RecordatorioEntity.CANCELADO -> EstadoRecordatorio.Cancelado(motivoCancelacion)
            else -> null
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun EstadoRecordatorio.codigo(): String = when (this) {
    EstadoRecordatorio.Pendiente -> RecordatorioEntity.PENDIENTE
    is EstadoRecordatorio.Completado -> RecordatorioEntity.COMPLETADO
    is EstadoRecordatorio.Cancelado -> RecordatorioEntity.CANCELADO
}

private fun Prioridad.codigo(): String = when (this) {
    Prioridad.Alta -> "ALTA"
    Prioridad.Media -> "MEDIA"
    Prioridad.Baja -> "BAJA"
}

private fun Prioridad.Companion.desdeCodigo(codigo: String): Prioridad? =
    todas.firstOrNull { it.codigo() == codigo }

private fun Prioridad.Companion.desdeNivel(nivel: Int): Prioridad? =
    todas.firstOrNull { it.nivel == nivel }

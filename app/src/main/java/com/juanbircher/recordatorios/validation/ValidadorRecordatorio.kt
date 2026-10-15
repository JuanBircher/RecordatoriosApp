package com.juanbircher.recordatorios.validation

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Recordatorio

/** Resultado de validar un recordatorio: válido, o inválido con la lista de todos los problemas. */
sealed interface ResultadoValidacion {
    data object Valido : ResultadoValidacion
    data class Invalido(val errores: List<ErrorValidacion>) : ResultadoValidacion
}

/** Cada regla incumplida, con el dato que hace falta para explicarla. */
sealed interface ErrorValidacion {
    data object TituloVacio : ErrorValidacion
    data class TituloLongitudInvalida(val actual: Int) : ErrorValidacion
    data class DescripcionDemasiadoLarga(val actual: Int) : ErrorValidacion
    data object FechaLimiteAnteriorACreacion : ErrorValidacion
    data class CategoriaInexistente(val id: String) : ErrorValidacion
}

object ReglasRecordatorio {
    const val TITULO_MIN = 3
    const val TITULO_MAX = 60
    const val DESCRIPCION_MAX = 200
}

/**
 * Aplica las reglas del producto. No depende de Android: se prueba con JUnit sin emulador.
 * Devuelve todos los errores juntos, no solo el primero.
 */
fun validar(recordatorio: Recordatorio, categoriasValidas: Set<Categoria>): ResultadoValidacion {
    val errores = listOfNotNull(
        validarTitulo(recordatorio.titulo),
        validarDescripcion(recordatorio.descripcion),
        validarFechaLimite(recordatorio),
        validarCategoria(recordatorio.categoria, categoriasValidas)
    )
    return if (errores.isEmpty()) ResultadoValidacion.Valido else ResultadoValidacion.Invalido(errores)
}

private fun validarTitulo(titulo: String): ErrorValidacion? {
    val limpio = titulo.trim()
    return when {
        limpio.isEmpty() -> ErrorValidacion.TituloVacio
        limpio.length !in ReglasRecordatorio.TITULO_MIN..ReglasRecordatorio.TITULO_MAX ->
            ErrorValidacion.TituloLongitudInvalida(limpio.length)
        else -> null
    }
}

/** La descripción es opcional: si es null no hay nada que validar. */
private fun validarDescripcion(descripcion: String?): ErrorValidacion? {
    val largo = descripcion?.length ?: return null
    return if (largo > ReglasRecordatorio.DESCRIPCION_MAX) ErrorValidacion.DescripcionDemasiadoLarga(largo) else null
}

private fun validarFechaLimite(recordatorio: Recordatorio): ErrorValidacion? {
    val limite = recordatorio.fechaLimite ?: return null
    return if (limite.isBefore(recordatorio.creadoEl)) ErrorValidacion.FechaLimiteAnteriorACreacion else null
}

private fun validarCategoria(categoria: Categoria, categoriasValidas: Set<Categoria>): ErrorValidacion? =
    if (categoria in categoriasValidas) null else ErrorValidacion.CategoriaInexistente(categoria.id)

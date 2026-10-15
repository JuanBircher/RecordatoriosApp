package com.juanbircher.recordatorios.domain

/**
 * Categoría con la que se agrupan los recordatorios (Trabajo, Salud, Estudio...).
 *
 * Es una data class: dos categorías con el mismo id y nombre son iguales (==), por eso un Set
 * puede descartar las repetidas.
 */
data class Categoria(
    val id: String,
    val nombre: String
)

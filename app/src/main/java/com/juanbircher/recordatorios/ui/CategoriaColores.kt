package com.juanbircher.recordatorios.ui

import androidx.compose.ui.graphics.Color
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.domain.Categoria

/**
 * Color con el que se muestra cada categoría. Vive en `ui` y no en `domain` porque Color es de
 * Compose: el modelo de dominio no debe depender de la interfaz.
 */
val coloresPorCategoria: Map<Categoria, Color> = mapOf(
    DatosSimulados.trabajo to Color(0xFF1565C0),
    DatosSimulados.personal to Color(0xFF6A1B9A),
    DatosSimulados.salud to Color(0xFF2E7D32),
    DatosSimulados.estudio to Color(0xFFE65100)
)

/** Color neutro para una categoría que no figura en el Map. */
val colorCategoriaDesconocida = Color(0xFF616161)

/** Busca en el Map; como la clave podría no existir, el resultado es nullable y se resuelve con Elvis. */
fun colorDe(categoria: Categoria): Color = coloresPorCategoria[categoria] ?: colorCategoriaDesconocida

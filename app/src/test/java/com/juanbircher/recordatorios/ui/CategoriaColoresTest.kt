package com.juanbircher.recordatorios.ui

import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.domain.Categoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriaColoresTest {

    @Test
    fun `toda categoria del catalogo tiene color en el Map`() {
        assertTrue(DatosSimulados.categorias.all { it in coloresPorCategoria })
    }

    @Test
    fun `una categoria fuera del Map usa el color neutro`() {
        assertEquals(colorCategoriaDesconocida, colorDe(Categoria("ocio", "Ocio")))
        assertEquals(coloresPorCategoria.getValue(DatosSimulados.salud), colorDe(DatosSimulados.salud))
    }
}

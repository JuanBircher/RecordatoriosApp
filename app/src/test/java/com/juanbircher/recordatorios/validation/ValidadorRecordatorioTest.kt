package com.juanbircher.recordatorios.validation

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.validation.ErrorValidacion.CategoriaInexistente
import com.juanbircher.recordatorios.validation.ErrorValidacion.DescripcionDemasiadoLarga
import com.juanbircher.recordatorios.validation.ErrorValidacion.FechaLimiteAnteriorACreacion
import com.juanbircher.recordatorios.validation.ErrorValidacion.TituloLongitudInvalida
import com.juanbircher.recordatorios.validation.ErrorValidacion.TituloVacio
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ValidadorRecordatorioTest {

    private val trabajo = Categoria("trabajo", "Trabajo")
    private val catalogo = setOf(trabajo)
    private val hoy = LocalDate.of(2026, 10, 15)

    private fun recordatorio(
        titulo: String = "Entregar informe",
        descripcion: String? = null,
        fechaLimite: LocalDate? = null,
        categoria: Categoria = trabajo
    ) = Recordatorio(
        id = 1,
        titulo = titulo,
        categoria = categoria,
        prioridad = Prioridad.Media,
        creadoEl = hoy,
        descripcion = descripcion,
        fechaLimite = fechaLimite
    )

    private fun errores(r: Recordatorio): List<ErrorValidacion> =
        when (val resultado = validar(r, catalogo)) {
            ResultadoValidacion.Valido -> emptyList()
            is ResultadoValidacion.Invalido -> resultado.errores
        }

    @Test
    fun `un recordatorio completo y correcto es valido`() {
        val r = recordatorio(descripcion = "Para el viernes", fechaLimite = hoy.plusDays(2))
        assertEquals(ResultadoValidacion.Valido, validar(r, catalogo))
    }

    @Test
    fun `los datos opcionales pueden faltar`() {
        assertEquals(ResultadoValidacion.Valido, validar(recordatorio(), catalogo))
    }

    @Test
    fun `titulo vacio o solo espacios es invalido`() {
        assertEquals(listOf(TituloVacio), errores(recordatorio(titulo = "")))
        assertEquals(listOf(TituloVacio), errores(recordatorio(titulo = "    ")))
    }

    @Test
    fun `titulo respeta los bordes de 3 y 60 caracteres`() {
        assertEquals(listOf(TituloLongitudInvalida(2)), errores(recordatorio(titulo = "ab")))
        assertEquals(emptyList<ErrorValidacion>(), errores(recordatorio(titulo = "abc")))
        assertEquals(emptyList<ErrorValidacion>(), errores(recordatorio(titulo = "a".repeat(60))))
        assertEquals(listOf(TituloLongitudInvalida(61)), errores(recordatorio(titulo = "a".repeat(61))))
    }

    @Test
    fun `los espacios de los bordes no cuentan para la longitud del titulo`() {
        assertEquals(listOf(TituloLongitudInvalida(2)), errores(recordatorio(titulo = "  ab  ")))
    }

    @Test
    fun `descripcion de hasta 200 caracteres es valida y de 201 no`() {
        assertEquals(emptyList<ErrorValidacion>(), errores(recordatorio(descripcion = "d".repeat(200))))
        assertEquals(
            listOf(DescripcionDemasiadoLarga(201)),
            errores(recordatorio(descripcion = "d".repeat(201)))
        )
    }

    @Test
    fun `fecha limite anterior a la creacion es invalida y el mismo dia es valido`() {
        assertEquals(listOf(FechaLimiteAnteriorACreacion), errores(recordatorio(fechaLimite = hoy.minusDays(1))))
        assertEquals(emptyList<ErrorValidacion>(), errores(recordatorio(fechaLimite = hoy)))
    }

    @Test
    fun `la categoria tiene que existir en el catalogo`() {
        val r = recordatorio(categoria = Categoria("ocio", "Ocio"))
        assertEquals(listOf(CategoriaInexistente("ocio")), errores(r))
    }

    @Test
    fun `informa todos los errores juntos`() {
        val r = recordatorio(
            titulo = " ",
            descripcion = "d".repeat(250),
            fechaLimite = hoy.minusDays(3),
            categoria = Categoria("ocio", "Ocio")
        )
        assertEquals(
            listOf(
                TituloVacio,
                DescripcionDemasiadoLarga(250),
                FechaLimiteAnteriorACreacion,
                CategoriaInexistente("ocio")
            ),
            errores(r)
        )
    }
}

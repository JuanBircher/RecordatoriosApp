package com.juanbircher.recordatorios.data.remote

import com.juanbircher.recordatorios.data.dtoDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MapeosFirestoreTest {

    private val completo = dtoDePrueba(clave = "c-1").copy(
        descripcion = "Con IVA",
        estado = "COMPLETADO",
        completadoEl = "2026-11-10",
        fechaLimite = "2026-11-20",
        ubicacion = "Oficina"
    )

    @Test
    fun `el documento no lleva la clave porque el id del documento ya la identifica`() {
        assertFalse("claveCliente" in completo.aDocumentoFirestore())
    }

    @Test
    fun `los campos vacios no se escriben`() {
        val documento = dtoDePrueba().aDocumentoFirestore()

        assertEquals(setOf("titulo", "categoriaId", "prioridad", "estado", "creadoEl"), documento.keys)
    }

    @Test
    fun `ida y vuelta conserva todo el contenido y toma la clave del id del documento`() {
        val vuelta = dtoDesdeDocumentoFirestore("c-1", completo.aDocumentoFirestore())

        assertEquals(completo, vuelta)
    }

    @Test
    fun `un campo de otro tipo o ausente se lee como vacio y despues lo descarta la validacion`() {
        val dto = dtoDesdeDocumentoFirestore("c-2", mapOf("titulo" to 42L, "prioridad" to "ALTA", "extra" to true))

        assertNull(dto.titulo)
        assertNull(dto.estado)
        assertEquals("ALTA", dto.prioridad)
        assertEquals("c-2", dto.claveCliente)
    }
}

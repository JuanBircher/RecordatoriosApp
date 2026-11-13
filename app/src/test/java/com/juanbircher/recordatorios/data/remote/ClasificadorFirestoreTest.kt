package com.juanbircher.recordatorios.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClasificadorFirestoreTest {

    @Test
    fun `permiso denegado o sin autenticar es NoAutorizado y no se reintenta solo`() {
        listOf("PERMISSION_DENIED", "UNAUTHENTICATED").forEach { codigo ->
            val resultado = clasificarCodigoFirestore(codigo)
            assertEquals(codigo, ResultadoRemoto.NoAutorizado, resultado)
            assertFalse(resultado.esTransitorio)
        }
    }

    @Test
    fun `servicio no disponible o sobrecargado es un error transitorio del servidor`() {
        listOf("UNAVAILABLE", "RESOURCE_EXHAUSTED", "ABORTED", "INTERNAL", "UNKNOWN", "CANCELLED").forEach { codigo ->
            val resultado = clasificarCodigoFirestore(codigo)
            assertEquals(codigo, ResultadoRemoto.ErrorServidor(codigo), resultado)
            assertTrue(codigo, resultado.esTransitorio)
        }
    }

    @Test
    fun `plazo vencido es Timeout`() {
        assertEquals(ResultadoRemoto.Timeout, clasificarCodigoFirestore("DEADLINE_EXCEEDED"))
    }

    @Test
    fun `argumento invalido es una solicitud rechazada que no se arregla reintentando`() {
        listOf("INVALID_ARGUMENT", "FAILED_PRECONDITION", "OUT_OF_RANGE").forEach { codigo ->
            val resultado = clasificarCodigoFirestore(codigo)
            assertEquals(codigo, ResultadoRemoto.SolicitudInvalida(codigo), resultado)
            assertFalse(codigo, resultado.esTransitorio)
        }
    }

    @Test
    fun `un codigo desconocido se trata como falla transitoria del servicio`() {
        assertTrue(clasificarCodigoFirestore("CODIGO_NUEVO").esTransitorio)
    }

    @Test
    fun `NoAutorizado tiene su propia causa de fallo`() {
        assertEquals(CausaFallo.NoAutorizado, ResultadoRemoto.NoAutorizado.causaDeFallo())
    }
}

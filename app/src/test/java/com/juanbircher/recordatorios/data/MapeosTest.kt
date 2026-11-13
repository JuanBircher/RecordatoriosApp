package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.domain.EstadoRecordatorio
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MapeosTest {

    private val categorias = DatosSimulados.categorias
    private val hoy = LocalDate.of(2026, 11, 9)

    private val completo = Recordatorio(
        id = 3,
        titulo = "Enviar presupuesto",
        categoria = DatosSimulados.trabajo,
        prioridad = Prioridad.Alta,
        creadoEl = hoy.minusDays(2),
        estado = EstadoRecordatorio.Completado(hoy.minusDays(1)),
        descripcion = "Con IVA incluido",
        fechaLimite = hoy.plusDays(3),
        ubicacion = "Oficina"
    )

    @Test
    fun `dominio a entidad y de vuelta conserva todos los campos`() {
        val entidad = completo.aEntidadNueva("clave-x", ANA)

        assertEquals(completo.copy(id = 0, sincronizacion = EstadoSincronizacion.Pendiente), entidad.aDominio().copy(id = 0))
    }

    @Test
    fun `cada estado se guarda con su codigo y sus datos`() {
        val pendiente = completo.copy(estado = EstadoRecordatorio.Pendiente).aEntidadNueva("k", ANA)
        val cancelado = completo.copy(estado = EstadoRecordatorio.Cancelado("Se pospuso")).aEntidadNueva("k", ANA)

        assertEquals(RecordatorioEntity.PENDIENTE, pendiente.estadoTipo)
        assertEquals(EstadoRecordatorio.Cancelado("Se pospuso"), cancelado.aDominio().estado)
        assertEquals(RecordatorioEntity.CANCELADO, cancelado.estadoTipo)
    }

    @Test
    fun `un registro nuevo pertenece a la cuenta, queda pendiente y todavia no existe en el servicio`() {
        val entidad = completo.aEntidadNueva("k", ANA)

        assertEquals(EstadoSincronizacion.Pendiente.name, entidad.sincronizacion)
        assertFalse(entidad.enRemoto)
        assertEquals(ANA, entidad.uid)
        assertEquals("k", entidad.claveCliente)
    }

    @Test
    fun `aplicar cambios conserva la identidad del registro y lo deja pendiente`() {
        val original = completo.aEntidadNueva("k", ANA).copy(
            id = 9,
            enRemoto = true,
            sincronizacion = EstadoSincronizacion.Sincronizado.name
        )

        val cambiado = original.conCambiosDe(completo.copy(titulo = "Otro título"))

        assertEquals(9L, cambiado.id)
        assertTrue(cambiado.enRemoto)
        assertEquals(ANA, cambiado.uid)
        assertEquals("k", cambiado.claveCliente)
        assertEquals("Otro título", cambiado.titulo)
        assertEquals(EstadoSincronizacion.Pendiente.name, cambiado.sincronizacion)
    }

    @Test
    fun `la entidad viaja al servicio como dto y vuelve igual`() {
        val entidad = completo.aEntidadNueva("k", ANA)

        val vuelta = entidad.aDto().aEntidad(ANA, categorias)!!

        assertEquals(entidad.copy(sincronizacion = EstadoSincronizacion.Sincronizado.name, enRemoto = true), vuelta)
    }

    @Test
    fun `un dto recibido conserva el id local del registro existente`() {
        val existente = completo.aEntidadNueva("remota-1", ANA).copy(id = 8)

        val actualizada = dtoDePrueba(clave = "remota-1").aEntidad(ANA, categorias, existente)!!

        assertEquals(8L, actualizada.id)
    }

    @Test
    fun `un dto sin clave o sin titulo se descarta`() {
        assertNull(dtoDePrueba().copy(claveCliente = null).aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(claveCliente = " ").aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(titulo = null).aEntidad(ANA, categorias))
    }

    @Test
    fun `un dto con categoria desconocida, prioridad o estado invalidos se descarta`() {
        assertNull(dtoDePrueba(categoriaId = "inexistente").aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(prioridad = "URGENTE").aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(estado = "ARCHIVADO").aEntidad(ANA, categorias))
    }

    @Test
    fun `un dto con fechas ilegibles o completado sin fecha se descarta`() {
        assertNull(dtoDePrueba().copy(creadoEl = "ayer").aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(fechaLimite = "32/13/2026").aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(estado = RecordatorioEntity.COMPLETADO, completadoEl = null).aEntidad(ANA, categorias))
    }

    @Test
    fun `un dto que rompe las reglas del dominio se descarta`() {
        assertNull(dtoDePrueba(titulo = "abc").aEntidad(ANA, categorias))
        assertNull(dtoDePrueba().copy(creadoEl = "2026-11-08", fechaLimite = "2026-11-01").aEntidad(ANA, categorias))
    }

    @Test
    fun `un dto valido se guarda como sincronizado`() {
        val entidad = dtoDePrueba().aEntidad(ANA, categorias)

        assertNotNull(entidad)
        assertEquals(EstadoSincronizacion.Sincronizado.name, entidad!!.sincronizacion)
        assertTrue(entidad.enRemoto)
        assertEquals(ANA, entidad.uid)
    }
}

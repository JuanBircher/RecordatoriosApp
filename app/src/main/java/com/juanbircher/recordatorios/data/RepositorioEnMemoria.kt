package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.updateAndGet
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fuente de datos en memoria: se pierde al cerrar la app. Parte de los datos simulados, descartando
 * los inválidos, y usa [latenciaMs] para simular una operación asincrónica.
 *
 * Las fallas simuladas ocurren una sola vez, así el reintento posterior funciona.
 */
class RepositorioEnMemoria(
    private val latenciaMs: Long = 1_000,
    hoy: LocalDate = LocalDate.now()
) : RecordatoriosRepository, SimuladorEscenarios {

    private val iniciales: List<Recordatorio> =
        cargarRecordatorios(DatosSimulados.recordatorios(hoy), DatosSimulados.categorias).validos

    private val recordatorios = MutableStateFlow(iniciales)
    private val fallarProximaCarga = AtomicBoolean(false)
    private val fallarProximoGuardado = AtomicBoolean(false)

    override val categorias: Set<Categoria> = DatosSimulados.categorias

    override fun observarRecordatorios(): Flow<List<Recordatorio>> = flow {
        delay(latenciaMs)
        if (fallarProximaCarga.getAndSet(false)) throw IOException("Falla simulada al cargar")
        emitAll(recordatorios)
    }

    override suspend fun obtenerRecordatorio(id: Long): Recordatorio? {
        delay(latenciaMs)
        return recordatorios.value.find { it.id == id }
    }

    override suspend fun guardarRecordatorio(recordatorio: Recordatorio): Recordatorio {
        delay(latenciaMs)
        if (fallarProximoGuardado.getAndSet(false)) throw IOException("Falla simulada al guardar")
        return recordatorios
            .updateAndGet { lista -> lista + recordatorio.copy(id = (lista.maxOfOrNull { it.id } ?: 0L) + 1) }
            .last()
    }

    override fun aplicar(escenario: EscenarioSimulado) {
        when (escenario) {
            EscenarioSimulado.ConDatos -> reiniciar(iniciales)
            EscenarioSimulado.ListaVacia -> reiniciar(emptyList())
            EscenarioSimulado.ErrorAlCargar -> fallarProximaCarga.set(true)
            EscenarioSimulado.ErrorAlGuardar -> fallarProximoGuardado.set(true)
        }
    }

    private fun reiniciar(lista: List<Recordatorio>) {
        recordatorios.value = lista
        fallarProximaCarga.set(false)
        fallarProximoGuardado.set(false)
    }
}

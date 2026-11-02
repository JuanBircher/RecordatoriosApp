package com.juanbircher.recordatorios.ui

import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.EscenarioSimulado
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.SimuladorEscenarios
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.LocalDate

/** Doble de prueba: demuestra que los ViewModel funcionan con cualquier implementación de la interfaz. */
class RepositorioFalso(
    iniciales: List<Recordatorio> = emptyList()
) : RecordatoriosRepository, SimuladorEscenarios {

    val lista = MutableStateFlow(iniciales)
    val guardados = mutableListOf<Recordatorio>()
    val escenarios = mutableListOf<EscenarioSimulado>()
    var fallaAlObservar = false
    var fallaAlObtener = false
    var fallaAlGuardar = false

    override val categorias: Set<Categoria> = DatosSimulados.categorias

    override fun observarRecordatorios(): Flow<List<Recordatorio>> = flow {
        delay(LATENCIA)
        if (fallaAlObservar) throw IOException("falla de prueba")
        lista.collect { emit(it) }
    }

    override suspend fun obtenerRecordatorio(id: Long): Recordatorio? {
        delay(LATENCIA)
        if (fallaAlObtener) throw IOException("falla de prueba")
        return lista.value.find { it.id == id }
    }

    override suspend fun guardarRecordatorio(recordatorio: Recordatorio): Recordatorio {
        delay(LATENCIA)
        if (fallaAlGuardar) throw IOException("falla de prueba")
        guardados += recordatorio
        return recordatorio.copy(id = 100)
    }

    override fun aplicar(escenario: EscenarioSimulado) {
        escenarios += escenario
    }

    companion object {
        const val LATENCIA = 100L
    }
}

fun recordatorioDePrueba(
    id: Long,
    prioridad: Prioridad = Prioridad.Media
) = Recordatorio(
    id = id,
    titulo = "Recordatorio $id",
    categoria = DatosSimulados.trabajo,
    prioridad = prioridad,
    creadoEl = LocalDate.of(2026, 11, 2)
)

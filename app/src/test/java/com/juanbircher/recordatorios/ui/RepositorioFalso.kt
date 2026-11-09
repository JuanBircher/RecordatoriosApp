package com.juanbircher.recordatorios.ui

import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.ResumenSincronizacion
import com.juanbircher.recordatorios.data.remote.ModoRed
import com.juanbircher.recordatorios.data.remote.SimuladorServidor
import com.juanbircher.recordatorios.data.sync.EstadoSync
import com.juanbircher.recordatorios.data.sync.ResultadoSincronizacion
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.domain.TemaApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.LocalDate

/** Doble de prueba: demuestra que los ViewModel funcionan con cualquier implementación de la interfaz. */
class RepositorioFalso(
    iniciales: List<Recordatorio> = emptyList()
) : RecordatoriosRepository {

    val lista = MutableStateFlow(iniciales)
    val guardados = mutableListOf<Recordatorio>()
    val actualizados = mutableListOf<Recordatorio>()
    val eliminados = mutableListOf<Long>()
    var sincronizaciones = 0
    var reintentos = 0

    var fallaAlObservar = false
    var fallaAlObtener = false
    var fallaAlGuardar = false
    var fallaAlActualizar = false
    var fallaAlEliminar = false
    var fallaAlGuardarPreferencia = false

    val preferenciasActuales = MutableStateFlow(Preferencias())
    val resumen = MutableStateFlow(ResumenSincronizacion())

    override val categorias: Set<Categoria> = DatosSimulados.categorias
    override val preferencias: Flow<Preferencias> = preferenciasActuales
    override val estadoSincronizacion = MutableStateFlow<EstadoSync>(EstadoSync.Inactivo)

    override fun observarResumenSincronizacion(): Flow<ResumenSincronizacion> = resumen

    override fun observarRecordatorios(): Flow<List<Recordatorio>> = flow {
        delay(LATENCIA)
        if (fallaAlObservar) throw IOException("falla de prueba")
        lista.collect { emit(it) }
    }

    override fun observarRecordatorio(id: Long): Flow<Recordatorio?> = flow {
        delay(LATENCIA)
        if (fallaAlObservar) throw IOException("falla de prueba")
        lista.collect { emit(it.find { r -> r.id == id }) }
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

    override suspend fun actualizarRecordatorio(recordatorio: Recordatorio) {
        delay(LATENCIA)
        if (fallaAlActualizar) throw IOException("falla de prueba")
        actualizados += recordatorio
        lista.value = lista.value.map { if (it.id == recordatorio.id) recordatorio else it }
    }

    override suspend fun eliminarRecordatorio(id: Long) {
        delay(LATENCIA)
        if (fallaAlEliminar) throw IOException("falla de prueba")
        eliminados += id
        lista.value = lista.value.filterNot { it.id == id }
    }

    override suspend fun sincronizar(): ResultadoSincronizacion {
        sincronizaciones++
        return ResultadoSincronizacion.Completa(0, 0)
    }

    override suspend fun reintentarFallidos(): ResultadoSincronizacion {
        reintentos++
        return ResultadoSincronizacion.Completa(0, 0)
    }

    override suspend fun actualizarTema(tema: TemaApp) {
        if (fallaAlGuardarPreferencia) throw IOException("falla de prueba")
        preferenciasActuales.value = preferenciasActuales.value.copy(tema = tema)
    }

    override suspend fun actualizarMostrarCompletados(mostrar: Boolean) {
        if (fallaAlGuardarPreferencia) throw IOException("falla de prueba")
        preferenciasActuales.value = preferenciasActuales.value.copy(mostrarCompletados = mostrar)
    }

    override suspend fun actualizarOrden(orden: OrdenListado) {
        if (fallaAlGuardarPreferencia) throw IOException("falla de prueba")
        preferenciasActuales.value = preferenciasActuales.value.copy(orden = orden)
    }

    companion object {
        const val LATENCIA = 100L
    }
}

class SimuladorFalso : SimuladorServidor {
    override val modo = MutableStateFlow(ModoRed.Normal)
    override fun fijar(modo: ModoRed) {
        this.modo.value = modo
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

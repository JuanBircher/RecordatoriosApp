package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.data.local.RecordatorioDao
import com.juanbircher.recordatorios.data.local.RecordatorioEntity
import com.juanbircher.recordatorios.data.remote.RecordatorioDto
import com.juanbircher.recordatorios.data.remote.RecordatoriosRemoteDataSource
import com.juanbircher.recordatorios.data.remote.ResultadoRemoto
import com.juanbircher.recordatorios.data.sync.Conectividad
import com.juanbircher.recordatorios.domain.EstadoSincronizacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** DAO en memoria: permite probar el repositorio y la sincronización sin Android ni SQLite. */
class DaoFalso : RecordatorioDao {
    val filas = MutableStateFlow<List<RecordatorioEntity>>(emptyList())
    private var siguienteId = 1L

    override fun observarTodos(): Flow<List<RecordatorioEntity>> =
        filas.map { lista -> lista.filter { !it.eliminado }.sortedBy { it.id } }

    override fun observarSinCompletados(): Flow<List<RecordatorioEntity>> =
        observarTodos().map { lista -> lista.filter { it.estadoTipo != RecordatorioEntity.COMPLETADO } }

    override fun observarPorId(id: Long): Flow<RecordatorioEntity?> =
        filas.map { lista -> lista.firstOrNull { it.id == id && !it.eliminado } }

    override suspend fun obtenerPorId(id: Long) = filas.value.firstOrNull { it.id == id && !it.eliminado }

    override suspend fun obtenerPorClave(clave: String) = filas.value.firstOrNull { it.claveCliente == clave }

    override suspend fun obtenerPendientes() =
        filas.value.filter { it.sincronizacion == EstadoSincronizacion.Pendiente.name }.sortedBy { it.id }

    override fun contarPorSincronizacion(sincronizacion: String): Flow<Int> =
        filas.map { lista -> lista.count { it.sincronizacion == sincronizacion } }

    override suspend fun reencolarErrores(): Int {
        var cambiadas = 0
        filas.update { lista ->
            lista.map {
                if (it.sincronizacion == EstadoSincronizacion.Error.name) {
                    cambiadas++
                    it.copy(sincronizacion = EstadoSincronizacion.Pendiente.name, intentos = 0)
                } else it
            }
        }
        return cambiadas
    }

    override suspend fun insertar(recordatorio: RecordatorioEntity): Long {
        require(filas.value.none { it.claveCliente == recordatorio.claveCliente }) { "clave repetida" }
        val id = siguienteId++
        filas.update { it + recordatorio.copy(id = id) }
        return id
    }

    override suspend fun actualizar(recordatorio: RecordatorioEntity) {
        filas.update { lista -> lista.map { if (it.id == recordatorio.id) recordatorio else it } }
    }

    override suspend fun eliminar(id: Long) {
        filas.update { lista -> lista.filterNot { it.id == id } }
    }
}

/**
 * Servicio remoto falso que se comporta como el real en lo que importa: respeta la clave de idempotencia
 * (reenviar una creación no genera un segundo registro) y registra cada llamada para poder contarlas.
 */
class RemotoFalso : RecordatoriosRemoteDataSource {
    val servidor = mutableListOf<RecordatorioDto>()
    private val clavesRecibidas = mutableMapOf<String, Long>()
    private var siguienteId = 100L

    var llamadasCrear = 0
    var llamadasActualizar = 0
    var llamadasEliminar = 0
    var llamadasListar = 0

    /** Si no es `null`, toda operación termina con este resultado. */
    var fallo: ResultadoRemoto<Nothing>? = null
    var retrasoMs = 0L
    var resultadoListar: ResultadoRemoto<List<RecordatorioDto>>? = null

    override suspend fun listar(): ResultadoRemoto<List<RecordatorioDto>> {
        llamadasListar++
        return resultadoListar ?: fallo ?: ResultadoRemoto.Exito(servidor.toList())
    }

    override suspend fun crear(claveIdempotencia: String, recordatorio: RecordatorioDto): ResultadoRemoto<RecordatorioDto> {
        llamadasCrear++
        if (retrasoMs > 0) delay(retrasoMs)
        fallo?.let { return it }
        val existente = clavesRecibidas[claveIdempotencia]
        if (existente != null) return ResultadoRemoto.Exito(servidor.first { it.id == existente })
        val creado = recordatorio.copy(id = siguienteId++)
        servidor += creado
        clavesRecibidas[claveIdempotencia] = creado.id!!
        return ResultadoRemoto.Exito(creado)
    }

    override suspend fun actualizar(id: Long, recordatorio: RecordatorioDto): ResultadoRemoto<RecordatorioDto> {
        llamadasActualizar++
        fallo?.let { return it }
        val posicion = servidor.indexOfFirst { it.id == id }
        if (posicion < 0) return ResultadoRemoto.NoEncontrado
        servidor[posicion] = recordatorio.copy(id = id)
        return ResultadoRemoto.Exito(servidor[posicion])
    }

    override suspend fun eliminar(id: Long): ResultadoRemoto<Unit> {
        llamadasEliminar++
        fallo?.let { return it }
        return if (servidor.removeAll { it.id == id }) ResultadoRemoto.Exito(Unit) else ResultadoRemoto.NoEncontrado
    }
}

class ConectividadFalsa(conectado: Boolean = true) : Conectividad {
    val estado = MutableStateFlow(conectado)
    override fun estaConectado() = estado.value
    override val conexion: Flow<Boolean> = estado
}

fun entidadDePrueba(
    clave: String = "clave-1",
    titulo: String = "Entregar la actividad",
    sincronizacion: EstadoSincronizacion = EstadoSincronizacion.Pendiente,
    idRemoto: Long? = null,
    eliminado: Boolean = false
) = RecordatorioEntity(
    claveCliente = clave,
    idRemoto = idRemoto,
    titulo = titulo,
    categoriaId = "estudio",
    categoriaNombre = "Estudio",
    prioridad = 3,
    estadoTipo = RecordatorioEntity.PENDIENTE,
    creadoEl = "2026-11-09",
    sincronizacion = sincronizacion.name,
    eliminado = eliminado
)

fun dtoDePrueba(
    id: Long = 500,
    clave: String = "remota-1",
    titulo: String? = "Recordatorio del servidor",
    categoriaId: String? = "trabajo"
) = RecordatorioDto(
    id = id,
    claveCliente = clave,
    titulo = titulo,
    categoriaId = categoriaId,
    prioridad = "MEDIA",
    estado = RecordatorioEntity.PENDIENTE,
    creadoEl = "2026-11-08"
)

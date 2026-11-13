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

/** Cuenta que usan por defecto las pruebas. */
const val ANA = "uid-ana"
const val BETO = "uid-beto"

/** DAO en memoria: permite probar el repositorio y la sincronización sin Android ni SQLite. */
class DaoFalso : RecordatorioDao {
    val filas = MutableStateFlow<List<RecordatorioEntity>>(emptyList())
    private var siguienteId = 1L

    private fun visibles(uid: String, lista: List<RecordatorioEntity>) =
        lista.filter { it.uid == uid && !it.eliminado }.sortedBy { it.id }

    override fun observarTodos(uid: String): Flow<List<RecordatorioEntity>> = filas.map { visibles(uid, it) }

    override fun observarSinCompletados(uid: String): Flow<List<RecordatorioEntity>> =
        observarTodos(uid).map { lista -> lista.filter { it.estadoTipo != RecordatorioEntity.COMPLETADO } }

    override fun observarPorId(uid: String, id: Long): Flow<RecordatorioEntity?> =
        filas.map { lista -> visibles(uid, lista).firstOrNull { it.id == id } }

    override suspend fun obtenerPorId(uid: String, id: Long) = visibles(uid, filas.value).firstOrNull { it.id == id }

    override suspend fun obtenerPorClave(uid: String, clave: String) =
        filas.value.firstOrNull { it.uid == uid && it.claveCliente == clave }

    override suspend fun obtenerPendientes(uid: String) =
        filas.value.filter { it.uid == uid && it.sincronizacion == EstadoSincronizacion.Pendiente.name }.sortedBy { it.id }

    override suspend fun obtenerSincronizadosEnRemoto(uid: String) = filas.value.filter {
        it.uid == uid && it.enRemoto && !it.eliminado && it.sincronizacion == EstadoSincronizacion.Sincronizado.name
    }

    override fun contarPorSincronizacion(uid: String, sincronizacion: String): Flow<Int> =
        filas.map { lista -> lista.count { it.uid == uid && it.sincronizacion == sincronizacion } }

    override suspend fun reencolarErrores(uid: String): Int = reemplazar(
        { it.uid == uid && it.sincronizacion == EstadoSincronizacion.Error.name },
        { it.copy(sincronizacion = EstadoSincronizacion.Pendiente.name, intentos = 0) }
    )

    override suspend fun adoptarHuerfanas(uid: String): Int = reemplazar({ it.uid == "" }, { it.copy(uid = uid) })

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

    private fun reemplazar(condicion: (RecordatorioEntity) -> Boolean, cambio: (RecordatorioEntity) -> RecordatorioEntity): Int {
        var cambiadas = 0
        filas.update { lista -> lista.map { if (condicion(it)) cambio(it).also { cambiadas++ } else it } }
        return cambiadas
    }
}

/**
 * Servicio remoto falso que se comporta como Firestore en lo que importa: cada cuenta tiene su propia
 * colección, guardar con la misma clave reemplaza el documento (nunca crea otro) y borrar algo que no
 * existe no es un error. [denegados] simula las reglas de seguridad: esas cuentas reciben NoAutorizado.
 */
class RemotoFalso : RecordatoriosRemoteDataSource {
    /** Documentos por cuenta: uid -> (clave -> documento). */
    val colecciones = mutableMapOf<String, MutableMap<String, RecordatorioDto>>()
    val denegados = mutableSetOf<String>()

    var llamadasGuardar = 0
    var llamadasEliminar = 0
    var llamadasListar = 0

    /** Si no es `null`, toda operación termina con este resultado. */
    var fallo: ResultadoRemoto<Nothing>? = null
    var retrasoMs = 0L
    var resultadoListar: ResultadoRemoto<List<RecordatorioDto>>? = null

    fun de(uid: String = ANA): MutableMap<String, RecordatorioDto> = colecciones.getOrPut(uid) { mutableMapOf() }

    /** Lo que otra instalación (otro dispositivo) dejó en el servicio. */
    fun agregar(dto: RecordatorioDto, uid: String = ANA) {
        de(uid)[dto.claveCliente!!] = dto
    }

    override suspend fun listar(uid: String): ResultadoRemoto<List<RecordatorioDto>> {
        llamadasListar++
        if (uid in denegados) return ResultadoRemoto.NoAutorizado
        return resultadoListar ?: fallo ?: ResultadoRemoto.Exito(de(uid).values.toList())
    }

    override suspend fun guardar(uid: String, clave: String, recordatorio: RecordatorioDto): ResultadoRemoto<Unit> {
        llamadasGuardar++
        if (retrasoMs > 0) delay(retrasoMs)
        if (uid in denegados) return ResultadoRemoto.NoAutorizado
        fallo?.let { return it }
        de(uid)[clave] = recordatorio.copy(claveCliente = clave)
        return ResultadoRemoto.Exito(Unit)
    }

    override suspend fun eliminar(uid: String, clave: String): ResultadoRemoto<Unit> {
        llamadasEliminar++
        if (uid in denegados) return ResultadoRemoto.NoAutorizado
        fallo?.let { return it }
        de(uid).remove(clave)
        return ResultadoRemoto.Exito(Unit)
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
    enRemoto: Boolean = false,
    eliminado: Boolean = false,
    uid: String = ANA
) = RecordatorioEntity(
    uid = uid,
    claveCliente = clave,
    enRemoto = enRemoto,
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
    clave: String = "remota-1",
    titulo: String? = "Recordatorio del servidor",
    categoriaId: String? = "trabajo"
) = RecordatorioDto(
    claveCliente = clave,
    titulo = titulo,
    categoriaId = categoriaId,
    prioridad = "MEDIA",
    estado = RecordatorioEntity.PENDIENTE,
    creadoEl = "2026-11-08"
)

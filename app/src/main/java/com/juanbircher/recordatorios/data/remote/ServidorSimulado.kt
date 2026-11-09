package com.juanbircher.recordatorios.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.aDto
import com.juanbircher.recordatorios.data.aEntidadNueva
import com.juanbircher.recordatorios.data.cargarRecordatorios
import java.io.File
import java.time.LocalDate

/** Respuesta HTTP que produce el servicio simulado. */
data class RespuestaSimulada(val codigo: Int, val cuerpo: String? = null)

/** Dónde guarda el servicio simulado lo que "tiene en el servidor", para que sobreviva a cerrar la app. */
interface AlmacenServidor {
    fun leer(): String?
    fun escribir(json: String)
}

class AlmacenEnMemoria : AlmacenServidor {
    private var contenido: String? = null
    override fun leer() = contenido
    override fun escribir(json: String) {
        contenido = json
    }
}

class AlmacenEnArchivo(private val archivo: File) : AlmacenServidor {
    override fun leer(): String? = archivo.takeIf { it.exists() }?.readText()
    override fun escribir(json: String) {
        archivo.writeText(json)
    }
}

private data class EstadoServidor(
    val siguienteId: Long = 1,
    val recordatorios: List<RecordatorioDto> = emptyList(),
    /** Clave de idempotencia → id del registro que se creó con ella. */
    val claves: Map<String, Long> = emptyMap()
)

/**
 * Servicio REST simulado, sin red: se comporta como un backend real de recordatorios.
 *
 * - `GET /recordatorios` → 200 con la lista.
 * - `POST /recordatorios` con `Idempotency-Key` → 201 si crea; **200 con el registro existente** si la clave
 *   ya se recibió (el reenvío no duplica).
 * - `PUT /recordatorios/{id}` → 200, o 404 si no existe.
 * - `DELETE /recordatorios/{id}` → 204, o 404 si no existe.
 * - Valida lo que recibe: título de 4 a 60 caracteres, categoría, prioridad y fecha de creación; si no, 400.
 *
 * Permite probar la app completa sin depender de un servicio de terceros ni de la red del equipo. Para usar
 * un servicio real alcanza con apuntar la URL base a él (ver `local.properties.example`).
 */
class ServidorSimulado(
    private val almacen: AlmacenServidor,
    private val semilla: List<RecordatorioDto> = emptyList(),
    private val gson: Gson = Gson()
) {
    @Synchronized
    fun atender(metodo: String, segmentos: List<String>, claveIdempotencia: String?, cuerpo: String?): RespuestaSimulada {
        val estado = cargar()
        return when {
            segmentos.isEmpty() && metodo == "GET" -> ok(200, estado.recordatorios)
            segmentos.isEmpty() && metodo == "POST" -> crear(estado, claveIdempotencia, cuerpo)
            segmentos.size == 1 && metodo == "PUT" -> actualizar(estado, segmentos[0], cuerpo)
            segmentos.size == 1 && metodo == "DELETE" -> eliminar(estado, segmentos[0])
            else -> error(404, "Ruta no encontrada")
        }
    }

    private fun crear(estado: EstadoServidor, clave: String?, cuerpo: String?): RespuestaSimulada {
        if (clave.isNullOrBlank()) return error(400, "Falta el encabezado Idempotency-Key")
        val existente = estado.claves[clave]?.let { id -> estado.recordatorios.firstOrNull { it.id == id } }
        if (existente != null) return ok(200, existente)

        val dto = leerDto(cuerpo) ?: return error(400, "Cuerpo ilegible")
        validar(dto)?.let { return error(400, it) }

        val creado = dto.copy(id = estado.siguienteId, claveCliente = clave)
        guardar(
            estado.copy(
                siguienteId = estado.siguienteId + 1,
                recordatorios = estado.recordatorios + creado,
                claves = estado.claves + (clave to estado.siguienteId)
            )
        )
        return ok(201, creado)
    }

    private fun actualizar(estado: EstadoServidor, idTexto: String, cuerpo: String?): RespuestaSimulada {
        val id = idTexto.toLongOrNull() ?: return error(400, "Id inválido")
        if (estado.recordatorios.none { it.id == id }) return error(404, "No existe el recordatorio $id")
        val dto = leerDto(cuerpo) ?: return error(400, "Cuerpo ilegible")
        validar(dto)?.let { return error(400, it) }

        val actualizado = dto.copy(id = id)
        guardar(estado.copy(recordatorios = estado.recordatorios.map { if (it.id == id) actualizado else it }))
        return ok(200, actualizado)
    }

    private fun eliminar(estado: EstadoServidor, idTexto: String): RespuestaSimulada {
        val id = idTexto.toLongOrNull() ?: return error(400, "Id inválido")
        if (estado.recordatorios.none { it.id == id }) return error(404, "No existe el recordatorio $id")
        guardar(estado.copy(recordatorios = estado.recordatorios.filterNot { it.id == id }))
        return RespuestaSimulada(204)
    }

    private fun validar(dto: RecordatorioDto): String? = when {
        dto.titulo?.trim()?.length !in 4..60 -> "El título debe tener entre 4 y 60 caracteres"
        dto.categoriaId.isNullOrBlank() -> "Falta la categoría"
        dto.prioridad !in setOf("ALTA", "MEDIA", "BAJA") -> "Prioridad inválida"
        dto.creadoEl.isNullOrBlank() -> "Falta la fecha de creación"
        else -> null
    }

    private fun leerDto(cuerpo: String?): RecordatorioDto? = try {
        cuerpo?.let { gson.fromJson(it, RecordatorioDto::class.java) }
    } catch (_: JsonParseException) {
        null
    }

    private fun cargar(): EstadoServidor {
        val json = almacen.leer()
        if (json != null) {
            try {
                gson.fromJson(json, EstadoServidor::class.java)?.let { return it }
            } catch (_: JsonParseException) {
                // Archivo dañado: se parte de la semilla.
            }
        }
        val inicial = EstadoServidor(
            siguienteId = semilla.size + 1L,
            recordatorios = semilla.mapIndexed { i, dto -> dto.copy(id = i + 1L) },
            claves = semilla.mapIndexedNotNull { i, dto -> dto.claveCliente?.let { it to i + 1L } }.toMap()
        )
        guardar(inicial)
        return inicial
    }

    private fun guardar(estado: EstadoServidor) = almacen.escribir(gson.toJson(estado))

    private fun ok(codigo: Int, cuerpo: Any) = RespuestaSimulada(codigo, gson.toJson(cuerpo))

    private fun error(codigo: Int, mensaje: String) =
        RespuestaSimulada(codigo, gson.toJson(mapOf("error" to mensaje)))

    companion object {
        /** Los recordatorios con los que arranca el servicio la primera vez: simulan datos creados en otro dispositivo. */
        fun semilla(hoy: LocalDate = LocalDate.now()): List<RecordatorioDto> =
            cargarRecordatorios(DatosSimulados.recordatorios(hoy), DatosSimulados.categorias).validos
                .take(4)
                .map { it.aEntidadNueva("semilla-${it.id}").aDto() }
    }
}

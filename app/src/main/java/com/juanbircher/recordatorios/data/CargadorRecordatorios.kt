package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.validation.ErrorValidacion
import com.juanbircher.recordatorios.validation.ResultadoValidacion
import com.juanbircher.recordatorios.validation.validar

/** Recordatorio que no pasó la validación, junto con los motivos. */
data class Descartado(
    val recordatorio: Recordatorio,
    val errores: List<ErrorValidacion>
)

/** Resultado de cargar los recordatorios: los que se muestran y los que se descartaron. */
data class CargaRecordatorios(
    val validos: List<Recordatorio>,
    val descartados: List<Descartado>
)

/** Valida cada recordatorio de [origen] y separa los válidos de los descartados. */
fun cargarRecordatorios(origen: List<Recordatorio>, categorias: Set<Categoria>): CargaRecordatorios {
    val (validos, invalidos) = origen
        .map { it to validar(it, categorias) }
        .partition { (_, resultado) -> resultado is ResultadoValidacion.Valido }

    return CargaRecordatorios(
        validos = validos.map { (recordatorio, _) -> recordatorio },
        descartados = invalidos.map { (recordatorio, resultado) ->
            Descartado(recordatorio, (resultado as ResultadoValidacion.Invalido).errores)
        }
    )
}

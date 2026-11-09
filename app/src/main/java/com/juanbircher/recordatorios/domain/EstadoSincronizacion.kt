package com.juanbircher.recordatorios.domain

/**
 * Situación de un recordatorio respecto del servicio remoto. Se guarda junto al registro en Room para
 * que la interfaz pueda mostrar qué pasó con cada uno, incluso sin conexión.
 */
enum class EstadoSincronizacion {
    /** El servicio ya confirmó la última versión de este recordatorio. */
    Sincronizado,

    /** Hay un cambio local que todavía no llegó al servicio; se enviará solo cuando se pueda. */
    Pendiente,

    /** El envío falló de forma que no se corrige esperando; hace falta un nuevo intento manual. */
    Error
}

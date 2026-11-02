package com.juanbircher.recordatorios.data

/** Situaciones que se pueden provocar para comprobar cada estado de la interfaz. */
enum class EscenarioSimulado { ConDatos, ListaVacia, ErrorAlCargar, ErrorAlGuardar }

/**
 * Permite comprobar los estados de vacío y error sin base de datos ni servicio remoto.
 * Está separado de [RecordatoriosRepository] porque no es una operación propia de la fuente de datos:
 * desaparece cuando la app tenga una fuente real.
 */
interface SimuladorEscenarios {
    fun aplicar(escenario: EscenarioSimulado)
}

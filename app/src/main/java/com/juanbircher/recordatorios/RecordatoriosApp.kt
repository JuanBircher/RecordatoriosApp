package com.juanbircher.recordatorios

import android.app.Application
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.RepositorioEnMemoria
import com.juanbircher.recordatorios.data.SimuladorEscenarios

/**
 * Único lugar donde se elige la fuente de datos. Vive en Application para que los datos en memoria
 * sobrevivan a la rotación y sean los mismos en todas las pantallas.
 */
class RecordatoriosApp : Application() {
    private val enMemoria = RepositorioEnMemoria()

    val repositorio: RecordatoriosRepository get() = enMemoria
    val simulador: SimuladorEscenarios get() = enMemoria
}

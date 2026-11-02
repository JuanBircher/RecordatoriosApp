package com.juanbircher.recordatorios.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.juanbircher.recordatorios.RecordatoriosApp
import com.juanbircher.recordatorios.ui.crear.CrearViewModel
import com.juanbircher.recordatorios.ui.detalle.DetalleViewModel
import com.juanbircher.recordatorios.ui.listado.ListadoViewModel
import com.juanbircher.recordatorios.ui.navegacion.Rutas

private val CreationExtras.app: RecordatoriosApp
    get() = this[APPLICATION_KEY] as RecordatoriosApp

/** Cómo se construye cada ViewModel: acá se le entrega la interfaz del repositorio que necesita. */
object FabricasViewModel {
    val listado: ViewModelProvider.Factory = viewModelFactory {
        initializer { ListadoViewModel(app.repositorio, app.simulador) }
    }

    val crear: ViewModelProvider.Factory = viewModelFactory {
        initializer { CrearViewModel(app.repositorio) }
    }

    /** El id llega como argumento de navegación y Navigation lo deja en el SavedStateHandle. */
    val detalle: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val id = checkNotNull(createSavedStateHandle().get<Long>(Rutas.ARG_RECORDATORIO_ID))
            DetalleViewModel(id, app.repositorio)
        }
    }
}

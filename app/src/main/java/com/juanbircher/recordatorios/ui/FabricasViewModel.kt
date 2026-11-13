package com.juanbircher.recordatorios.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.juanbircher.recordatorios.BuildConfig
import com.juanbircher.recordatorios.RecordatoriosApp
import com.juanbircher.recordatorios.ui.acceso.AccesoViewModel
import com.juanbircher.recordatorios.ui.ajustes.AjustesViewModel
import com.juanbircher.recordatorios.ui.crear.CrearViewModel
import com.juanbircher.recordatorios.ui.detalle.DetalleViewModel
import com.juanbircher.recordatorios.ui.listado.ListadoViewModel
import com.juanbircher.recordatorios.ui.navegacion.Rutas
import com.juanbircher.recordatorios.ui.navegacion.SesionViewModel

private val CreationExtras.app: RecordatoriosApp
    get() = this[APPLICATION_KEY] as RecordatoriosApp

/** Cómo se construye cada ViewModel: acá se le entregan las interfaces (repositorio, autenticación) que necesita. */
object FabricasViewModel {
    val sesion: ViewModelProvider.Factory = viewModelFactory {
        initializer { SesionViewModel(app.auth) }
    }

    val acceso: ViewModelProvider.Factory = viewModelFactory {
        initializer { AccesoViewModel(app.auth) }
    }

    val listado: ViewModelProvider.Factory = viewModelFactory {
        initializer { ListadoViewModel(app.repositorio) }
    }

    /**
     * La misma fábrica sirve para crear y para editar: en la ruta de edición Navigation deja el id en el
     * SavedStateHandle; en la de creación no hay id y el formulario arranca vacío.
     */
    val crear: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val id = createSavedStateHandle().get<Long>(Rutas.ARG_RECORDATORIO_ID)
            CrearViewModel(app.repositorio, recordatorioId = id)
        }
    }

    /** El id llega como argumento de navegación y Navigation lo deja en el SavedStateHandle. */
    val detalle: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val id = checkNotNull(createSavedStateHandle().get<Long>(Rutas.ARG_RECORDATORIO_ID))
            DetalleViewModel(id, app.repositorio)
        }
    }

    val ajustes: ViewModelProvider.Factory = viewModelFactory {
        initializer { AjustesViewModel(app.repositorio, app.auth, permitirPruebaAcceso = BuildConfig.USAR_EMULADOR) }
    }
}

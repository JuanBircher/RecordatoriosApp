package com.juanbircher.recordatorios.ui.navegacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juanbircher.recordatorios.R
import com.juanbircher.recordatorios.data.auth.AuthRepository
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.ui.FabricasViewModel
import com.juanbircher.recordatorios.ui.acceso.AccesoRuta
import kotlinx.coroutines.flow.StateFlow

/** Expone el estado de la sesión a la raíz de la interfaz, sin que la interfaz conozca el proveedor. */
class SesionViewModel(auth: AuthRepository) : ViewModel() {
    val sesion: StateFlow<EstadoSesion> = auth.sesion

    /**
     * Un almacén de ViewModel por cuenta. Vive acá (y no en la composición) para sobrevivir a una rotación;
     * al cambiar de cuenta se libera el de la anterior, y con él todos sus ViewModel y sus observaciones.
     */
    private val almacenes = mutableMapOf<String, ViewModelStore>()

    fun almacenDe(uid: String): ViewModelStore = almacenes.getOrPut(uid) { ViewModelStore() }

    fun liberarOtras(uidActual: String?) {
        almacenes.keys.filter { it != uidActual }.forEach { almacenes.remove(it)?.clear() }
    }

    override fun onCleared() {
        liberarOtras(uidActual = null)
    }
}

/**
 * Raíz de la interfaz: lo que se muestra depende del estado real de la sesión, no de la pantalla que se
 * vio antes. Si la sesión termina (cierre voluntario o sesión inválida), se vuelve al acceso desde
 * cualquier pantalla.
 *
 * Cada cuenta tiene su propio grafo de navegación (`key(uid)`) y su propio almacén de ViewModel. Una cuenta
 * nueva nunca hereda pantallas, filtros ni estados en memoria de la anterior, y los ViewModel de la cuenta
 * anterior se liberan (dejan de observar la base) en lugar de quedar vivos hasta cerrar la actividad.
 */
@Composable
fun RaizApp(viewModel: SesionViewModel = viewModel(factory = FabricasViewModel.sesion)) {
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()

    when (val actual = sesion) {
        EstadoSesion.Verificando -> Cargando()
        is EstadoSesion.SinSesion -> AccesoRuta(motivo = actual.motivo)
        is EstadoSesion.Activa -> key(actual.uid) {
            val almacen = viewModel.almacenDe(actual.uid)
            val dueno = remember(almacen) { object : ViewModelStoreOwner { override val viewModelStore = almacen } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides dueno) { AppNavHost() }
        }
    }

    val uidActivo = (sesion as? EstadoSesion.Activa)?.uid
    LaunchedEffect(uidActivo) { if (sesion !is EstadoSesion.Verificando) viewModel.liberarOtras(uidActivo) }
}

@Composable
private fun Cargando() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.acceso_verificando), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

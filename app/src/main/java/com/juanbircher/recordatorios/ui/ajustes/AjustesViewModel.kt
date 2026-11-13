package com.juanbircher.recordatorios.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.ResultadoPruebaAcceso
import com.juanbircher.recordatorios.data.ResumenSincronizacion
import com.juanbircher.recordatorios.data.auth.AuthRepository
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Situación de la prueba de acceso a otra cuenta. */
sealed interface PruebaAcceso {
    data object EnCurso : PruebaAcceso
    data class Terminada(val resultado: ResultadoPruebaAcceso) : PruebaAcceso
}

/** Preferencias que se muestran, la cuenta activa y si el último cambio no pudo guardarse. */
data class AjustesUiState(
    val preferencias: Preferencias = Preferencias(),
    val errorAlGuardar: Boolean = false,
    val cuenta: EstadoSesion.Activa? = null,
    val cambiosSinEnviar: Int = 0,
    val mostrarPruebaAcceso: Boolean = false,
    val pruebaAcceso: PruebaAcceso? = null
)

class AjustesViewModel(
    private val repositorio: RecordatoriosRepository,
    private val auth: AuthRepository,
    private val permitirPruebaAcceso: Boolean = false
) : ViewModel() {

    private val errorAlGuardar = MutableStateFlow(false)
    private val pruebaAcceso = MutableStateFlow<PruebaAcceso?>(null)

    val uiState: StateFlow<AjustesUiState> = combine(
        repositorio.preferencias,
        errorAlGuardar,
        auth.sesion,
        // Si falla la lectura local de pendientes, solo esa parte queda en cero: la cuenta, el cierre de sesión y
        // las preferencias siguen funcionando.
        repositorio.observarResumenSincronizacion()
            .catch { causa -> if (causa is IOException) emit(ResumenSincronizacion()) else throw causa },
        pruebaAcceso
    ) { prefs, error, sesion, resumen, prueba ->
        AjustesUiState(
            preferencias = prefs,
            errorAlGuardar = error,
            cuenta = sesion as? EstadoSesion.Activa,
            cambiosSinEnviar = resumen.pendientes + resumen.conError,
            mostrarPruebaAcceso = permitirPruebaAcceso,
            pruebaAcceso = prueba
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState())

    fun onTema(tema: TemaApp) = guardar { repositorio.actualizarTema(tema) }

    fun onMostrarCompletados(mostrar: Boolean) = guardar { repositorio.actualizarMostrarCompletados(mostrar) }

    fun onOrden(orden: OrdenListado) = guardar { repositorio.actualizarOrden(orden) }

    fun onAvisoVisto() = errorAlGuardar.update { false }

    /**
     * Cierra la sesión en el proveedor. Los recordatorios de la cuenta quedan en Room (los pendientes se
     * enviarán cuando vuelva a ingresar), pero solo los ve su dueño. La raíz de la app muestra el acceso.
     */
    fun onCerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }

    /** Solo en desarrollo: intenta leer la colección de otra cuenta y muestra qué respondió el servicio. */
    fun onProbarAcceso(uid: String) {
        val otraCuenta = uid.trim()
        if (!permitirPruebaAcceso || otraCuenta.isEmpty() || pruebaAcceso.value == PruebaAcceso.EnCurso) return
        pruebaAcceso.value = PruebaAcceso.EnCurso
        viewModelScope.launch {
            pruebaAcceso.value = PruebaAcceso.Terminada(repositorio.probarAccesoAOtraCuenta(otraCuenta))
        }
    }

    private fun guardar(operacion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operacion()
                errorAlGuardar.update { false }
            } catch (_: IOException) {
                errorAlGuardar.update { true }
            }
        }
    }
}

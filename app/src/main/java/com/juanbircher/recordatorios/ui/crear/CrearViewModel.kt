package com.juanbircher.recordatorios.ui.crear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import com.juanbircher.recordatorios.validation.validarFormulario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/** Recibe los eventos del formulario, aplica las validaciones y simula el guardado sin bloquear la interfaz. */
class CrearViewModel(
    private val repositorio: RecordatoriosRepository,
    private val hoy: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CrearUiState(categoriasDisponibles = repositorio.categorias.toList()).validado()
    )
    val uiState: StateFlow<CrearUiState> = _uiState.asStateFlow()

    fun onTituloChange(valor: String) = editar { copy(titulo = valor) }

    fun onDescripcionChange(valor: String) = editar { copy(descripcion = valor) }

    fun onCategoriaChange(valor: Categoria) = editar { copy(categoria = valor) }

    fun onPrioridadChange(valor: Prioridad) = editar { copy(prioridad = valor) }

    fun onGuardar() {
        val formulario = _uiState.value
        if (!formulario.puedeGuardar) return
        val categoria = formulario.categoria ?: return
        val prioridad = formulario.prioridad ?: return

        _uiState.update { it.copy(envio = EstadoEnvio.Enviando) }
        viewModelScope.launch {
            val resultado = try {
                repositorio.guardarRecordatorio(
                    Recordatorio(
                        id = 0,
                        titulo = formulario.titulo.trim(),
                        categoria = categoria,
                        prioridad = prioridad,
                        creadoEl = hoy(),
                        descripcion = formulario.descripcion.trim().ifEmpty { null }
                    )
                )
                EstadoEnvio.Exitoso
            } catch (_: IOException) {
                EstadoEnvio.Error
            }
            _uiState.update { it.copy(envio = resultado) }
        }
    }

    /** Cada cambio crea una nueva instancia, revalida y quita el error de un guardado anterior. */
    private fun editar(cambio: CrearUiState.() -> CrearUiState) {
        _uiState.update { actual ->
            if (!actual.editable) {
                actual
            } else {
                actual.cambio().copy(mostrarErrores = true, envio = EstadoEnvio.Inactivo).validado()
            }
        }
    }

    private fun CrearUiState.validado(): CrearUiState = copy(
        errores = validarFormulario(titulo, descripcion, categoria, prioridad, repositorio.categorias)
    )
}

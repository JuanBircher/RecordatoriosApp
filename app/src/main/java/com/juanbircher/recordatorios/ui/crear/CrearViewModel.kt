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

/**
 * Recibe los eventos del formulario, aplica las validaciones y guarda sin bloquear la interfaz.
 *
 * Si [recordatorioId] no es `null`, el formulario **edita** ese recordatorio: lo carga, y al guardar lo
 * actualiza conservando lo que el formulario no muestra (estado, fecha de creación, fecha límite, lugar).
 */
class CrearViewModel(
    private val repositorio: RecordatoriosRepository,
    private val recordatorioId: Long? = null,
    private val hoy: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CrearUiState(
            categoriasDisponibles = repositorio.categorias.toList(),
            esEdicion = recordatorioId != null,
            cargando = recordatorioId != null
        ).validado()
    )
    val uiState: StateFlow<CrearUiState> = _uiState.asStateFlow()

    /** El recordatorio que se está editando, tal como estaba al abrir el formulario. */
    private var original: Recordatorio? = null

    init {
        if (recordatorioId != null) cargarOriginal(recordatorioId)
    }

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
                val base = original
                val titulo = formulario.titulo.trim()
                val descripcion = formulario.descripcion.trim().ifEmpty { null }
                if (base != null) {
                    repositorio.actualizarRecordatorio(
                        base.copy(titulo = titulo, descripcion = descripcion, categoria = categoria, prioridad = prioridad)
                    )
                } else {
                    repositorio.guardarRecordatorio(
                        Recordatorio(
                            id = 0,
                            titulo = titulo,
                            categoria = categoria,
                            prioridad = prioridad,
                            creadoEl = hoy(),
                            descripcion = descripcion
                        )
                    )
                }
                EstadoEnvio.Exitoso
            } catch (_: IOException) {
                EstadoEnvio.Error
            }
            _uiState.update { it.copy(envio = resultado) }
        }
    }

    private fun cargarOriginal(id: Long) {
        viewModelScope.launch {
            val cargado = try {
                repositorio.obtenerRecordatorio(id)
            } catch (_: IOException) {
                null
            }
            original = cargado
            _uiState.update { actual ->
                if (cargado == null) {
                    actual.copy(cargando = false, cargaFallida = true)
                } else {
                    actual.copy(
                        titulo = cargado.titulo,
                        descripcion = cargado.descripcion.orEmpty(),
                        categoria = cargado.categoria,
                        prioridad = cargado.prioridad,
                        cargando = false
                    ).validado()
                }
            }
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

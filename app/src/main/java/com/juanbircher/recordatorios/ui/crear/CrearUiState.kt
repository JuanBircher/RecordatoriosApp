package com.juanbircher.recordatorios.ui.crear

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.validation.ErroresFormulario

/** Situación del envío del formulario. Es sealed para que no pueda estar enviando y con error a la vez. */
sealed interface EstadoEnvio {
    data object Inactivo : EstadoEnvio
    data object Enviando : EstadoEnvio
    data object Exitoso : EstadoEnvio
    data object Error : EstadoEnvio
}

/**
 * Estado del formulario de creación.
 *
 * [errores] siempre refleja los valores actuales y decide si se puede guardar. Los mensajes se muestran
 * recién cuando la persona empezó a completar el formulario ([mostrarErrores]), para no recibirla con
 * todos los campos en error.
 */
data class CrearUiState(
    val titulo: String = "",
    val descripcion: String = "",
    val categoria: Categoria? = null,
    val prioridad: Prioridad? = null,
    val categoriasDisponibles: List<Categoria> = emptyList(),
    val errores: ErroresFormulario = ErroresFormulario(),
    val mostrarErrores: Boolean = false,
    val envio: EstadoEnvio = EstadoEnvio.Inactivo
) {
    val erroresVisibles: ErroresFormulario = if (mostrarErrores) errores else ErroresFormulario()

    /** Mientras se guarda, o una vez guardado, los campos no se pueden modificar. */
    val editable: Boolean = envio !is EstadoEnvio.Enviando && envio !is EstadoEnvio.Exitoso

    val puedeGuardar: Boolean = errores.esValido && editable
}

package com.juanbircher.recordatorios.data

import com.juanbircher.recordatorios.domain.Categoria
import com.juanbircher.recordatorios.domain.EstadoRecordatorio.Cancelado
import com.juanbircher.recordatorios.domain.EstadoRecordatorio.Completado
import com.juanbircher.recordatorios.domain.Prioridad
import com.juanbircher.recordatorios.domain.Recordatorio
import java.time.LocalDate

/**
 * Datos de prueba mientras la app no tiene persistencia ni conexión.
 * Incluye a propósito dos recordatorios inválidos para mostrar que la validación los descarta.
 */
object DatosSimulados {

    val trabajo = Categoria("trabajo", "Trabajo")
    val personal = Categoria("personal", "Personal")
    val salud = Categoria("salud", "Salud")
    val estudio = Categoria("estudio", "Estudio")

    /** "Trabajo" aparece dos veces a propósito: el Set conserva una sola. */
    val categorias: Set<Categoria> = setOf(trabajo, personal, salud, estudio, Categoria("trabajo", "Trabajo"))

    /** Fechas relativas a [hoy] para que "vence en X días" tenga sentido el día que se ejecute. */
    fun recordatorios(hoy: LocalDate = LocalDate.now()): List<Recordatorio> = listOf(
        Recordatorio(
            id = 1,
            titulo = "Entregar Actividad 1 de Programación Mobile",
            categoria = estudio,
            prioridad = Prioridad.Alta,
            creadoEl = hoy.minusDays(3),
            descripcion = "PDF con capturas y enlace al repositorio",
            fechaLimite = hoy.plusDays(2)
        ),
        Recordatorio(
            id = 2,
            titulo = "Turno con el dentista",
            categoria = salud,
            prioridad = Prioridad.Media,
            creadoEl = hoy.minusDays(10),
            fechaLimite = hoy.plusDays(6),
            ubicacion = "Consultorio Av. Colón 1200"
        ),
        Recordatorio(
            id = 3,
            titulo = "Enviar presupuesto al cliente",
            categoria = trabajo,
            prioridad = Prioridad.Alta,
            creadoEl = hoy.minusDays(5),
            estado = Completado(completadoEl = hoy.minusDays(1))
        ),
        Recordatorio(
            id = 4,
            titulo = "Comprar regalo de cumpleaños",
            categoria = personal,
            prioridad = Prioridad.Baja,
            creadoEl = hoy.minusDays(2),
            fechaLimite = hoy.plusDays(12)
        ),
        Recordatorio(
            id = 5,
            titulo = "Repasar sealed classes",
            categoria = estudio,
            prioridad = Prioridad.Media,
            creadoEl = hoy.minusDays(1),
            descripcion = "Ejercicio de ResultadoGuardado del Módulo 1"
        ),
        Recordatorio(
            id = 6,
            titulo = "Reunión de equipo semanal",
            categoria = trabajo,
            prioridad = Prioridad.Media,
            creadoEl = hoy.minusDays(7),
            estado = Cancelado(motivo = "Se pasó a la semana próxima")
        ),
        Recordatorio(
            id = 7,
            titulo = "Pedir análisis de rutina",
            categoria = salud,
            prioridad = Prioridad.Alta,
            creadoEl = hoy,
            fechaLimite = hoy.plusDays(1)
        ),
        Recordatorio(
            id = 8,
            titulo = "Renovar suscripción del gimnasio",
            categoria = personal,
            prioridad = Prioridad.Baja,
            creadoEl = hoy.minusDays(20),
            estado = Cancelado()
        ),
        // Inválido: título en blanco.
        Recordatorio(
            id = 9,
            titulo = "   ",
            categoria = trabajo,
            prioridad = Prioridad.Baja,
            creadoEl = hoy
        ),
        // Inválido: la fecha límite es anterior a la creación.
        Recordatorio(
            id = 10,
            titulo = "Pagar la factura de luz",
            categoria = personal,
            prioridad = Prioridad.Media,
            creadoEl = hoy,
            fechaLimite = hoy.minusDays(4)
        )
    )
}

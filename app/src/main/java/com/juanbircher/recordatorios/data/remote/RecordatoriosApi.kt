package com.juanbircher.recordatorios.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Interfaz Retrofit del servicio. Todas las funciones son `suspend` y devuelven [Response] para que la
 * capa de datos interprete el código HTTP; ninguna pantalla ve esta interfaz.
 */
interface RecordatoriosApi {

    @GET("recordatorios")
    suspend fun listar(): Response<List<RecordatorioDto>>

    /**
     * Crea un recordatorio. [claveIdempotencia] es la clave del cliente: si el servicio ya la recibió,
     * devuelve el registro existente en lugar de crear otro, así un reenvío no duplica.
     */
    @POST("recordatorios")
    suspend fun crear(
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Body recordatorio: RecordatorioDto
    ): Response<RecordatorioDto>

    @PUT("recordatorios/{id}")
    suspend fun actualizar(
        @Path("id") id: Long,
        @Body recordatorio: RecordatorioDto
    ): Response<RecordatorioDto>

    @DELETE("recordatorios/{id}")
    suspend fun eliminar(@Path("id") id: Long): Response<Unit>
}

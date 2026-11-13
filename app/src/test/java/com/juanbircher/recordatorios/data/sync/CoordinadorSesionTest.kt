package com.juanbircher.recordatorios.data.sync

import com.juanbircher.recordatorios.data.ConectividadFalsa
import com.juanbircher.recordatorios.data.remote.CausaFallo
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.ui.AuthFalso
import com.juanbircher.recordatorios.ui.RepositorioFalso
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinadorSesionTest {

    private val repositorio = RepositorioFalso()
    private val conectividad = ConectividadFalsa(conectado = true)
    private val ana = EstadoSesion.Activa(uid = "uid-ana", correo = "ana@prueba.com")

    private fun TestScope.iniciar(auth: AuthFalso) {
        CoordinadorSesion(auth, repositorio, conectividad).iniciar(backgroundScope)
        runCurrent()
    }

    @Test
    fun `al abrir la app se comprueba una vez que la sesion guardada siga siendo valida`() = runTest {
        val auth = AuthFalso(ana)

        iniciar(auth)

        assertEquals(1, auth.verificaciones)
    }

    @Test
    fun `con sesion y conexion se preparan los datos de la cuenta y se sincroniza`() = runTest {
        iniciar(AuthFalso(ana))

        assertEquals(listOf("uid-ana"), repositorio.sesionesPreparadas)
        assertEquals(1, repositorio.sincronizaciones)
    }

    @Test
    fun `sin sesion no se sincroniza nada hasta que alguien ingresa`() = runTest {
        val auth = AuthFalso(EstadoSesion.SinSesion())
        iniciar(auth)
        assertEquals(0, repositorio.sincronizaciones)

        auth.sesion.value = ana
        runCurrent()

        assertEquals(1, repositorio.sincronizaciones)
    }

    @Test
    fun `sin conexion se prepara la cuenta igual y se sincroniza cuando vuelve la red`() = runTest {
        conectividad.estado.value = false
        iniciar(AuthFalso(ana))
        assertEquals(listOf("uid-ana"), repositorio.sesionesPreparadas)
        assertEquals(0, repositorio.sincronizaciones)

        conectividad.estado.value = true
        runCurrent()

        assertEquals(1, repositorio.sincronizaciones)
    }

    @Test
    fun `cambiar de cuenta prepara y sincroniza la cuenta nueva`() = runTest {
        val auth = AuthFalso(ana)
        iniciar(auth)

        auth.sesion.value = EstadoSesion.Activa(uid = "uid-beto", correo = "beto@prueba.com")
        runCurrent()

        assertEquals(listOf("uid-ana", "uid-beto"), repositorio.sesionesPreparadas)
        assertEquals(2, repositorio.sincronizaciones)
    }

    @Test
    fun `si el servicio rechaza la identidad se vuelve a comprobar la sesion`() = runTest {
        val auth = AuthFalso(ana)
        iniciar(auth)

        repositorio.estadoSincronizacion.value = EstadoSync.Fallo(CausaFallo.NoAutorizado)
        runCurrent()

        assertEquals(2, auth.verificaciones)
    }
}

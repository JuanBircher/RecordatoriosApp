package com.juanbircher.recordatorios.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferenciasDataSourceTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val alcances = mutableListOf<CoroutineScope>()

    @After
    fun cerrar() = alcances.forEach { it.cancel() }

    /** Abre un DataStore real sobre [archivo]; abrirlo otra vez equivale a cerrar y reabrir la app. */
    private fun abrir(archivo: File): DataStore<Preferences> {
        val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { alcances += it }
        return PreferenceDataStoreFactory.create(scope = alcance, produceFile = { archivo })
    }

    private fun archivo() = File(carpeta.root, "preferencias.preferences_pb")

    @Test
    fun `sin nada guardado usa los valores por defecto`() = runBlocking {
        val fuente = PreferenciasDataSource(abrir(archivo()))

        assertEquals(Preferencias(), fuente.preferencias.first())
        assertEquals(Preferencias(TemaApp.Sistema, true, OrdenListado.Prioridad), fuente.preferencias.first())
    }

    @Test
    fun `lo guardado se conserva al cerrar y volver a abrir`() = runBlocking {
        val archivo = archivo()
        val primera = PreferenciasDataSource(abrir(archivo))
        primera.actualizarTema(TemaApp.Oscuro)
        primera.actualizarMostrarCompletados(false)
        primera.actualizarOrden(OrdenListado.MasRecientes)
        alcances.forEach { it.cancel() }

        val reabierta = PreferenciasDataSource(abrir(archivo))

        assertEquals(Preferencias(TemaApp.Oscuro, false, OrdenListado.MasRecientes), reabierta.preferencias.first())
    }

    @Test
    fun `cambiar una preferencia no pisa las otras`() = runBlocking {
        val fuente = PreferenciasDataSource(abrir(archivo()))
        fuente.actualizarTema(TemaApp.Claro)

        fuente.actualizarOrden(OrdenListado.FechaLimite)

        assertEquals(Preferencias(TemaApp.Claro, true, OrdenListado.FechaLimite), fuente.preferencias.first())
    }

    @Test
    fun `un valor desconocido se reemplaza por el predeterminado y los demas se respetan`() = runBlocking {
        val almacen = abrir(archivo())
        almacen.edit {
            it[ClavesPreferencias.TEMA] = "MORADO"
            it[ClavesPreferencias.ORDEN] = OrdenListado.FechaLimite.name
        }

        val leidas = PreferenciasDataSource(almacen).preferencias.first()

        assertEquals(TemaApp.Sistema, leidas.tema)
        assertEquals(OrdenListado.FechaLimite, leidas.orden)
    }

    @Test
    fun `un valor guardado con otro tipo no rompe la lectura`() = runBlocking {
        val almacen = abrir(archivo())
        almacen.edit {
            it[stringPreferencesKey("mostrar_completados")] = "quizas"
            it[booleanPreferencesKey("tema")] = true
        }

        val leidas = PreferenciasDataSource(almacen).preferencias.first()

        assertEquals(Preferencias(), leidas)
    }
}

package com.juanbircher.recordatorios.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.juanbircher.recordatorios.domain.OrdenListado
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Única instancia de DataStore de la app. La delegación `preferencesDataStore` garantiza una sola por
 * proceso: crear dos sobre el mismo archivo lanzaría una excepción.
 */
val Context.dataStorePreferencias: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

/** Claves tipadas del archivo de preferencias. */
object ClavesPreferencias {
    val TEMA = stringPreferencesKey("tema")
    val MOSTRAR_COMPLETADOS = booleanPreferencesKey("mostrar_completados")
    val ORDEN = stringPreferencesKey("orden")
}

/**
 * Lee y escribe las [Preferencias] en DataStore. Las enumeraciones se guardan por nombre (texto) y se
 * leen con valores por defecto: si falta el dato, tiene un valor desconocido o es de otro tipo, se usa el
 * predeterminado de [Preferencias] en lugar de fallar. Un error de lectura recuperable (E/S) también
 * produce los valores predeterminados.
 */
class PreferenciasDataSource(private val dataStore: DataStore<Preferences>) {

    /** Emite las preferencias actuales y cada cambio. */
    val preferencias: Flow<Preferencias> = dataStore.data
        .catch { causa ->
            if (causa is IOException) emit(emptyPreferences()) else throw causa
        }
        .map { it.aModelo() }
        .distinctUntilChanged()

    suspend fun actualizarTema(tema: TemaApp) {
        dataStore.edit { it[ClavesPreferencias.TEMA] = tema.name }
    }

    suspend fun actualizarMostrarCompletados(mostrar: Boolean) {
        dataStore.edit { it[ClavesPreferencias.MOSTRAR_COMPLETADOS] = mostrar }
    }

    suspend fun actualizarOrden(orden: OrdenListado) {
        dataStore.edit { it[ClavesPreferencias.ORDEN] = orden.name }
    }
}

private fun Preferences.aModelo(): Preferencias {
    val predeterminadas = Preferencias()
    return Preferencias(
        tema = leer(ClavesPreferencias.TEMA)?.let { nombre -> TemaApp.entries.firstOrNull { it.name == nombre } }
            ?: predeterminadas.tema,
        mostrarCompletados = leer(ClavesPreferencias.MOSTRAR_COMPLETADOS) ?: predeterminadas.mostrarCompletados,
        orden = leer(ClavesPreferencias.ORDEN)?.let { nombre -> OrdenListado.entries.firstOrNull { it.name == nombre } }
            ?: predeterminadas.orden
    )
}

/**
 * Lee una clave sin lanzar si el valor guardado es de otro tipo: se trata como ausente. Se lee del mapa y
 * se comprueba el tipo (`as?` con tipo reificado), porque `this[clave]` solo falla al usar el resultado.
 */
private inline fun <reified T> Preferences.leer(clave: Preferences.Key<T>): T? = asMap()[clave] as? T

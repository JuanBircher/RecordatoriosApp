package com.juanbircher.recordatorios.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Qué sabe la app sobre la conexión del dispositivo. Es una interfaz para poder probar sin Android. */
interface Conectividad {
    fun estaConectado(): Boolean

    /** Emite el estado actual y cada cambio de conectividad. */
    val conexion: Flow<Boolean>
}

/** Implementación con [ConnectivityManager]. Requiere el permiso ACCESS_NETWORK_STATE. */
class ConectividadAndroid(contexto: Context) : Conectividad {
    private val manager = contexto.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun estaConectado(): Boolean {
        val red = manager.activeNetwork ?: return false
        val capacidades = manager.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override val conexion: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(estaConectado())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(estaConectado())
            }

            override fun onLost(network: Network) {
                trySend(estaConectado())
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(estaConectado())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

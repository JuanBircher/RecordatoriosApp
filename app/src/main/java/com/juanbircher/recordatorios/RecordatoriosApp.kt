package com.juanbircher.recordatorios

import android.app.Application
import android.util.Log
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.RecordatoriosRepositoryImpl
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.local.RecordatoriosDatabase
import com.juanbircher.recordatorios.data.preferences.PreferenciasDataSource
import com.juanbircher.recordatorios.data.preferences.dataStorePreferencias
import com.juanbircher.recordatorios.data.remote.AlmacenEnArchivo
import com.juanbircher.recordatorios.data.remote.ControlServidorSimulado
import com.juanbircher.recordatorios.data.remote.InterceptorServidorSimulado
import com.juanbircher.recordatorios.data.remote.RecordatoriosApi
import com.juanbircher.recordatorios.data.remote.RetrofitRemoteDataSource
import com.juanbircher.recordatorios.data.remote.ServidorSimulado
import com.juanbircher.recordatorios.data.remote.SimuladorServidor
import com.juanbircher.recordatorios.data.sync.Conectividad
import com.juanbircher.recordatorios.data.sync.ConectividadAndroid
import com.juanbircher.recordatorios.data.sync.SincronizadorRecordatorios
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Único lugar donde se arma la capa de datos: base Room, DataStore, Retrofit/OkHttp, sincronizador y
 * repositorio. Cada pieza se crea una sola vez y vive mientras viva el proceso. Los ViewModel reciben
 * solo el repositorio (una interfaz) mediante `FabricasViewModel`.
 */
class RecordatoriosApp : Application() {

    /** Alcance de vida de la app: la sincronización en segundo plano no depende de ninguna pantalla. */
    private val alcance = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, causa -> Log.e(ETIQUETA, "Falló una tarea en segundo plano", causa) }
    )

    private val base by lazy { RecordatoriosDatabase.crear(this) }
    private val conectividad: Conectividad by lazy { ConectividadAndroid(this) }
    private val control = ControlServidorSimulado()

    private val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.SERVICIO_SIMULADO) {
                    val servidor = ServidorSimulado(
                        almacen = AlmacenEnArchivo(File(filesDir, "servidor_simulado.json")),
                        semilla = ServidorSimulado.semilla()
                    )
                    addInterceptor(InterceptorServidorSimulado(servidor, modo = { control.modo.value }))
                }
            }
            .build()
    }

    private val api: RecordatoriosApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.URL_BASE)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecordatoriosApi::class.java)
    }

    val repositorio: RecordatoriosRepository by lazy {
        RecordatoriosRepositoryImpl(
            dao = base.recordatorioDao(),
            preferenciasFuente = PreferenciasDataSource(dataStorePreferencias),
            sincronizador = SincronizadorRecordatorios(
                dao = base.recordatorioDao(),
                remoto = RetrofitRemoteDataSource(api, conectividad),
                categorias = DatosSimulados.categorias
            ),
            categorias = DatosSimulados.categorias,
            alcance = alcance
        )
    }

    val simulador: SimuladorServidor get() = control

    override fun onCreate() {
        super.onCreate()
        // Se sincroniza al abrir la app y cada vez que se recupera la conexión. Si varios eventos coinciden,
        // el sincronizador atiende uno solo: no hay envíos duplicados.
        alcance.launch {
            conectividad.conexion.filter { it }.collect { repositorio.sincronizar() }
        }
    }

    private companion object {
        const val ETIQUETA = "Recordatorios"
    }
}

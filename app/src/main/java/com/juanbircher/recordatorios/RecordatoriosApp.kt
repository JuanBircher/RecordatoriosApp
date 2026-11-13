package com.juanbircher.recordatorios

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.juanbircher.recordatorios.data.DatosSimulados
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.data.RecordatoriosRepositoryImpl
import com.juanbircher.recordatorios.data.auth.AuthRepository
import com.juanbircher.recordatorios.data.auth.FirebaseAuthRepository
import com.juanbircher.recordatorios.data.local.RecordatoriosDatabase
import com.juanbircher.recordatorios.data.preferences.PreferenciasDataSource
import com.juanbircher.recordatorios.data.preferences.dataStorePreferencias
import com.juanbircher.recordatorios.data.remote.FirestoreRemoteDataSource
import com.juanbircher.recordatorios.data.sync.Conectividad
import com.juanbircher.recordatorios.data.sync.ConectividadAndroid
import com.juanbircher.recordatorios.data.sync.CoordinadorSesion
import com.juanbircher.recordatorios.data.sync.SincronizadorRecordatorios
import com.juanbircher.recordatorios.domain.EstadoSesion
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Único lugar donde se arma la capa de datos: Firebase (Auth y Firestore), base Room, DataStore,
 * sincronizador y repositorios. Cada pieza se crea una sola vez y vive mientras viva el proceso. Los
 * ViewModel reciben solo interfaces mediante `FabricasViewModel`: ninguno conoce Firebase.
 *
 * Ambientes (product flavors):
 * - **dev**: Firebase Local Emulator Suite en la PC. Se configura a mano con el proyecto `demo-recordatorios`
 *   (el prefijo `demo-` hace que el emulador nunca pueda tocar un proyecto real) y no usa google-services.json.
 * - **prod**: el proyecto Firebase real, configurado por el plugin de Google Services con
 *   `app/src/prod/google-services.json`.
 */
class RecordatoriosApp : Application() {

    /** Alcance de vida de la app: la sincronización en segundo plano no depende de ninguna pantalla. */
    private val alcance = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, causa -> Log.e(ETIQUETA, "Falló una tarea en segundo plano", causa) }
    )

    private val base by lazy { RecordatoriosDatabase.crear(this) }
    private val conectividad: Conectividad by lazy { ConectividadAndroid(this) }

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    val auth: AuthRepository by lazy { FirebaseAuthRepository(firebaseAuth, alcance) }

    /** La cuenta con sesión activa, o `null`. Es todo lo que la capa de datos sabe de la autenticación. */
    private val uidActivo: StateFlow<String?> by lazy {
        auth.sesion.map { (it as? EstadoSesion.Activa)?.uid }.stateIn(alcance, SharingStarted.Eagerly, null)
    }

    private val remoto by lazy { FirestoreRemoteDataSource(firestore, conectividad) }

    val repositorio: RecordatoriosRepository by lazy {
        RecordatoriosRepositoryImpl(
            dao = base.recordatorioDao(),
            preferenciasFuente = PreferenciasDataSource(dataStorePreferencias),
            sincronizador = SincronizadorRecordatorios(
                dao = base.recordatorioDao(),
                remoto = remoto,
                categorias = DatosSimulados.categorias,
                uidActual = { uidActivo.value }
            ),
            categorias = DatosSimulados.categorias,
            alcance = alcance,
            uidActivo = uidActivo,
            remoto = remoto
        )
    }

    override fun onCreate() {
        super.onCreate()
        configurarFirebase()
        // El archivo del servidor REST simulado de la versión 3 ya no se usa (se borra fuera del hilo principal).
        alcance.launch(Dispatchers.IO) { File(filesDir, "servidor_simulado.json").delete() }
        // Se sincroniza al ingresar, al abrir la app con una sesión guardada y cada vez que vuelve la
        // conexión. Si varios eventos coinciden, el sincronizador atiende uno solo: no hay envíos duplicados.
        CoordinadorSesion(auth, repositorio, conectividad).iniciar(alcance)
    }

    /** Firebase se configura antes de cualquier uso: el emulador y la caché no se pueden cambiar después. */
    private fun configurarFirebase() {
        if (BuildConfig.USAR_EMULADOR && FirebaseApp.getApps(this).isEmpty()) {
            val opciones = FirebaseOptions.Builder()
                .setProjectId(PROYECTO_DEMO)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("emulador-local") // el emulador no valida la clave
                .build()
            FirebaseApp.initializeApp(this, opciones)
        }
        firebaseAuth = Firebase.auth
        firestore = Firebase.firestore
        if (BuildConfig.USAR_EMULADOR) {
            firebaseAuth.useEmulator(BuildConfig.EMULADOR_HOST, PUERTO_AUTH)
            firestore.useEmulator(BuildConfig.EMULADOR_HOST, PUERTO_FIRESTORE)
        }
        // Room es la única copia local: Firestore guarda en memoria lo mínimo para operar y nada en disco,
        // así no quedan en el teléfono restos de la sesión de otra cuenta.
        firestore.firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
    }

    private companion object {
        const val ETIQUETA = "Recordatorios"
        const val PROYECTO_DEMO = "demo-recordatorios"
        const val PUERTO_AUTH = 9099
        const val PUERTO_FIRESTORE = 8085
    }
}

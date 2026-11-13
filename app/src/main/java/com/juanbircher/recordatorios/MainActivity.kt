package com.juanbircher.recordatorios

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.juanbircher.recordatorios.data.RecordatoriosRepository
import com.juanbircher.recordatorios.domain.Preferencias
import com.juanbircher.recordatorios.domain.TemaApp
import com.juanbircher.recordatorios.ui.navegacion.RaizApp
import com.juanbircher.recordatorios.ui.theme.RecordatoriosTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Solo en el primer arranque de la actividad: al rotar no se vuelve a preguntar.
        if (savedInstanceState == null) pedirRedLocalSiHaceFalta()
        val repositorio = (application as RecordatoriosApp).repositorio
        // Se espera la primera lectura de DataStore (sin bloquear el hilo principal) para que la pantalla
        // arranque con el tema guardado y no parpadee con el predeterminado.
        lifecycleScope.launch {
            val inicial = repositorio.preferencias.first()
            mostrar(repositorio, inicial)
        }
    }

    /**
     * Solo en dev (emulador de Firebase en la PC): desde Android 17 conectarse a una dirección de la red
     * local requiere el permiso de ejecución ACCESS_LOCAL_NETWORK. Sin él, cada llamada al emulador vence
     * como si no hubiera conexión. prod nunca lo pide: solo habla con servidores de internet.
     */
    private fun pedirRedLocalSiHaceFalta() {
        if (!BuildConfig.USAR_EMULADOR || Build.VERSION.SDK_INT < VERSION_ANDROID_17) return
        if (checkSelfPermission(PERMISO_RED_LOCAL) != PackageManager.PERMISSION_GRANTED) {
            // Pedido directo de la plataforma: no se necesita la respuesta (si se niega, las llamadas al
            // emulador fallan como sin conexión) y así no depende de la versión de Fragment que trae Firebase.
            requestPermissions(arrayOf(PERMISO_RED_LOCAL), CODIGO_PEDIDO_RED_LOCAL)
        }
    }

    private fun mostrar(repositorio: RecordatoriosRepository, inicial: Preferencias) {
        setContent {
            // El tema sale de DataStore: se observa con el ciclo de vida y cambia en cuanto se modifica la preferencia.
            val preferencias by repositorio.preferencias.collectAsStateWithLifecycle(initialValue = inicial)
            val oscuro = when (preferencias.tema) {
                TemaApp.Sistema -> isSystemInDarkTheme()
                TemaApp.Claro -> false
                TemaApp.Oscuro -> true
            }

            // Los íconos de las barras del sistema deben contrastar con el tema elegido, no con el del dispositivo.
            DisposableEffect(oscuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                    ) { oscuro }
                )
                onDispose {}
            }

            RecordatoriosTheme(darkTheme = oscuro) {
                RaizApp()
            }
        }
    }
}

private const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
private const val VERSION_ANDROID_17 = 37
private const val CODIGO_PEDIDO_RED_LOCAL = 17

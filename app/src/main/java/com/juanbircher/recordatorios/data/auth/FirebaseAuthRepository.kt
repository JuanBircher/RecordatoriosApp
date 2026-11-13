package com.juanbircher.recordatorios.data.auth

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.juanbircher.recordatorios.domain.ErrorAuth
import com.juanbircher.recordatorios.domain.EstadoSesion
import com.juanbircher.recordatorios.domain.MotivoSinSesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/**
 * [AuthRepository] sobre Firebase Authentication con correo y contraseña.
 *
 * - El estado sale de un `AuthStateListener` envuelto en un `callbackFlow`: Firebase avisa al registrarse
 *   el oyente (con la sesión guardada, si existe) y cada vez que alguien ingresa o sale. `awaitClose`
 *   quita el oyente cuando ya nadie observa.
 * - La sesión persiste entre aperturas: la guarda el SDK, no esta clase.
 * - [verificarSesion] usa `reload()`: le pregunta al servidor por la cuenta. Así se detecta una cuenta
 *   deshabilitada o borrada aunque el teléfono todavía tenga una sesión guardada.
 *
 * @param alcance Alcance de vida de la app, donde se mantiene el estado compartido.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    alcance: CoroutineScope
) : AuthRepository {

    /**
     * Por qué terminó la última sesión. Es un estado aparte del usuario, y no un valor que se consume al
     * leerlo: cuando la cuenta se deshabilita, Firebase cierra la sesión por su cuenta y después la cierra
     * esta clase, así que el oyente recibe dos avisos seguidos y ambos deben mostrar el mismo motivo. Se
     * borra recién al intentar un nuevo ingreso.
     */
    private val motivoDelCierre = MutableStateFlow<MotivoSinSesion?>(null)

    private val usuario = callbackFlow<FirebaseUser?> {
        val oyente = FirebaseAuth.AuthStateListener { firebaseAuth -> trySend(firebaseAuth.currentUser) }
        auth.addAuthStateListener(oyente)
        awaitClose { auth.removeAuthStateListener(oyente) }
    }

    override val sesion: StateFlow<EstadoSesion> = combine(usuario, motivoDelCierre) { actual, motivo ->
        if (actual != null) {
            EstadoSesion.Activa(uid = actual.uid, correo = actual.email)
        } else {
            EstadoSesion.SinSesion(motivo)
        }
    }.stateIn(alcance, SharingStarted.Eagerly, EstadoSesion.Verificando)

    override suspend fun registrar(correo: String, contrasena: String): ErrorAuth? = autenticar {
        motivoDelCierre.value = null
        auth.createUserWithEmailAndPassword(correo, contrasena).await()
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): ErrorAuth? = autenticar {
        motivoDelCierre.value = null
        auth.signInWithEmailAndPassword(correo, contrasena).await()
    }

    override suspend fun cerrarSesion() {
        motivoDelCierre.value = MotivoSinSesion.CerroSesion
        auth.signOut()
    }

    override suspend fun verificarSesion() {
        val usuario = auth.currentUser ?: return
        try {
            usuario.reload().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidUserException) {
            // La cuenta fue deshabilitada o borrada, o su credencial ya no vale: la sesión local no alcanza.
            motivoDelCierre.value = MotivoSinSesion.SesionInvalida
            auth.signOut()
        } catch (e: FirebaseException) {
            // Sin conexión, demasiados pedidos u otra falla del servicio: no se puede comprobar ahora. Se sigue
            // con los datos locales y se vuelve a comprobar en la próxima ocasión (no se cierra la sesión).
        }
    }

    private suspend fun autenticar(operacion: suspend () -> Unit): ErrorAuth? =
        try {
            operacion()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            clasificarCodigoAuth(e.errorCode)
        } catch (e: FirebaseNetworkException) {
            ErrorAuth.SinConexion
        } catch (e: FirebaseTooManyRequestsException) {
            ErrorAuth.DemasiadosIntentos
        } catch (e: FirebaseException) {
            ErrorAuth.Otro
        }
}

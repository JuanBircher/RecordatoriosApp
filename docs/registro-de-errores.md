# Registro de errores

Problemas de sincronización, compilación, instalación o ejecución, cada uno con su corrección y la
re-ejecución que la verificó.

## Actividad 1

Entorno: Android Studio 2026.2.1 · SDK Platform 37 · emulador `Medium_Phone_API_37.0` (Android 17, API 37).

| # | Etapa | Síntoma | Causa | Corrección | Verificación |
|---|---|---|---|---|---|
| 1 | Compilación (empaquetado) | Aviso `Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so` en `stripDebugDebugSymbols` | Para quitar los símbolos de las librerías nativas que trae una dependencia de Compose hace falta el NDK, que no está instalado | Ninguna: es un aviso, no un error. La librería se empaqueta sin reducir, lo que solo afecta un poco el tamaño del APK de depuración | `assembleDebug` termina con `BUILD SUCCESSFUL` y la app se instala y ejecuta |
| 2 | Pruebas (intencional) | `testDebugUnitTest` falla con 16 tests en rojo (`NotImplementedError`) | Las pruebas se escribieron **antes** que el código (TDD): las funciones todavía eran `TODO()` | Implementar `validar`, los filtros y el orden | Nueva ejecución: 16/16 en verde; con datos y pantalla, 26/26 |
| 3 | Instalación | Al presionar **Run ▶** con el emulador apagado: `Error al ejecutar 'app': 'device offline' error on device serial #emulator-5554 executing service … install-commit` | Android Studio arrancó el emulador e intentó instalar el APK antes de que el dispositivo terminara de iniciar; ADB todavía lo reportaba como `offline` | Esperar a que el emulador termine de arrancar (`adb shell getprop sys.boot_completed` = `1`, `adb devices` = `device`) y volver a ejecutar | Segundo **Run ▶**: `Install successfully finished in 2 s 993 ms` y la app abre en el emulador |

La sincronización de Gradle en Android Studio terminó sin errores. No hubo errores de ejecución: logcat
no muestra crashes al abrir la app, filtrar ni rotar la pantalla.

## Actividad 2

Mismo entorno que la Actividad 1. Las pruebas de la app se ejecutaron en el emulador, con Compose BOM
2026.02.01, Navigation 2.10.2 y Lifecycle 2.11.0.

| # | Etapa | Síntoma | Causa | Corrección | Verificación |
|---|---|---|---|---|---|
| 1 | Pruebas (intencional) | `testDebugUnitTest`: 33 de 57 en rojo (`NotImplementedError` y la regla vieja de 3 caracteres) | Las pruebas se escribieron antes que el código (TDD) | Implementar el repositorio, los ViewModel y la validación del formulario; subir el mínimo del título a 4 | Nueva ejecución: 57/57 en verde |
| 2 | Compilación | `compileDebugKotlin`: `Cannot infer type for this parameter` y `Type checking has run into a recursive problem` en `RepositorioEnMemoria.kt` | La lista inicial no declaraba su tipo y el id del cálculo mezclaba `Int` con `Long` | Declarar `List<Recordatorio>` y usar `0L` | `BUILD SUCCESSFUL` y pruebas en verde |
| 3 | Ejecución (prueba) | Al escribir 201 caracteres con `adb shell input text` en el campo Descripción quedaban menos | No era un error de la app: Android descarta las teclas cuando la ráfaga llega a unos 10 segundos (`InputDispatcher: Dropped event because it is stale`). Los caracteres perdidos coincidieron con los eventos descartados | Ninguna en la app. Se probó un cambio en la recolección del estado, se revirtió por innecesario y la prueba pasó a escribir en tramos cortos | Con tramos cortos: 201/201 caracteres; 25 tipeos cortos seguidos sin pérdidas |
| 4 | Ejecución (prueba) | Lo escrito no llegaba al campo y la pantalla quedaba cubierta | El teclado del emulador abrió su tutorial de lápiz óptico ("Try out your stylus") | Ninguna en la app. Se cerró y se desactivó en el emulador con `settings put secure stylus_handwriting_enabled 0` | Recorrido completo en el emulador: 30/30 verificaciones, sin crashes en logcat |

## Actividad 3

Mismo entorno. Dependencias nuevas: Room 2.8.5 (con KSP 2.3.13), DataStore 1.3.0, Retrofit 3.0.0, OkHttp 5.5.1 y
Gson 2.15.0. Pruebas unitarias en JVM y pruebas instrumentadas del DAO en el emulador.

| # | Etapa | Síntoma | Causa | Corrección | Verificación |
|---|---|---|---|---|---|
| 1 | Pruebas (intencional) | `SincronizadorRecordatoriosTest`: 20 de 21 en rojo (`NotImplementedError`) | Las pruebas se escribieron antes que el sincronizador (TDD); la clase era un esqueleto con `TODO()` | Implementar el sincronizador | 21/21 en verde. Comprobación inversa: sin la exclusión mutua falla la prueba de eventos simultáneos, y con una clave distinta en cada envío falla la de reenvío sin duplicar |
| 2 | Compilación | `Returns are prohibited for functions with an expression body` en `Mapeos.kt` | Un `return null` dentro de una función con cuerpo de expresión | Pasarla a cuerpo de bloque | `BUILD SUCCESSFUL` |
| 3 | Compilación (pruebas) | `Name contains illegal characters: :` | Un nombre de prueba entre acentos graves incluía `:`, que la JVM no admite | Quitar el `:` del nombre | Compila |
| 4 | Pruebas | `eliminar` devolvía `RespuestaInvalida` en lugar de éxito | Un `DELETE` responde 204 sin cuerpo y Retrofit entrega `body() == null`; se trataba como respuesta sin contenido. **Habría impedido borrar nunca en el servicio** | `clasificarRespuesta` reconoce 204 y 205 como éxito sin cuerpo | Prueba de punta a punta de listar, actualizar y eliminar en verde |
| 5 | Pruebas | Una respuesta que no era JSON se clasificaba como `SinConexion` | Gson lanza `MalformedJsonException`, que es una `IOException`, y el `catch` de red la atrapaba primero | Capturar `MalformedJsonException` antes que `IOException` | Prueba de respuesta ilegible en verde |
| 6 | Pruebas | `ClassCastException` al leer una preferencia guardada con otro tipo | El tipo genérico se borra: la conversión ocurría al usar el valor, después del `try` | Leer del mapa con `as?` y un tipo reificado | Prueba de valor con otro tipo en verde |
| 7 | Pruebas | La sincronización pedida al guardar no se ejecutaba en `RecordatoriosRepositoryImplTest` | `advanceUntilIdle()` no espera las tareas de `backgroundScope` | Usar el propio `TestScope` como alcance de la prueba | 10/10 en verde |
| 8 | Pruebas instrumentadas | `NoSuchMethodError: runBlockingK$default` | Las pruebas se compilaron con coroutines 1.11.0 y la app traía una versión anterior a través de Room y DataStore | Declarar `kotlinx-coroutines-android` 1.11.0 como dependencia de la app | 9/9 pruebas del DAO en verde en el emulador |
| 9 | Ejecución | El aviso decía «1 pendientes de enviar» | Texto fijo en plural | Recursos `plurals` | Compila y el aviso concuerda en número |
| 10 | Compilación (empaquetado) | Aviso `Unable to strip ... libdatastore_shared_counter.so` | Igual que el de la Actividad 1: falta el NDK para reducir símbolos | Ninguna: es un aviso | `assembleDebug` y `assembleRelease` terminan con `BUILD SUCCESSFUL` |
| 11 | Ejecución (prueba) | `adb push` y `adb shell` fallaban con rutas como `/data/local/tmp` | El shell de Git para Windows convierte las rutas que empiezan con `/` | Definir `MSYS_NO_PATHCONV=1` | El archivo de preferencias inválido se cargó en el emulador |

El recorrido completo en el emulador (preferencias, creación sin conexión, cierre y reapertura, recuperación de
la conexión, edición, eliminación y los cuatro tipos de fallo) terminó sin crashes en logcat.

## Actividad 4

Mismo entorno. Dependencias nuevas: Firebase BoM 34.19.0 (Authentication y Firestore), kotlinx-coroutines-play-services
1.11.0, plugin Google Services 4.6.0 y plugin de Room 2.8.5. Para el emulador de Firebase: firebase-tools 15.34.1,
@firebase/rules-unit-testing 6.0.0 y firebase 13.1.0 (versiones fijas con más de dos semanas de publicadas).

| # | Etapa | Síntoma | Causa | Corrección | Verificación |
|---|---|---|---|---|---|
| 1 | Compilación (pruebas) | `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0, expected version is 2.2.0` en `firebase-auth-25.0.0` | Firebase BoM 35.x está compilado con Kotlin 2.4; el proyecto usa Kotlin 2.2.10 | Usar Firebase BoM 34.19.0 (la última 34) en lugar de subir Kotlin, que arrastra al plugin de Compose | Compila y las pruebas corren |
| 2 | Pruebas | `NoClassDefFoundError: Could not initialize class FirebaseFirestoreException$Code` y `Method isEmpty in android.text.TextUtils not mocked` | El enum de códigos de Firestore y `FirebaseNetworkException` usan clases de Android al inicializarse; en la JVM no existen | El clasificador trabaja con el **nombre** del código (`String`), que es puro; la conversión desde la excepción queda en la fuente remota | 6/6 pruebas del clasificador en verde |
| 3 | Pruebas (intencional) | 20 de 153 en rojo (`NotImplementedError`) | TDD: el clasificador de errores de autenticación, el mapeo de Firestore, `CoordinadorSesion` y `AccesoViewModel` eran esqueletos con `TODO()` | Implementarlos | 153/153 en verde. Comprobación inversa en el sincronizador: sin el corte por `NoAutorizado`, sin el control de cambio de sesión, sin la detección de borrados o borrando con un listado fallido, falla al menos una prueba en cada caso |
| 4 | Pruebas de reglas | `Could not start Firestore Emulator, port taken` | El puerto 8080 lo usan Docker y WSL en esta PC | Firestore emulado en el 8085 (`firebase.json` y `RecordatoriosApp`) | El emulador arranca |
| 5 | Pruebas de reglas | `Cannot find module ...\firebase\test` | En Windows, `node --test` no acepta una carpeta como argumento | Indicar el archivo `test/reglas.test.js` | 13/13 en verde. Comprobación inversa: con la regla de dueño debilitada a «cualquier sesión», fallan las 4 pruebas de acceso entre cuentas |
| 6 | Ejecución (dev) | Registrarse quedaba «Verificando con el servicio…» y a los 10 s mostraba «No hay conexión»; logcat: `RecaptchaCallWrapper: ... A network error ... has occurred` | **Android 17 exige el permiso de ejecución `ACCESS_LOCAL_NETWORK`** a las apps con targetSdk 37 para conectarse a direcciones locales. `nc 10.0.2.2 9099` funcionaba desde la shell pero vencía con el usuario de la app (`run-as`) | Declarar el permiso solo en el manifest de `dev` y pedirlo al iniciar cuando se usa el emulador. prod no lo necesita | Con el permiso otorgado, `nc` como la app recibe `HTTP/1.1 200 OK` y el registro crea la cuenta en el emulador |
| 7 | Ejecución (dev) | Con la cuenta deshabilitada, la app volvía al acceso **sin** el aviso de sesión inválida | Firebase cierra la sesión por su cuenta cuando `reload()` falla y después la cierra la app: llegan dos avisos. El motivo se «consumía» en el primero y el segundo lo pisaba con `SinSesion(null)` | El motivo pasa a ser un estado propio que se combina con el usuario y se borra recién al volver a ingresar | Deshabilitar la cuenta en el emulador y reabrir: aparece «Tu sesión ya no es válida» |
| 8 | Revisión | El formulario de acceso conservaba en memoria el correo y la contraseña de la sesión anterior | `AccesoViewModel` vive con la actividad, no con la sesión | Vaciar el formulario cuando el ingreso sale bien | Prueba nueva vista en rojo y después en verde |
| 9 | Release | `Lint found fatal errors ... InvalidFragmentVersionForActivityResult` en `assembleProdRelease` | Firebase trae `androidx.fragment` 1.1.0, incompatible con la API de resultados de actividades | Pedir el permiso con `requestPermissions` de la plataforma (no se necesita la respuesta) | `assembleProdRelease` termina con `BUILD SUCCESSFUL` |
| 10 | Ejecución (prueba) | Lo tipeado con `adb shell input text " (offline)"` no llegaba al campo | Los paréntesis rompen el comando en la shell del dispositivo; no es un error de la app | Tipear sin paréntesis | El título editado se guarda |

El recorrido en el emulador (registro, creación, edición sin conexión, sincronización al reconectar sin duplicar,
cierre de sesión, otra cuenta en el mismo teléfono, prueba de acceso entre cuentas, cuenta deshabilitada, alta y
borrado desde otro dispositivo, e instalación nueva que recupera los datos) terminó sin crashes en logcat.

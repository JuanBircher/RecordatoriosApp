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

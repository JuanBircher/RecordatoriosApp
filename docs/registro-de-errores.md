# Registro de errores

Problemas de sincronización, compilación, instalación o ejecución, cada uno con su corrección y la
re-ejecución que la verificó.

## Actividad 1

Entorno: Android Studio 2026.2.1 · SDK Platform 37 · emulador `Medium_Phone_API_37.0` (Android 17, API 37).

| # | Etapa | Síntoma | Causa | Corrección | Verificación |
|---|---|---|---|---|---|
| 1 | Compilación (empaquetado) | Aviso `Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so` en `stripDebugDebugSymbols` | Para quitar los símbolos de las librerías nativas que trae una dependencia de Compose hace falta el NDK, que no está instalado | Ninguna: es un aviso, no un error. La librería se empaqueta sin reducir, lo que solo afecta un poco el tamaño del APK de depuración | `assembleDebug` termina con `BUILD SUCCESSFUL` y la app se instala y ejecuta |
| 2 | Pruebas (intencional) | `testDebugUnitTest` falla con 16 tests en rojo (`NotImplementedError`) | Las pruebas se escribieron **antes** que el código (TDD): las funciones todavía eran `TODO()` | Implementar `validar`, los filtros y el orden | Nueva ejecución: 16/16 en verde; con datos y pantalla, 26/26 |

No hubo errores de sincronización de Gradle, de instalación (`adb install` → `Success`) ni de ejecución
(logcat sin crashes al abrir la app, filtrar y rotar la pantalla).

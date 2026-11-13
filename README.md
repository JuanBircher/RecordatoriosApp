# Recordatorios — Programación Mobile (TECLAB)

App Android de recordatorios, desarrollada en Kotlin con Jetpack Compose y Material 3. Se construyó de forma
incremental a lo largo de los cuatro módulos de la materia; cada entrega queda marcada con un tag (`api-1`,
`api-2`, `api-3`, `api-4`).

**Autor:** Juan Bircher

## Estado actual (Actividad 4: versión candidata 4.0.0)
- **Cuentas con Firebase Authentication** (correo y contraseña): registro, ingreso, sesión que persiste al
  reabrir, cierre de sesión y detección de una sesión que dejó de ser válida.
- **Cloud Firestore** guarda una copia de los recordatorios de cada cuenta en `users/{uid}/reminders/{id}`.
  El `id` del documento es el mismo identificador estable que usa Room (`claveCliente`).
- **Room sigue siendo la fuente de verdad** de la interfaz (local-first): todo funciona sin conexión y se
  sincroniza cuando vuelve la red, sin duplicar.
- **Datos separados por cuenta en el dispositivo**: cada fila de Room tiene dueño. Quien ingresa después en el
  mismo teléfono no ve los recordatorios de la sesión anterior.
- **Reglas de seguridad en Firestore**: una cuenta no puede leer ni escribir datos de otra, aunque modifique la
  app. La interfaz no es la barrera.
- **Dos ambientes (product flavors)**: `dev` contra el Firebase Local Emulator Suite y `prod` contra el proyecto real.
- **Release versionado y firmado**: `versionCode 4`, `versionName 4.0.0`, APK y AAB firmados con una clave que
  vive fuera del repositorio.

## Requisitos
- Android Studio 2026.2 o posterior (su JDK sirve también para el emulador de Firebase).
- Android SDK Platform 37 y un emulador o dispositivo con Android 8.0 (API 26) o superior.
- Node.js 20 o posterior, solo para el emulador de Firebase y las pruebas de las reglas.

## Configuración
Los pasos completos están en [`docs/firebase-setup.md`](docs/firebase-setup.md). En resumen:

| Archivo | Para qué | ¿Se versiona? |
|---|---|---|
| `app/src/prod/google-services.json` | Proyecto Firebase real (flavor prod) | No (se entrega en el zip) |
| `keystore.properties` + `.jks` | Firma del release | **Nunca** ([ejemplo](keystore.properties.example)) |
| `local.properties` | SDK y host del emulador de Firebase | No ([ejemplo](local.properties.example)) |
| `firebase/firestore.rules` | Reglas de seguridad de Firestore | Sí |

Sin `google-services.json` el flavor dev funciona igual (usa el emulador). Sin `keystore.properties`, el
release se genera sin firmar en lugar de fallar.

## Cómo ejecutarla

**Desarrollo (contra el emulador de Firebase):**

```powershell
cd firebase; npm install
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
npx firebase emulators:start --project demo-recordatorios   # interfaz web en http://127.0.0.1:4000
```

En Android Studio, elegir la variante **devDebug** (Build Variants) y **Run ▶**. La app se llama
«Recordatorios DEV» y se instala junto a la de prod. En Android 17, la primera vez pide el permiso de
dispositivos cercanos: es necesario para llegar al emulador que corre en la PC.

**Release (contra Firebase real):**

```powershell
.\gradlew assembleProdRelease bundleProdRelease
adb install app\build\outputs\apk\prod\release\app-prod-release.apk
```

**Pruebas:**

```powershell
.\gradlew testDevDebugUnitTest              # 170 pruebas unitarias (JVM)
.\gradlew connectedDevDebugAndroidTest      # 14 pruebas de Room y la migración (emulador)
cd firebase; npm test                       # 13 pruebas de las reglas de Firestore (emulador de Firebase)
```

## Arquitectura

El esquema completo del recorrido de una operación está en [`docs/arquitectura.md`](docs/arquitectura.md).

```
Compose ──evento──▶ ViewModel ──▶ RecordatoriosRepository ──▶ Room (DAO, filtrado por uid)   fuente de verdad
   ▲                    │                 │     │
   └── StateFlow ◀──────┘◀── Flow ◀───────┘     └──▶ SincronizadorRecordatorios ──▶ FirestoreRemoteDataSource ──▶ Firestore
                                                         (envía pendientes, descarga, detecta borrados)          (reglas por uid)
RaizApp ◀── SesionViewModel ◀── AuthRepository ◀── FirebaseAuthRepository ◀── Firebase Authentication
                                      │
                                      └──▶ uid activo ──▶ repositorio y sincronizador
```

- Ninguna función `@Composable` llama a Firebase: las pantallas ven ViewModel, los ViewModel ven interfaces
  (`RecordatoriosRepository`, `AuthRepository`) y Firebase queda dentro de `data/`.
- Los errores del proveedor se traducen en la capa de datos (`ClasificadorFirestore`, `ClasificadorErrorAuth`) a
  tipos propios (`ResultadoRemoto`, `ErrorAuth`). La interfaz no conoce excepciones ni códigos de Firebase.
- `CoordinadorSesion` decide **cuándo** sincronizar (al ingresar, al recuperar la red, al reabrir) y el
  repositorio decide **cómo**.

```
app/src/main/java/com/juanbircher/recordatorios/
├── domain/          Recordatorio, Categoria, Prioridad, estados, Preferencias, Sesion (EstadoSesion, ErrorAuth)
├── validation/      Reglas de validación
├── data/
│   ├── auth/        AuthRepository, FirebaseAuthRepository, ClasificadorErrorAuth
│   ├── local/       Room: RecordatorioEntity (con uid), RecordatorioDao, RecordatoriosDatabase y MIGRATION_1_2
│   ├── preferences/ DataStore
│   ├── remote/      RecordatoriosRemoteDataSource, FirestoreRemoteDataSource, MapeosFirestore,
│   │                ClasificadorFirestore, RecordatorioDto, ResultadoRemoto
│   ├── sync/        SincronizadorRecordatorios, CoordinadorSesion, Conectividad
│   ├── Mapeos.kt    DTO <-> entidad <-> dominio
│   └── RecordatoriosRepository(.Impl)
├── ui/              acceso/ (ingreso y registro), navegacion/ (RaizApp por sesión), listado, crear, detalle, ajustes
├── RecordatoriosApp.kt  Composición: Firebase (emulador en dev), Room, DataStore, sincronizador, repositorios
└── MainActivity.kt
firebase/            firestore.rules, firebase.json y pruebas de las reglas (Node)
app/schemas/         Esquema exportado de la base (versión 2)
```

## Sincronización con Firestore

| Situación | Room | Firestore | Lo que ve la persona |
|---|---|---|---|
| Crear con conexión | Fila nueva `Pendiente` → `Sincronizado` | Documento `users/{uid}/reminders/{clave}` | El recordatorio y «Sincronizado» |
| Modificar sin conexión | Cambio guardado, `Pendiente` | Sin cambios | «Pendiente de enviar» y aviso «sin conexión» |
| Vuelve la conexión | Pasa a `Sincronizado` | El **mismo** documento se reemplaza | Sincroniza solo |
| Reintento | Sin cambios de identidad | `set()` sobre la misma clave: nunca una copia | Sin duplicados |
| Otro dispositivo crea o borra | Se inserta o se quita la fila | Fuente del cambio | Aparece o desaparece al sincronizar |
| Instalación nueva | Vacía → se descarga | Sin cambios | Ingresa y recupera sus recordatorios |

Garantías: una sola corrida a la vez (mutex), identificador estable del documento, estado por registro, la
descarga no pisa cambios locales pendientes, y los borrados remotos solo se aplican con una lista completa
leída con éxito del servidor. Firestore usa caché en memoria y lee del servidor (`Source.SERVER`): Room es la
única copia local.

## Seguridad

| Concepto | Cómo se resuelve |
|---|---|
| **Autenticación** (quién es) | Firebase Authentication. La app no guarda contraseñas ni tokens; la sesión la administra el SDK |
| **Autorización** (qué puede hacer) | Reglas de Firestore: solo el dueño (`request.auth.uid == userId`) lee y escribe su colección; además se valida el documento (campos permitidos, tipos y largos) y la fecha de modificación la pone el servidor |
| **Protección local** (qué queda en el teléfono) | Room separado por `uid`; el respaldo automático y la transferencia entre dispositivos solo copian las preferencias (no la base ni la sesión); tráfico sin cifrar prohibido salvo hacia el emulador y solo en dev; caché de Firestore en memoria |

- **Cerrar sesión** deja los recordatorios en Room, pero solo los ve su dueño. Los cambios pendientes se envían
  cuando esa cuenta vuelve a ingresar. El formulario de acceso se vacía al ingresar.
- **Sesión inválida**: al abrir la app se consulta al servidor (`reload()`). Si la cuenta fue deshabilitada o
  borrada, la app vuelve al acceso con el aviso «Tu sesión ya no es válida». Si Firestore responde
  `PERMISSION_DENIED`, se vuelve a comprobar la sesión y lo pendiente no se pierde.
- **Prueba entre cuentas**: en dev, Ajustes ofrece «Prueba de autorización». Con la sesión de una cuenta intenta
  leer del servicio la colección de otra, sin pasar por la interfaz normal, y muestra el rechazo del servicio.
- No se usa Android Keystore para datos propios porque la app no guarda ningún secreto. Sí se usa el keystore
  de firma, fuera del repositorio.

## Ambientes y versiones

| Flavor | applicationId | Nombre | Backend |
|---|---|---|---|
| `dev` | `com.juanbircher.recordatorios.dev` | Recordatorios DEV, `4.0.0-dev` | Emulador de Firebase (`demo-recordatorios`) |
| `prod` | `com.juanbircher.recordatorios` | Recordatorios, `4.0.0` | Proyecto Firebase real |

`versionCode` (4) identifica la actualización para el sistema y `versionName` (4.0.0) la entrega para los
testers. El release se optimiza con R8. Sin reglas propias: los documentos de Firestore se mapean a mano, sin
reflexión.

## Base de datos local
La versión 4 migra la base de la versión 3 (Room v1 → v2) sin perder recordatorios:
- agrega `uid` y `enRemoto`, y deja de usar el id del servicio REST anterior;
- las filas viejas quedan sin dueño y las adopta la primera cuenta que ingresa;
- vuelven a `Pendiente` para subirse a Firestore;
- descarta los datos de ejemplo del servidor simulado.

Hay una prueba instrumentada que lo comprueba.

## Decisiones
- **Firestore reemplaza al REST simulado de la Actividad 3** (que queda en el tag `api-3`). La interfaz de la
  fuente remota se mantuvo, ahora recibe el `uid` en cada operación, y crear y modificar son un único `set()`
  idempotente.
- **Emulador de Firebase para desarrollo y pruebas**: no hace falta cuenta ni datos reales para desarrollar, y las
  reglas se prueban de forma automática. El prefijo `demo-` impide que el emulador toque un proyecto real.
- **`minSdk = 26`**, sin inyección de dependencias con librería: `RecordatoriosApp` crea cada pieza una vez.
- **Firebase BoM 34.19.0**: la 35.x está compilada con Kotlin 2.4 y el proyecto usa Kotlin 2.2.10, que no
  puede leer esos metadatos.
- **Permiso de red local solo en dev**: Android 17 lo exige para conectarse al emulador de la PC; prod no lo
  declara.

Los problemas encontrados durante el desarrollo están en [`docs/registro-de-errores.md`](docs/registro-de-errores.md).

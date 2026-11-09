# Recordatorios — Programación Mobile (TECLAB)

App Android de recordatorios contextuales, desarrollada en Kotlin con Jetpack Compose y Material 3.
Se construye de forma incremental a lo largo de los cuatro módulos de la materia; cada entrega queda
marcada con un tag (`api-1`, `api-2`, `api-3`, …).

**Autor:** Juan Bircher

## Estado actual (Actividad 3)
- **Room** guarda los recordatorios: sobreviven al cierre de la app. Es la fuente de verdad de la interfaz.
- **DataStore** guarda tres preferencias: tema (sistema, claro u oscuro), mostrar completados y orden del listado.
- **Repositorio local-first**: la interfaz lee siempre de Room; cada cambio se guarda primero ahí, queda
  marcado como pendiente y se envía al servicio cuando hay conexión.
- **Retrofit + OkHttp** consumen un servicio REST. Por defecto lo simula la propia app (sin red); con una
  configuración se apunta a un servicio real.
- **Sincronización controlada**: una sola corrida a la vez, clave de idempotencia por registro y estado de
  sincronización visible en cada tarjeta (sincronizado, pendiente de enviar, error).
- Pantallas: listado, creación y edición, detalle (completar, editar, eliminar) y ajustes.

Todavía no tiene ubicación ni avisos: llegan en el módulo siguiente.

## Requisitos
- Android Studio 2026.2 o posterior (incluye el JDK que usa Gradle).
- Android SDK Platform 37.
- Un emulador o un dispositivo con Android 8.0 (API 26) o superior.
- Conexión a internet solo para que Gradle descargue las dependencias la primera vez.

## Configuración
El proyecto compila **sin configurar nada**: usa el servicio simulado. Para cambiarlo:

1. Copiar `local.properties.example` y agregar sus líneas a `local.properties` (en la raíz, junto a `gradlew`;
   Android Studio ya lo crea con `sdk.dir`). `local.properties` no se versiona.
2. Ajustar los valores:

| Propiedad | Para qué sirve | Valor predeterminado |
|---|---|---|
| `recordatorios.baseUrl` | URL base del servicio REST (con `https`) | `https://api.recordatorios.example/` |
| `recordatorios.simulado` | `true`: el servicio lo simula la app. `false`: se usa la red real contra `baseUrl` | `true` |

No hay URLs reales, credenciales ni tokens en el código. Si se usa un servicio real, debe exponer los
endpoints de la sección «Servicio remoto» y responder con los DTO descritos allí.

## Cómo ejecutarla
1. En Android Studio: **File › Open** y elegir esta carpeta. Esperar a que termine la sincronización de Gradle.
2. Elegir un emulador en Device Manager (o conectar un teléfono con depuración USB).
3. **Run ▶ app**.

Desde la terminal:

```bash
./gradlew testDebugUnitTest          # pruebas unitarias (no necesitan emulador)
./gradlew connectedDebugAndroidTest  # pruebas del DAO con Room real (necesitan emulador o dispositivo)
./gradlew installDebug               # compila e instala en el dispositivo conectado
```

## Estructura

```
app/src/main/java/com/juanbircher/recordatorios/
├── domain/       Modelos de dominio: Recordatorio, Categoria, Prioridad, EstadoRecordatorio,
│                 EstadoSincronizacion, Preferencias, filtros y orden
├── validation/   Reglas de validación y sus resultados
├── data/
│   ├── RecordatoriosRepository(.Impl)   Interfaz y repositorio local-first
│   ├── Mapeos.kt                        DTO <-> entidad <-> dominio, explícitos
│   ├── local/        Room: RecordatorioEntity, RecordatorioDao, RecordatoriosDatabase
│   ├── preferences/  DataStore: PreferenciasDataSource y claves tipadas
│   ├── remote/       Retrofit: RecordatoriosApi, DTO, ResultadoRemoto, RetrofitRemoteDataSource
│   │                 y el servicio simulado (ServidorSimulado, InterceptorServidorSimulado)
│   └── sync/         SincronizadorRecordatorios y Conectividad
├── ui/               Pantallas, ViewModel y UiState: listado, crear (y editar), detalle, ajustes
├── RecordatoriosApp.kt  Arma la capa de datos (Room, DataStore, Retrofit, repositorio)
└── MainActivity.kt      Aplica el tema guardado
app/src/test/         125 pruebas unitarias JUnit
app/src/androidTest/  9 pruebas del DAO con Room y SQLite reales
```

## Flujo de datos

```
Compose ──evento (lambda)──▶ ViewModel ──▶ RecordatoriosRepository ──▶ Room (DAO)   fuente de verdad
   ▲                            │                  │   │
   └─ collectAsStateWithLifecycle ◀── StateFlow ◀──┘   └─▶ DataStore (preferencias)
                                                   │
                                                   └──▶ SincronizadorRecordatorios ──▶ Retrofit/OkHttp ──▶ servicio
                                                          (envía pendientes, descarga y actualiza Room)
```

La pantalla nunca ve el DAO ni los DTO. Cuando el sincronizador cambia Room, el `Flow` del DAO vuelve a emitir
y la pantalla se actualiza sola.

## Persistencia

**DataStore** (configuración simple) y **Room** (registros estructurados) no se mezclan.

| Preferencia | Valores | Predeterminado |
|---|---|---|
| Tema | Seguir al sistema, Claro, Oscuro | Seguir al sistema |
| Mostrar completados | Sí / No | Sí |
| Orden del listado | Por prioridad, por fecha límite, más recientes primero | Por prioridad |

Un valor ausente, desconocido o guardado con otro tipo se reemplaza por el predeterminado, sin fallar. Un
error de lectura recuperable también produce los valores predeterminados.

## Servicio remoto

| Operación | Endpoint | Respuestas que interpreta la app |
|---|---|---|
| Listar | `GET recordatorios` | 200 |
| Crear | `POST recordatorios` con `Idempotency-Key` | 201 (creado), 200 (la clave ya existía: devuelve el registro) |
| Actualizar | `PUT recordatorios/{id}` | 200, 404 |
| Eliminar | `DELETE recordatorios/{id}` | 204, 404 |

La capa de datos distingue éxito, solicitud inválida (4xx), no encontrado (404), error del servidor (5xx),
timeout, falta de conexión y respuesta ilegible. Las pantallas solo ven un aviso recuperable.

## Sincronización sin duplicados
1. **Una corrida a la vez**: si varios eventos piden sincronizar juntos, uno corre y los demás se anotan.
2. **Clave de idempotencia**: cada recordatorio nace con un UUID (`claveCliente`) que viaja al crear. Reenviar
   la misma creación no genera un segundo registro.
3. **Estado por registro**: `Sincronizado` solo cuando el servicio lo confirmó; `Pendiente` mientras no; `Error`
   cuando el servicio rechazó el dato o se agotaron los intentos.
4. **La descarga busca por clave**: lo que ya existe se actualiza. Un cambio local pendiente no se pisa.
5. **Borrado diferido**: un recordatorio eliminado se oculta de inmediato y se borra del servicio cuando se puede.

La sincronización corre al abrir la app, al recuperar la conexión, después de cada cambio y desde el botón de
actualizar. Un fallo transitorio (sin red, timeout, falla del servidor) deja el registro pendiente; tres fallos
seguidos lo pasan a error y espera un reintento manual.

## Cómo comprobar cada situación

El menú ⋮ del listado simula el servicio: **Servicio normal**, **Sin conexión**, **Timeout**, **Error del
servidor (500)** y **Rechaza lo enviado (400)**. También se puede activar el modo avión del emulador: la app
detecta la falta de conexión y sincroniza sola al volver. El botón de actualizar fuerza una sincronización.

## Reglas de validación

| Campo | Regla |
|---|---|
| Título | Obligatorio; de 4 a 60 caracteres, sin contar los espacios de los bordes |
| Descripción | Opcional; hasta 200 caracteres |
| Fecha límite | Opcional; no puede ser anterior a la fecha de creación |
| Categoría | Obligatoria; tiene que existir en el catálogo de categorías |
| Prioridad | Obligatoria en el formulario |

El servicio simulado valida además el título (4 a 60 caracteres) y responde 400 si no se cumple.

## Decisiones
- **Servicio simulado en la propia app**: un interceptor de OkHttp responde en nombre del servicio. Retrofit,
  OkHttp, los DTO y la clasificación de respuestas son los mismos que con un servicio real; no se depende de
  un tercero ni de la red del equipo evaluador.
- **`POST` con clave de idempotencia** en lugar de crear con `PUT`: es el patrón habitual y permite reenviar sin
  riesgo.
- **Lo que el servicio ya no tiene no se borra localmente**: la descarga agrega y actualiza, pero no elimina los
  recordatorios sincronizados que desaparecen del servicio. Es una limitación conocida.
- **`minSdk = 26`**: permite usar `java.time.LocalDate` sin desugaring.
- **Versiones**: las genera el asistente de Android Studio 2026.2 (AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10,
  Compose BOM 2026.02.01) más las de Room, DataStore, Retrofit y OkHttp. Están en `gradle/libs.versions.toml`.
- **Sin inyección de dependencias con librería**: `RecordatoriosApp` crea cada pieza una sola vez y
  `FabricasViewModel` entrega el repositorio; es el único lugar que hay que cambiar para cambiar de fuente.

Los problemas encontrados durante el desarrollo están en [`docs/registro-de-errores.md`](docs/registro-de-errores.md).

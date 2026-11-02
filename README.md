# Recordatorios — Programación Mobile (TECLAB)

App Android de recordatorios contextuales, desarrollada en Kotlin con Jetpack Compose y Material 3.
Se construye de forma incremental a lo largo de los cuatro módulos de la materia; cada entrega queda
marcada con un tag (`api-1`, `api-2`, …).

**Autor:** Juan Bircher

## Estado actual (Actividad 2)
- Tres pantallas conectadas con Navigation Compose: listado, creación y detalle.
- Listado con estados de carga, vacío, datos y error con reintento, y filtros por estado y prioridad.
- Formulario validado: el botón Guardar se deshabilita si es inválido o mientras se guarda.
- Arquitectura de presentación con ViewModel, UiState sellado y StateFlow.
- Repositorio en memoria detrás de una interfaz: los datos se pierden al cerrar la app.

Todavía no tiene persistencia, sincronización, ubicación ni avisos: llegan en los módulos siguientes.

## Requisitos
- Android Studio 2026.2 o posterior (incluye el JDK que usa Gradle).
- Android SDK Platform 37.
- Un emulador o un dispositivo con Android 8.0 (API 26) o superior.

## Cómo ejecutarla
1. En Android Studio: **File › Open** y elegir esta carpeta. Esperar a que termine la sincronización de Gradle.
2. Elegir un emulador en Device Manager (o conectar un teléfono con depuración USB).
3. **Run ▶ app**.

Desde la terminal:

```bash
./gradlew testDebugUnitTest   # pruebas unitarias (no necesitan emulador)
./gradlew installDebug        # compila e instala en el dispositivo conectado
```

## Estructura

```
app/src/main/java/com/juanbircher/recordatorios/
├── domain/       Modelos: Recordatorio, Categoria, Prioridad, EstadoRecordatorio y filtros
├── validation/   Reglas de validación y sus resultados (ResultadoValidacion, ErrorValidacion)
├── data/         Interfaz del repositorio, implementación en memoria, simulador y datos de ejemplo
├── ui/
│   ├── listado/     PantallaListado, ListadoViewModel y ListadoUiState
│   ├── crear/       PantallaCrear, CrearViewModel y CrearUiState
│   ├── detalle/     PantallaDetalle, DetalleViewModel y DetalleUiState
│   ├── componentes/ TarjetaRecordatorio, MensajeDeEstado y campos del formulario
│   ├── navegacion/  AppNavHost y rutas
│   └── theme/       Tema de Material 3
├── RecordatoriosApp.kt  Elige la fuente de datos
└── MainActivity.kt
app/src/test/     Pruebas unitarias JUnit de dominio, validación, datos y ViewModel
```

`domain`, `validation` y `data` no dependen de Android, así que se prueban sin emulador. El Map de
categoría a color vive en `ui` porque `Color` pertenece a Compose.

## Arquitectura de presentación

La pantalla dibuja el `UiState` que recibe y avisa cada acción con una lambda. El ViewModel recibe el
evento, valida, le pide datos al repositorio y publica una nueva instancia del estado:

```
Pantalla ── evento (lambda) ──▶ ViewModel ── suspend / Flow ──▶ RecordatoriosRepository
   ▲                               │                                       ▲
   └── collectAsStateWithLifecycle ┘ StateFlow<UiState>     RepositorioEnMemoria (implementa)
```

## Navegación

| Ruta | Destino | Argumentos |
|---|---|---|
| `listado` | Listado (destino inicial) | Ninguno |
| `crear` | Formulario de creación | Ninguno |
| `detalle/{recordatorioId}` | Detalle | `recordatorioId`: `Long` |

## Cómo comprobar cada estado

El menú ⋮ del listado permite elegir un escenario: **Con datos de ejemplo** restaura los datos,
**Lista vacía** los quita, y **Error al cargar** y **Error al guardar** fallan una sola vez, para que el
reintento funcione. Se prueban sin base de datos ni servicio remoto.

## Reglas de validación

| Campo | Regla |
|---|---|
| Título | Obligatorio; de 4 a 60 caracteres, sin contar los espacios de los bordes |
| Descripción | Opcional; hasta 200 caracteres |
| Fecha límite | Opcional; no puede ser anterior a la fecha de creación |
| Categoría | Obligatoria; tiene que existir en el catálogo de categorías |
| Prioridad | Obligatoria en el formulario |

Un recordatorio inválido informa **todos** sus errores juntos, no solo el primero.

## Decisiones
- **`minSdk = 26`**: permite usar `java.time.LocalDate` sin desugaring y cubre prácticamente todos los
  dispositivos en uso.
- **Versiones**: las genera el asistente de Android Studio 2026.2 (AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10,
  Compose BOM 2026.02.01). Están centralizadas en `gradle/libs.versions.toml`.
- **Rutas de texto** en Navigation Compose: alcanzan para tres destinos y un argumento, sin sumar el
  plugin de serialización.
- **Sin inyección de dependencias con librería**: `RecordatoriosApp` crea el repositorio y
  `FabricasViewModel` lo entrega; es el único lugar que hay que cambiar al sumar otra fuente de datos.

Los problemas encontrados durante el desarrollo están en [`docs/registro-de-errores.md`](docs/registro-de-errores.md).

# Recordatorios — Programación Mobile (TECLAB)

App Android de recordatorios contextuales, desarrollada en Kotlin con Jetpack Compose y Material 3.
Se construye de forma incremental a lo largo de los cuatro módulos de la materia; cada entrega queda
marcada con un tag (`api-1`, `api-2`, …).

**Autor:** Juan Bircher

## Estado actual (Actividad 1)
- Modelo de dominio inmutable: recordatorios, categorías, prioridades y estados.
- Reglas de validación con resultados tipados.
- Datos simulados que se validan al cargarse: los inválidos se descartan y la pantalla informa el motivo.
- Pantalla inicial con la lista de recordatorios y filtros por estado y prioridad.

Todavía no tiene formularios, navegación, persistencia ni conexión: llegan en los módulos siguientes.

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
├── data/         Datos simulados y carga validada (CargadorRecordatorios)
├── ui/           Pantalla Compose, tarjeta, estado de pantalla y colores por categoría
└── MainActivity.kt
app/src/test/     Pruebas unitarias JUnit de dominio, validación, datos y estado de pantalla
```

`domain`, `validation` y `data` no dependen de Android, así que se prueban sin emulador. El Map de
categoría a color vive en `ui` porque `Color` pertenece a Compose.

## Reglas de validación

| Campo | Regla |
|---|---|
| Título | Obligatorio; de 3 a 60 caracteres, sin contar los espacios de los bordes |
| Descripción | Opcional; hasta 200 caracteres |
| Fecha límite | Opcional; no puede ser anterior a la fecha de creación |
| Categoría | Tiene que existir en el catálogo de categorías |

Un recordatorio inválido informa **todos** sus errores juntos, no solo el primero.

## Decisiones
- **`minSdk = 26`**: permite usar `java.time.LocalDate` sin desugaring y cubre prácticamente todos los
  dispositivos en uso.
- **Versiones**: las genera el asistente de Android Studio 2026.2 (AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10,
  Compose BOM 2026.02.01). Están centralizadas en `gradle/libs.versions.toml`.

Los problemas encontrados durante el desarrollo están en [`docs/registro-de-errores.md`](docs/registro-de-errores.md).

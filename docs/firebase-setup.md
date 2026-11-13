# Configuración de Firebase, emulador y firma

Cómo dejar el proyecto listo para compilar y ejecutar cada variante. Ningún paso guarda secretos en el
repositorio.

| Variante | Backend | Qué necesita |
|---|---|---|
| `devDebug` | Firebase Local Emulator Suite en la PC (proyecto `demo-recordatorios`) | Node y Java (la JBR de Android Studio) |
| `prodDebug` / `prodRelease` | Proyecto Firebase real | `app/src/prod/google-services.json` |
| `prodRelease` firmado | Proyecto Firebase real | además, `keystore.properties` y el `.jks` |

## 1. Ambiente de desarrollo: emulador de Firebase

El flavor `dev` no usa `google-services.json`: se configura solo contra el emulador, con un proyecto
`demo-…` que no puede tocar datos reales.

```powershell
cd firebase
npm install                      # instala firebase-tools y las librerías de prueba en firebase/node_modules
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
npx firebase emulators:start --project demo-recordatorios
```

- Auth en `127.0.0.1:9099`, Firestore en `127.0.0.1:8085` (el 8080 suele estar ocupado por Docker o WSL)
  e interfaz web en <http://127.0.0.1:4000> (usuarios, documentos, deshabilitar cuentas).
- En Android Studio, elegir la variante **devDebug** y ejecutar. La primera vez, Android 17 pide permiso para
  acceder a dispositivos cercanos (`ACCESS_LOCAL_NETWORK`): sin ese permiso, la app no llega al emulador de
  la PC y todo falla como si no hubiera conexión. Solo dev lo pide; prod no.
- Pruebas de las reglas de seguridad: `npm test` (dentro de `firebase/`).

## 2. Proyecto Firebase real (flavor prod)

En <https://console.firebase.google.com>:

1. **Crear proyecto** (por ejemplo, `recordatorios-app`). Google Analytics no hace falta.
2. **Agregar app → Android**:
   - Nombre del paquete: `com.juanbircher.recordatorios` (sin `.dev`).
   - Descargar `google-services.json` y guardarlo en **`app/src/prod/google-services.json`**.
   - Los pasos «Agregar el SDK» del asistente se saltean: el proyecto ya está configurado.
3. **Authentication → Comenzar → Método de acceso → Correo electrónico/contraseña → Habilitar** (sin
   «vínculo de correo electrónico»).
4. **Firestore Database → Crear base de datos**: modo producción, en una ubicación cercana (por ejemplo
   `southamerica-east1`).
5. **Firestore → Reglas**: reemplazar el contenido por el de [`firebase/firestore.rules`](../firebase/firestore.rules)
   y **Publicar**. También se pueden publicar desde la consola de comandos con
   `npx firebase login` y `npx firebase deploy --only firestore:rules --project <id-del-proyecto>`.

`google-services.json` identifica el proyecto, pero no es la protección: lo que impide leer datos ajenos
son las reglas del paso 5. Aun así queda fuera de git (`.gitignore`) y solo se incluye en el zip de entrega.

## 3. Firma del release

La clave se crea una sola vez y se guarda **fuera del proyecto**. Quien la tenga puede publicar
actualizaciones de la app, así que no se versiona ni se entrega.

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$env:USERPROFILE\.android\recordatorios-release.jks" `
  -alias recordatorios -keyalg RSA -keysize 2048 -validity 10000
```

Después copiar [`keystore.properties.example`](../keystore.properties.example) como `keystore.properties`
(en la raíz del proyecto) y completar la ruta, el alias y las contraseñas. Gradle firma solo:

```powershell
.\gradlew assembleProdRelease bundleProdRelease
```

- APK: `app/build/outputs/apk/prod/release/app-prod-release.apk`
- AAB: `app/build/outputs/bundle/prodRelease/app-prod-release.aab`

Sin `keystore.properties` el build no falla: genera `app-prod-release-unsigned.apk`, que Android no instala.

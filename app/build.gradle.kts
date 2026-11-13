import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

/** Lee un archivo .properties de la raíz del proyecto; si no existe, queda vacío. Ninguno se versiona. */
fun propiedades(nombre: String) = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file(nombre))
        .asText.orNull?.let { load(StringReader(it)) }
}

// Host del Firebase Local Emulator Suite para el flavor dev. 10.0.2.2 es la PC vista desde el emulador de
// Android; en un teléfono físico va la IP de la PC en la red local. Ver local.properties.example.
val hostEmulador = propiedades("local.properties").getProperty("firebase.emulador.host", "10.0.2.2")

// Firma del release: las credenciales viven en keystore.properties (fuera de git) y el .jks fuera del
// proyecto. Si el archivo falta, el release se genera sin firmar en lugar de romper el build.
val firma = propiedades("keystore.properties")
val hayFirma = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !firma.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.juanbircher.recordatorios"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.juanbircher.recordatorios"
        minSdk = 26
        targetSdk = 37
        // versionCode: entero que el sistema usa para ordenar las actualizaciones (siempre crece).
        // versionName: lo que leen los testers y aparece en los reportes.
        versionCode = 4
        versionName = "4.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hayFirma) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    // Dos ambientes estables: dev trabaja contra el emulador de Firebase en la PC (nunca toca datos reales)
    // y prod contra el proyecto Firebase real. Se instalan uno al lado del otro por el sufijo del paquete.
    flavorDimensions += "ambiente"
    productFlavors {
        create("dev") {
            dimension = "ambiente"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "USAR_EMULADOR", "true")
            buildConfigField("String", "EMULADOR_HOST", "\"$hostEmulador\"")
        }
        create("prod") {
            dimension = "ambiente"
            buildConfigField("boolean", "USAR_EMULADOR", "false")
            buildConfigField("String", "EMULADOR_HOST", "\"\"")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            if (hayFirma) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// El esquema de cada versión de la base se exporta y se versiona: documenta la migración y permite probarla.
room {
    schemaDirectory("$projectDir/schemas")
}

// dev no usa google-services.json (se configura a mano contra el emulador). En prod el archivo va en
// app/src/prod/ y no se versiona: si falta, el plugin solo avisa (así compilan dev y las pruebas)...
googleServices {
    missingGoogleServicesStrategy = com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy.WARN
}

// ...pero un release de prod sin configuración de Firebase se cerraría al abrir: ese build se corta acá.
val hayConfiguracionFirebaseProd = file("src/prod/google-services.json").exists()
val verificarFirebaseProd = tasks.register("verificarConfiguracionFirebaseProd") {
    description = "Corta el release de prod si falta app/src/prod/google-services.json."
    val existe = hayConfiguracionFirebaseProd
    // Sin salidas declaradas, Gradle la ejecuta siempre (nunca queda «al día»).
    doLast {
        if (!existe) {
            throw GradleException(
                "Falta app/src/prod/google-services.json: el release de prod no puede funcionar sin él. " +
                    "Ver docs/firebase-setup.md."
            )
        }
    }
}
tasks.matching { it.name == "processProdReleaseGoogleServices" }.configureEach { dependsOn(verificarFirebaseProd) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
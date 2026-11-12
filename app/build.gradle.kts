import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
}

// Firebase-Plugin nur anwenden, wenn die (gitignored) google-services.json vorliegt.
// So baut das Repo auch aus einem frischen Klon ohne Firebase-Config; ohne die Datei
// ist FCM/Push deaktiviert, die App baut und laeuft normal.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val tmdbApiKey: String = localProperties.getProperty("tmdb.api.key")
    ?: System.getenv("TMDB_API_KEY")
    ?: ""
// FCM-Tickle-Server (server/): Standard-URL + API-Key. Der Key landet in der APK
// und ist damit faktisch öffentlich; er hält nur Zufalls-Scanner fern.
val tickleServerUrl: String = localProperties.getProperty("tickle.server.url")
    ?: System.getenv("TICKLE_SERVER_URL")
    ?: "https://tickle.transio.de"
val tickleApiKey: String = localProperties.getProperty("tickle.api.key")
    ?: System.getenv("TICKLE_API_KEY")
    ?: ""

// === Versionierung ===
// Release-Builds bekommen die Version aus dem Git-Tag: CI setzt HIUNI_VERSION_NAME
// (Gradle-Property `-PHIUNI_VERSION_NAME=1.4.2` oder gleichnamige Env-Variable) aus
// dem Tag `v1.4.2`. Format MAJOR.MINOR.PATCH, optional mit Suffix (`1.5.0-beta1`).
// versionCode = MAJOR * 10000 + MINOR * 100 + PATCH (1.4.2 -> 10402). Der Suffix
// fließt nicht in den versionCode ein. Ohne Property (lokale Builds): 0.0.0-dev / 1.
data class HiUniVersion(val name: String, val code: Int)

val hiuniVersion: HiUniVersion = run {
    val raw = (providers.gradleProperty("HIUNI_VERSION_NAME").orNull
        ?: System.getenv("HIUNI_VERSION_NAME"))
        ?.trim()
        ?.removePrefix("v")
        ?.takeIf { it.isNotEmpty() }
        ?: return@run HiUniVersion(name = "0.0.0-dev", code = 1)
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?$""").matchEntire(raw)
        ?: throw GradleException(
            "HIUNI_VERSION_NAME '$raw' ist ungültig, erwartet MAJOR.MINOR.PATCH[-suffix], z.B. 1.4.2"
        )
    val (major, minor, patch) = match.groupValues.subList(1, 4).map { it.toInt() }
    if (minor > 99 || patch > 99) {
        throw GradleException(
            "HIUNI_VERSION_NAME '$raw': MINOR und PATCH müssen zwischen 0 und 99 liegen"
        )
    }
    if (major > 20_000) {
        throw GradleException("HIUNI_VERSION_NAME '$raw': MAJOR ist zu groß für den versionCode")
    }
    val code = major * 10_000 + minor * 100 + patch
    if (code < 1) {
        throw GradleException("HIUNI_VERSION_NAME '$raw' ergibt versionCode 0, mindestens 0.0.1 nötig")
    }
    HiUniVersion(name = raw, code = code)
}

// === Release-Signing ===
// Reihenfolge: Env-Variablen (CI) -> keystore.properties im Repo-Root (lokal) -> unsigniert.
// Ein unvollständiges Set führt nicht zum Fehler, der Release-APK bleibt dann unsigniert.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("HIUNI_KEYSTORE_FILE", "storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }
val releaseStorePassword = signingValue("HIUNI_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("HIUNI_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("HIUNI_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

if (!hasReleaseSigning && gradle.startParameter.taskNames.any { it.contains("Release") }) {
    logger.warn(
        "HiUni: kein vollständiger Release-Keystore (HIUNI_KEYSTORE_* bzw. keystore.properties), " +
            "Release-APK bleibt unsigniert."
    )
}

android {
    namespace = "de.transio.hiuni"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.transio.hiuni"
        minSdk = 28
        targetSdk = 36
        versionCode = hiuniVersion.code
        versionName = hiuniVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "TICKLE_SERVER_URL", "\"$tickleServerUrl\"")
        buildConfigField("String", "TICKLE_API_KEY", "\"$tickleApiKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk 28: v1-Signatur (JAR) ist nicht nötig
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // null ohne Keystore: unsignierter Release-Build statt Build-Fehler
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room-Migrations-Tests (MigrationTestHelper) laufen als JVM/Robolectric-Unit-Tests
    // und laden die exportierten Schema-JSONs aus $projectDir/schemas über den
    // Assets-Loader des Robolectric-Contexts. Robolectric liest Assets aus den
    // *gemergten Debug-Assets* (mergeDebugAssets), NICHT aus dem test-SourceSet —
    // daher hängen wir die Schemas an das debug-SourceSet. Nur Debug-Builds tragen
    // die Schemas dann in ihren Assets; der Release-APK bleibt unberührt.
    sourceSets {
        getByName("debug") {
            assets.srcDirs("$projectDir/schemas")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/NOTICE.md",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties"
            )
        }
    }

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // === Core / Lifecycle / Activity ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // MainActivity : FragmentActivity (BiometricPrompt) — explizit, nicht nur transitiv.
    implementation(libs.androidx.fragment)

    // === Compose (BOM-managed) ===
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsize)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.window)

    // === Navigation ===
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // === Home-Screen-Widgets (Glance) ===
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // === Hilt ===
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // === Room ===
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // === DataStore + Security + WorkManager ===
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.sqlcipher.android)

    // === Kotlinx ===
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // === Networking ===
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // === Firebase Cloud Messaging (FCM) — Mail-Push / Tickle-Modell ===
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // === Scraping ===
    implementation(libs.jsoup)

    // === Images ===
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)

    // === Email ===
    implementation(libs.angus.mail)
    implementation(libs.jakarta.mail.api)

    // === iCalendar ===
    implementation(libs.biweekly)

    // === Logging ===
    implementation(libs.timber)

    // === Unit Tests ===
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)

    // === Instrumented Tests ===
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // === Debug-only ===
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)
}

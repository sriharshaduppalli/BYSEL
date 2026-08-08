import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
}

apply {
    plugin("kotlin-kapt")
}

fun bumpPatchVersion(versionName: String): String {
    val parts = versionName.split('.').toMutableList()
    if (parts.size >= 3) {
        val patch = parts.last().toIntOrNull() ?: 0
        parts[parts.size - 1] = (patch + 1).toString()
    } else {
        while (parts.size < 3) parts.add("0")
        val patch = parts.last().toIntOrNull() ?: 0
        parts[parts.size - 1] = (patch + 1).toString()
    }
    return parts.joinToString(".")
}

/**
 * Play track series (versionName major):
 *   internal → 2.x.x
 *   closed   → 3.x.x
 *   open     → 4.x.x
 *
 * versionCode is GLOBAL and always increases (Play requirement across all tracks).
 * Build with:  .\gradlew.bat :app:bundleRelease -Ptrack=closed
 */
enum class ReleaseTrack(val key: String, val major: Int, val versionNameProp: String) {
    INTERNAL("internal", 2, "VERSION_NAME_INTERNAL"),
    CLOSED("closed", 3, "VERSION_NAME_CLOSED"),
    OPEN("open", 4, "VERSION_NAME_OPEN");

    companion object {
        fun from(raw: String?): ReleaseTrack {
            val key = raw?.trim()?.lowercase().orEmpty()
            return values().firstOrNull { it.key == key } ?: INTERNAL
        }
    }
}

fun ensureTrackMajor(versionName: String, major: Int): String {
    val parts = versionName.split('.').toMutableList()
    while (parts.size < 3) parts.add("0")
    parts[0] = major.toString()
    return parts.joinToString(".")
}

val versionPropsFile = rootProject.file("gradle.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}

val releaseTrack = ReleaseTrack.from(
    (project.findProperty("track") as? String)
        ?: versionProps.getProperty("RELEASE_TRACK")
        ?: "internal"
)

val baseVersionCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toIntOrNull() ?: 1
val legacyVersionName = versionProps.getProperty("VERSION_NAME") ?: "2.0.0"
val baseTrackVersionName = ensureTrackMajor(
    versionProps.getProperty(releaseTrack.versionNameProp)
        ?: if (releaseTrack == ReleaseTrack.INTERNAL) legacyVersionName else "${releaseTrack.major}.0.0",
    releaseTrack.major,
)

val requestedTaskNamesLower = gradle.startParameter.taskNames.joinToString(" ").lowercase()
val isBundleReleaseRequested = requestedTaskNamesLower.contains("bundlerelease")
// Global versionCode — must rise for every Play upload on any track.
val configuredVersionCode = if (isBundleReleaseRequested) baseVersionCode + 1 else baseVersionCode
val configuredVersionName =
    if (isBundleReleaseRequested) bumpPatchVersion(baseTrackVersionName) else baseTrackVersionName

println(
    "Release track=${releaseTrack.key} versionName=$configuredVersionName " +
        "versionCode=$configuredVersionCode (bundleRelease bump=$isBundleReleaseRequested)"
)

android {
    namespace = "com.bysel.trader"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.bysel.trader"
        minSdk = 24
        targetSdk = 36
        val certPinHost = System.getenv("CERT_PIN_HOST") ?: "bysel-backend.onrender.com"
        val certPinPrimary = System.getenv("CERT_PIN_PRIMARY") ?: ""
        val certPinBackup = System.getenv("CERT_PIN_BACKUP") ?: ""
        // Read version from root gradle.properties, but for bundleRelease we pre-bump
        // here so the built AAB and gradle.properties stay in sync in one run.
        versionCode = configuredVersionCode
        versionName = configuredVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "MARKET_REST_URL", "\"https://bysel-backend.onrender.com/\"")
        buildConfigField("String", "MARKET_WS_URL", "\"wss://bysel-backend.onrender.com/ws/quotes\"")
        buildConfigField("String", "MARKET_DATA_PROVIDER", "\"REST_FALLBACK\"")
        buildConfigField("String", "MARKET_TRUEDATA_WS_URL", "\"wss://push.truedata.in\"")
        buildConfigField("String", "MARKET_TRUEDATA_TOKEN", "\"\"")
        buildConfigField("String", "CHART_ENGINE", "\"COMPOSE\"")
        buildConfigField("String", "CERT_PIN_HOST", "\"$certPinHost\"")
        buildConfigField("String", "CERT_PIN_PRIMARY", "\"$certPinPrimary\"")
        buildConfigField("String", "CERT_PIN_BACKUP", "\"$certPinBackup\"")
    }

    // Persist the exact release version that bundleRelease used.
    tasks.matching { it.name == "bundleRelease" }.configureEach {
        doLast {
            if (!versionPropsFile.exists()) {
                println("gradle.properties not found at ${versionPropsFile.absolutePath}")
                return@doLast
            }

            val props = Properties()
            versionPropsFile.inputStream().use { props.load(it) }
            props.setProperty("VERSION_CODE", configuredVersionCode.toString())
            props.setProperty("VERSION_NAME", configuredVersionName)
            props.setProperty("RELEASE_TRACK", releaseTrack.key)
            props.setProperty(releaseTrack.versionNameProp, configuredVersionName)
            // Keep legacy VERSION_NAME aligned with the track that was just built.
            versionPropsFile.outputStream().use { props.store(it, null) }

            val aab = layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile
            val copyName = "bysel-v${configuredVersionName}-${releaseTrack.key}-release.aab"
            val copyTarget = rootProject.file("../$copyName")
            if (aab.exists()) {
                aab.copyTo(copyTarget, overwrite = true)
                println("Copied AAB → ${copyTarget.normalize().absolutePath}")
            }
            println(
                "Persisted track=${releaseTrack.key} VERSION_CODE=${configuredVersionCode} " +
                    "VERSION_NAME=${configuredVersionName}"
            )
        }
    }

    // Load keystore properties from project root `keystore.properties` or environment variables.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) {
            load(FileInputStream(keystorePropsFile))
        }
    }

    val requiresReleaseSigning = requestedTaskNamesLower.contains("release") || requestedTaskNamesLower.contains("bundle") || requestedTaskNamesLower.contains("publish")
    val configuredStoreFilePath = keystoreProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
    val configuredStorePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
    val configuredKeyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
    val configuredKeyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
    val hasReleaseSigningConfig = !configuredStoreFilePath.isNullOrBlank() &&
        !configuredStorePassword.isNullOrBlank() &&
        !configuredKeyAlias.isNullOrBlank() &&
        !configuredKeyPassword.isNullOrBlank()

    if (requiresReleaseSigning && !hasReleaseSigningConfig) {
        throw GradleException(
            "Keystore not configured for release build. " +
                "Create android/keystore.properties (see android/keystore.properties.example) " +
                "or set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars."
        )
    }

    signingConfigs {
        create("release") {
            if (!hasReleaseSigningConfig) {
                return@create
            }

            storeFile = file(configuredStoreFilePath!!)
            storePassword = configuredStorePassword
            keyAlias = configuredKeyAlias
            keyPassword = configuredKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Include native debug symbols in the AAB so Play Console can
            // symbolicate native crashes/ANRs (avoids the missing-symbols warning).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                // Allow debug/CI tasks to configure the project without release secrets.
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:network"))

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore-core:1.0.0")
    // Jetpack Compose BOM for version alignment
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    // Custom Tabs for Privacy/Terms/website
    implementation("androidx.browser:browser:1.8.0")
    // Credential Manager (password autofill)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    // Paging for large quote/news lists
    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    implementation("androidx.paging:paging-compose:3.3.0")
    // Window insets / foldables
    implementation("androidx.window:window:1.3.0")
    
    // Gesture support - HorizontalPager for swipeable tabs
    implementation("androidx.compose.foundation:foundation:1.6.8")
    
    // Modern splash screen API
    implementation("androidx.core:core-splashscreen:1.0.1")
    
// Google Play Core modules (SDK 34+ compatible: use latest modular APIs)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Biometric authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Jetpack Glance — home screen widget
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Encrypted storage for auth tokens
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.retrofit2:retrofit:2.10.0")
    implementation("com.squareup.retrofit2:converter-gson:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Hilt dependencies removed — app uses manual ViewModelProvider.Factory
    // implementation("com.google.dagger:hilt-android:2.51.1")
    // kapt("com.google.dagger:hilt-compiler:2.51.1")
    // implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    // Firebase BoM — single version for all Firebase libs
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    // SMS Retriever / User Consent API (no SMS permission required)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:17.5.0")
    // On-device LLM inference via MediaPipe (runs Gemma on device — no server cost)
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.23")
    testImplementation("org.robolectric:robolectric:4.11")
    testImplementation("androidx.work:work-testing:2.8.1")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

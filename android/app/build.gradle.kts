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

val versionPropsFile = rootProject.file("gradle.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val baseVersionCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toIntOrNull() ?: 1
val baseVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.0"

val requestedTaskNamesLower = gradle.startParameter.taskNames.joinToString(" ").lowercase()
val isBundleReleaseRequested = requestedTaskNamesLower.contains("bundlerelease")
val configuredVersionCode = if (isBundleReleaseRequested) baseVersionCode + 1 else baseVersionCode
val configuredVersionName = if (isBundleReleaseRequested) bumpPatchVersion(baseVersionName) else baseVersionName

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
            versionPropsFile.outputStream().use { props.store(it, null) }
            println(
                "Persisted VERSION_CODE: ${configuredVersionCode}, " +
                    "VERSION_NAME: ${configuredVersionName}"
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11")
    testImplementation("androidx.work:work-testing:2.8.1")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // ...existing code...
}

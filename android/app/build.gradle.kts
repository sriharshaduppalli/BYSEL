import java.util.Properties

plugins {
    id("com.android.application") version "8.4.0"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("org.jetbrains.kotlin.kapt") version "1.9.23"
    id("com.google.dagger.hilt.android") version "2.51.1"
}

apply {
    plugin("kotlin-kapt")
    plugin("com.google.dagger.hilt.android")
}

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
        // read version from root gradle.properties (VERSION_CODE, VERSION_NAME)
        val verCodeProp = rootProject.findProperty("VERSION_CODE") ?: "44"
        val verNameProp = rootProject.findProperty("VERSION_NAME") ?: "2.6.4"
        versionCode = verCodeProp.toString().toInt()
        versionName = verNameProp.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

        // --- Auto-increment version before bundleRelease ---
        // This task updates root gradle.properties VERSION_CODE and VERSION_NAME.
        // It increments VERSION_CODE by 1 and bumps the patch of VERSION_NAME (x.y.z -> x.y.(z+1)).
        val incrementVersion by tasks.registering {
            doLast {
                val propsFile = rootProject.file("gradle.properties")
                if (!propsFile.exists()) {
                    println("gradle.properties not found at ${propsFile.absolutePath}")
                    return@doLast
                }
                val props = Properties()
                props.load(propsFile.inputStream())
                val code = (props.getProperty("VERSION_CODE") ?: "0").toInt()
                val name = props.getProperty("VERSION_NAME") ?: "0.0.0"
                val newCode = code + 1
                val parts = name.split('.').toMutableList()
                if (parts.size >= 3) {
                    val patch = parts.last().toIntOrNull() ?: 0
                    parts[parts.size - 1] = (patch + 1).toString()
                } else {
                    // ensure at least 3 parts
                    while (parts.size < 3) parts.add("0")
                    val patch = parts.last().toIntOrNull() ?: 0
                    parts[parts.size - 1] = (patch + 1).toString()
                }
                val newName = parts.joinToString(".")
                props.setProperty("VERSION_CODE", newCode.toString())
                props.setProperty("VERSION_NAME", newName)
                props.store(propsFile.outputStream(), null)
                println("Bumped VERSION_CODE: $code -> $newCode, VERSION_NAME: $name -> $newName")
            }
        }

        // Ensure bundleRelease depends on incrementVersion so version is bumped automatically.
        tasks.matching { it.name == "bundleRelease" }.configureEach {
            dependsOn(incrementVersion)
        }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "BYSEL@2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "bysel_key"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "BYSEL@2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // ...existing code...
}

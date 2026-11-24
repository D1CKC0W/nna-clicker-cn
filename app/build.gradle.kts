plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nnazuna.nnaclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nnazuna.nnaclicker"
        minSdk = 24
        targetSdk = 34
        versionCode = 24
        versionName = "2.3.1"
    }

    // Kunci tanda tangan tetap, supaya APK versi baru bisa langsung menimpa versi lama.
    val stableStorePassword =
        project.findProperty("KEYSTORE_PASSWORD") as String? ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
    val stableKeyPassword =
        project.findProperty("KEY_PASSWORD") as String? ?: System.getenv("KEY_PASSWORD") ?: ""
    val hasStableSigning = stableStorePassword.isNotBlank() && stableKeyPassword.isNotBlank()

    signingConfigs {
        create("stable") {
            storeFile = file("signing/oprecorder.keystore")
            storePassword = stableStorePassword
            keyAlias = "oprecorder"
            keyPassword = stableKeyPassword
        }
    }

    buildTypes {
        debug {
            // Local contributors can build without the private signing password.
            signingConfig = if (hasStableSigning) {
                signingConfigs.getByName("stable")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = false
            // Unsigned when the signing secrets are absent, so F-Droid can build and sign it.
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // F-Droid: don't embed the Google-encrypted dependency metadata block in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val shizuku = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizuku")
    implementation("dev.rikka.shizuku:provider:$shizuku")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests; the Android stub returns empty values.
    testImplementation("org.json:json:20240303")
}

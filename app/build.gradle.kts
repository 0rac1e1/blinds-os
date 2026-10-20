plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.blinds.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blinds.os"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "26.4"
    }

    // A fixed keystore is committed on purpose so every CI build is signed with the
    // same key and new builds install over old ones without uninstalling first.
    signingConfigs {
        create("blinds") {
            storeFile = file("blinds.keystore")
            storePassword = "blindsos123"
            keyAlias = "blinds"
            keyPassword = "blindsos123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("blinds")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("blinds")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
}

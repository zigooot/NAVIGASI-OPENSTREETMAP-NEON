plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.kahyangan.navneon"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.kahyangan.navneon"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Pemasangan pribadi: ditandatangani dengan kunci debug.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // peta offline format mapsforge (LGPL-3.0)
    implementation("org.mapsforge:mapsforge-core:0.25.0")
    implementation("org.mapsforge:mapsforge-map:0.25.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.25.0")
    implementation("org.mapsforge:mapsforge-themes:0.25.0")
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
}

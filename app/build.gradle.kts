
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bluckyprime.ytubelite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bluckyprime.ytubelite"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    implementation(
        "io.github.junkfood02.youtubedl-android:library:0.18.1"
    )
    implementation(
        "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1"
    )
}

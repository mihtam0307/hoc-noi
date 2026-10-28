plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val verCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.mihtam.floatstudy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mihtam.floatstudy"
        minSdk = 26
        targetSdk = 34
        versionCode = verCode
        versionName = "1.0.$verCode"
    }

    signingConfigs {
        if (ksFile != null && file(ksFile).exists()) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Nhận chữ từ ảnh (chạy trên máy; model do Google Play Services tải, APK không phình to)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
}

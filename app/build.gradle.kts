plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val hasSigningKey = !System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()

android {
    signingConfigs {
        if (hasSigningKey) {
            create("production") {
                storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    namespace = "com.fgmachines.fgnote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fgmachines.fgnote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.4.0-beta1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigningKey) signingConfig = signingConfigs.getByName("production")
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

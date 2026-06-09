plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wsx.ankiaddword"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wsx.ankiaddword"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
    }

    // CI 提供固定签名时使用它（保证每次构建“身份指纹”一致，APP 内更新才能覆盖安装）；
    // 本地无环境变量时回退到默认 debug 签名。
    val ksFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (!ksFile.isNullOrEmpty()) {
            create("fixed") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!ksFile.isNullOrEmpty()) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            if (!ksFile.isNullOrEmpty()) signingConfig = signingConfigs.getByName("fixed")
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AnkiDroid API (via JitPack)
    implementation("com.github.ankidroid:Anki-Android:api-v1.1.0")
}

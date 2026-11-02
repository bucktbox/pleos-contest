plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.safecargo.poc"
    compileSdk = 34
    // CarUxRestrictionsManager (android.car) — 플랫폼 optional 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.safecargo.poc"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-poc"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The proxy URL comes from build config, never from code. Dev default: the host machine as seen by the emulator.
// Override with: ./gradlew :androidApp:assembleDebug -PproxyBaseUrl=https://your-dev-proxy.example
val proxyBaseUrl = providers.gradleProperty("proxyBaseUrl").orElse("http://10.0.2.2:8080")

android {
    namespace = "sg.hirokids.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "sg.hirokids.app" // placeholder: final package name is an open question (tasks/plan.md)
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PROXY_BASE_URL", "\"${proxyBaseUrl.get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.core)
    implementation(libs.play.services.location) // FusedLocationProviderClient (spec section 6)
}

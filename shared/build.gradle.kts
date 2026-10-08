plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "sg.hirokids.shared"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        withHostTest {}
    }
    jvm() // runs the shared tests on the JVM (spec section 10)
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

// One source of truth: data/nlb_libraries.json is copied into the app's resources at build time (read as files/nlb_libraries.json).
// A custom resource directory replaces the default one, so the staged copy holds both the strings and the library file.
val stageResources =
    tasks.register<Sync>("stageCommonResources") {
        from(layout.projectDirectory.dir("src/commonMain/composeResources"))
        from(rootProject.layout.projectDirectory.file("data/nlb_libraries.json")) { into("files") }
        into(layout.buildDirectory.dir("generated/commonResources"))
    }

compose.resources {
    packageOfResClass = "sg.hirokids.shared.resources"
    publicResClass = false
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = layout.dir(stageResources.map { it.destinationDir }),
    )
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("sg.hirokids.shared.db")
        }
    }
}

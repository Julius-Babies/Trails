import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    androidLibrary {
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        namespace = "es.jvbabi.trails.shared.compose"

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.browser)
            val mapboxVersion = libs.versions.app.mapbox.get()
            api("com.mapbox.maps:android-ndk27:$mapboxVersion") {
                exclude(group = "com.google.android.gms", module = "play-services-cronet")
            }
            implementation(libs.mapbox.compose)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.navigation3.lifecycle)

            api(libs.koin.compose)
            implementation(libs.koin.compose.navigation3)

            implementation(libs.kotlinx.datetime)

            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            api(libs.moko.permissions.core)
            api(libs.moko.permissions.compose)
            implementation(libs.moko.permissions.location)

            api(libs.kermit)

            implementation(libs.haze.blur)
            implementation(libs.haze.blur.materials)

            api(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websocket)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}


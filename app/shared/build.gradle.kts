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

    android {
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
            implementation(libs.app.androidx.browser)
            val mapboxVersion = libs.versions.app.mapbox.get()
            api("com.mapbox.maps:android-ndk27:$mapboxVersion") {
                exclude(group = "com.google.android.gms", module = "play-services-cronet")
            }
            implementation(libs.app.mapbox.compose)
            implementation(libs.app.ktor.client.cio)
        }

        commonMain.dependencies {
            implementation(projects.shared)

            implementation(libs.app.compose.runtime)
            implementation(libs.app.compose.foundation)
            implementation(libs.app.compose.material3)
            implementation(libs.app.compose.ui)
            implementation(libs.app.compose.components.resources)
            implementation(libs.app.compose.uiToolingPreview)
            implementation(libs.app.androidx.lifecycle.viewmodelCompose)
            implementation(libs.app.androidx.lifecycle.runtimeCompose)

            implementation(libs.app.navigation3.runtime)
            implementation(libs.app.navigation3.ui)
            implementation(libs.app.navigation3.lifecycle)

            api(libs.app.koin.compose)
            implementation(libs.app.koin.compose.navigation3)

            implementation(libs.app.kotlinx.datetime)

            implementation(libs.app.androidx.room.runtime)
            implementation(libs.app.androidx.sqlite.bundled)

            api(libs.app.moko.permissions.core)
            api(libs.app.moko.permissions.compose)
            implementation(libs.app.moko.permissions.location)

            api(libs.app.kermit)

            implementation(libs.app.haze.blur)
            implementation(libs.app.haze.blur.materials)

            implementation(libs.app.human.readable)

            api(libs.app.ktor.client.core)
            implementation(libs.app.ktor.client.content.negotiation)
            implementation(libs.app.ktor.client.websockets)
            implementation(libs.app.ktor.serialization.kotlinx.json)
        }

        iosMain.dependencies {
            implementation(libs.app.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.app.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.app.androidx.room.compiler)
    add("kspIosArm64", libs.app.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.app.compose.uiTooling)
}

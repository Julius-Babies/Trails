import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }

    dependencies {
        implementation(libs.app.compose.uiToolingPreview)
        implementation(libs.app.androidx.activity.compose)
        implementation(projects.app.shared)
    }
}

android {
    namespace = "es.jvbabi.trails"

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    if (listOf("signing.default.file", "signing.default.storepassword", "signing.default.keyalias", "signing.default.keypassword").all { localProperties.containsKey(it) }) {
        signingConfigs {
            create("default") {
                storeFile = rootProject.file(localProperties["signing.default.file"]!!)
                storePassword = localProperties["signing.default.storepassword"].toString()
                keyAlias = localProperties["signing.default.keyalias"].toString()
                keyPassword = localProperties["signing.default.keypassword"].toString()
            }
        }
    } else {
        println("Warning: signing configuration not found in local.properties, release builds will not be signed.")
    }

    defaultConfig {
        applicationId = "es.jvbabi.trails"
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "MAPBOX_API_KEY", "\"${localProperties.getProperty("mapbox.public-token")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFile(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            signingConfig = signingConfigs.findByName("default")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

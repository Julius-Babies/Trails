import org.gradle.kotlin.dsl.maven

rootProject.name = "server"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "GitHub Authentikt"
            url = uri("https://maven.pkg.github.com/Julius-Babies/authentikt")
            credentials {
                username = providers.gradleProperty("maven.pkg.github.com.user").get()
                password = providers.gradleProperty("maven.pkg.github.com.token").get()
            }
        }
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:3.4.0")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
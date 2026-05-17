rootProject.name = "Trails"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
        maven("https://api.mapbox.com/downloads/v2/releases/maven") {
            credentials {
                // Set in ~/.gradle/gradle.properties: MAPBOX_DOWNLOADS_TOKEN=your_token
                username = "mapbox"
                password = providers.gradleProperty("mapbox.token").orElse("").get()
            }
        }
    }
}

include(":composeApp")
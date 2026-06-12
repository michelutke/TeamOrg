rootProject.name = "teamorg"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/interactive") {
            content { includeGroupAndSubgroups("org.jetbrains") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content { includeGroupAndSubgroups("org.jetbrains") }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroupAndSubgroups("com.github") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content { includeGroupAndSubgroups("org.jetbrains") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/interactive") {
            content { includeGroupAndSubgroups("org.jetbrains") }
        }
    }
}

include(":shared")
include(":composeApp")
include(":server")

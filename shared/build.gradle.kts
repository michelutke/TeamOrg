import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.clientLogging)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.sqldelight.runtime)
            implementation(libs.multiplatform.settings)
        }

        androidMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.onesignal)
        }

        iosMain.dependencies {
            implementation(libs.ktor.clientDarwin)
            implementation(libs.sqldelight.nativeDriver)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
            implementation(libs.sqldelight.sqliteDriver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":server"))
                implementation(libs.kotlin.testJunit)
                implementation(libs.ktor.serverNetty)
                implementation(libs.ktor.serverCore)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junit)
                implementation(libs.kotlinx.coroutinesTest)
                implementation(libs.multiplatform.settings.test)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// Resolve API_BASE_URL from (in order): env var, gradle property, local.properties,
// then the provided default. local.properties is gitignored, so it never leaks into builds.
fun resolveApiBaseUrl(default: String): String {
    System.getenv("API_BASE_URL")?.takeIf { it.isNotBlank() }?.let { return it }
    (project.findProperty("API_BASE_URL") as String?)?.takeIf { it.isNotBlank() }?.let { return it }
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("API_BASE_URL=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return default
}

android {
    namespace = "ch.teamorg.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl("https://api.teamorg.app")}\"")
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl("http://10.0.2.2:8080")}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${resolveApiBaseUrl("https://api.teamorg.app")}\"")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

sqldelight {
    databases {
        create("TeamorgDb") {
            packageName.set("ch.teamorg.db")
        }
    }
}

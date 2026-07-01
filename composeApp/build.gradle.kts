import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activityCompose)
            implementation(libs.koin.android)
            implementation(libs.androidx.lifecycleProcess)
            implementation(libs.onesignal)
            // implementation(libs.updraft.sdk) // TODO: needs com.simplify:ink + com.rm:freedrawview repos
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycleViewmodelCompose)
            implementation(libs.androidx.lifecycleRuntimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.compose.navigation3)
            implementation(libs.coil3.compose)
            implementation(projects.shared)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kizitonwose.calendar.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
            implementation(libs.kotest.assertions)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutinesTest)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.compose.uiTestJunit4)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.compose.uiTestJunit4)
            }
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// kotlinx-datetime 0.7.x typealiases cause Kotlin/Native IR ClassCastException with CMP 1.10.1
configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
}

android {
    namespace = "ch.teamorg"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ch.teamorg"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        val onesignalAppId: String by lazy {
            val props = providers.gradleProperty("onesignal.appId").orNull
                ?: rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
                    ?.firstOrNull { it.startsWith("onesignal.appId=") }?.substringAfter("=")
                ?: ""
            props
        }
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"$onesignalAppId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${project.projectDir}/release.jks")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "ch.teamorg.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ch.teamorg"
            packageVersion = "1.0.0"
        }
    }
}

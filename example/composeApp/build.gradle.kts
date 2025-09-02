import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.cactus.example")
        }
    }

    sourceSets {
                androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
            implementation(libs.ktor.client.okhttp)

        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation("com.cactus:library:0.2.4")
            implementation(libs.ktor.client.core)
        }
    }
}

android {
    namespace = "com.cactus.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cactus.example"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/com/sun/jna/android-*/**"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    

}

dependencies {
    debugImplementation(libs.androidx.compose.ui.tooling)
}

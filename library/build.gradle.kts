import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("co.touchlab.kmmbridge") version "1.2.1"
    `maven-publish`
}

group = "com.cactus"
version = "0.2.5"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
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
            baseName = "cactus"
            isStatic = true
        }

        iosTarget.compilations.getByName("main") {
            cinterops {
                val cactus by creating {
                    defFile(project.file("src/iosMain/cinterop/cactus.def"))
                    packageName("com.cactus.native")

                    val archPath = when (iosTarget.name) {
                        "iosArm64" -> "ios-arm64"
                        "iosSimulatorArm64" -> "ios-arm64-simulator"
                        else -> "ios-arm64"
                    }

                    val headerPath = project.file("src/commonMain/resources/ios/include")
                    val libraryPath = project.file("src/commonMain/resources/ios/lib/$archPath")
                    
                    includeDirs(headerPath)

                    compilerOpts("-framework", "Accelerate", "-framework", "Foundation",
                        "-framework", "Metal", "-framework", "MetalKit")
                    
                    compilerOpts("-L${libraryPath.absolutePath}", "-lcactus")
                    extraOpts("-libraryPath", libraryPath.absolutePath)
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.activity:activity-compose:1.8.2")
            }
        }
    }
}

kmmbridge {
    mavenPublishArtifacts()
    spm()
}

android {
    namespace = "com.cactus.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = "27.0.12077973"
    
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                abiFilters += setOf("arm64-v8a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("Cactus")
                description.set("Run AI locally in your apps")
                url.set("https://github.com/cactus-compute/cactus/")
            }
        }
    }
}

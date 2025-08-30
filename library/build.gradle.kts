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
            freeCompilerArgs.add("-Xexpect-actual-classes")
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
        
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        iosTarget.compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
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
                
                val cactus_util by creating {
                    defFile(project.file("src/iosMain/cinterop/cactus_util.def"))
                    packageName("com.cactus.util.native")

                    val archPath = when (iosTarget.name) {
                        "iosArm64" -> "ios-arm64"
                        "iosSimulatorArm64" -> "ios-arm64-simulator"
                        else -> "ios-arm64"
                    }

                    // Use the xcframework provided by the user
                    val xcframeworkPath = project.file("src/commonMain/resources/ios/cactus_util.xcframework")
                    val frameworkPath = xcframeworkPath.resolve("$archPath/cactus_util.framework")
                    
                    includeDirs(frameworkPath.resolve("Headers"))
                    
                    // Use the library path to find the dynamic library
                    extraOpts("-libraryPath", frameworkPath.absolutePath)
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("io.ktor:ktor-client-core:3.1.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("io.ktor:ktor-client-okhttp:3.1.3")
            }
        }
        val iosMain by creating {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.1.3")
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
    
    // Include prebuilt .so files
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/commonMain/resources/android/libs")
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

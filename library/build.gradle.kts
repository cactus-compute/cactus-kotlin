import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val properties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("co.touchlab.kmmbridge") version "1.2.1"
    `maven-publish`
}

group = "com.cactus"
version = "0.2.5"

buildConfig {
    packageName("com.cactus")
    buildConfigField("String", "FRAMEWORK_VERSION", "\"$version\"")
}

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

                    val headerPath = project.file("src/commonMain/resources/ios/include")
                    val libraryPath = project.file("src/commonMain/resources/ios/lib/$archPath")
                    
                    includeDirs(headerPath)

                    compilerOpts("-framework", "Foundation", "-framework", "Accelerate", 
                        "-framework", "Metal", "-framework", "MetalKit")
                    
                    compilerOpts("-L${libraryPath.absolutePath}", "-lcactus_util")
                    extraOpts("-libraryPath", libraryPath.absolutePath)
                }

                val vosk by creating {
                    defFile(project.file("src/commonMain/resources/cinterop/vosk.def"))
                    packageName("com.vosk.native")

                    val voskArchPath = when (iosTarget.name) {
                        "iosArm64" -> "ios-arm64_armv7_armv7s"
                        "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
                        else -> "ios-arm64_armv7_armv7s"
                    }

                    val voskLibPath = project.file("src/commonMain/resources/ios/libvosk.xcframework/$voskArchPath")
                    val voskHeaderPath = project.file("src/commonMain/resources/cinterop")

                    includeDirs(voskHeaderPath)
                    compilerOpts("-I", voskHeaderPath.absolutePath)

                    // Link the static library
                    extraOpts("-libraryPath", voskLibPath.absolutePath)
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
                implementation("com.squareup.okio:okio:3.9.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("io.ktor:ktor-client-okhttp:3.1.3")
                implementation("com.alphacephei:vosk-android:0.3.47@aar")
                implementation("net.java.dev.jna:jna:5.13.0@aar")
            }
        }
        val iosMain by creating {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.1.3")
                implementation("com.squareup.okio:okio:3.9.0")
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
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cactus-compute/cactus-kotlin")
            credentials {
                username = properties.getProperty("github.username") ?: System.getenv("GITHUB_ACTOR")
                password = properties.getProperty("github.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
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

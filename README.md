# Cactus Kotlin Multiplatform Library

![Cactus Logo](https://github.com/cactus-compute/cactus-kotlin/blob/main/assets/logo.png)

Official Kotlin Multiplatform library for Cactus, a framework for deploying LLM and STT models locally in your app. Requires iOS 12.0+, Android API 24+.

## Resources
[![cactus](https://img.shields.io/badge/cactus-000000?logo=github&logoColor=white)](https://github.com/cactus-compute/cactus) [![HuggingFace](https://img.shields.io/badge/HuggingFace-FFD21E?logo=huggingface&logoColor=black)](https://huggingface.co/Cactus-Compute/models?sort=downloads) [![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/bNurx3AXTJ) [![Documentation](https://img.shields.io/badge/Documentation-4285F4?logo=googledocs&logoColor=white)](https://cactuscompute.com/docs)

## Installation

### 1. Add the repository to your `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            name = "GitHubPackagesCactus"
            url = uri("https://maven.pkg.github.com/cactus-compute/cactus-kotlin")
            credentials {
                username = properties.getProperty("github.username") ?: System.getenv("GITHUB_ACTOR")
                password = properties.getProperty("github.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### 2. Add credentials
Add your GitHub username and token to `local.properties`:
```
github.username=your-username
github.token=your-personal-access-token
```
Or set them as environment variables: `GITHUB_ACTOR` and `GITHUB_TOKEN`.

### 3. Add the dependency to your `build.gradle.kts`:
```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("com.cactus:library:0.2.5")
            }
        }
    }
}
```

For iOS projects, add the dependency through Swift Package Manager:
- In Xcode: File → Add Package Dependencies → Paste `https://github.com/cactus-compute/cactus-kotlin` → Click Add

## Hello World

Here's a simple example to get started with text generation:

```kotlin
import com.cactus.CactusLM
import com.cactus.ChatMessage
import kotlinx.coroutines.runBlocking

runBlocking {
    val lm = CactusLM()

    // Download and initialize the default model
    lm.downloadModel()
    lm.initializeModel()

    // Generate a response
    val result = lm.generateCompletion(
        messages = listOf(ChatMessage("Hello, world!", "user"))
    )

    result?.let {
        println(it.response)
    }

    // Clean up
    lm.unload()
}
```

That's it! For more advanced usage, check the [documentation](https://cactuscompute.com/docs).

## Support

- 📖 [Documentation](https://cactuscompute.com/docs)
- 💬 [Discord Community](https://discord.gg/bNurx3AXTJ)
- 🐛 [Issues](https://github.com/cactus-compute/cactus-kotlin/issues)
- 🤗 [Models on Hugging Face](https://huggingface.co/Cactus-Compute/models)

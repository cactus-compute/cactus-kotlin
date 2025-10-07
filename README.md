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
You can generate a personal access token by following the instructions on [GitHub's documentation](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens). The token needs `read:packages` scope.

Or set them as environment variables: `GITHUB_ACTOR` and `GITHUB_TOKEN`.

### 3. Add to your KMP project's `build.gradle.kts`:
```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("com.cactus:library:0.3-beta.1")
            }
        }
    }
}
```

### 4. Add the permissions to your manifest (Android)
```xml
<uses-permission android:name="android.permission.INTERNET" /> // for model downloads
<uses-permission android:name="android.permission.RECORD_AUDIO" /> // for transcription
```

## Getting Started

### Telemetry Setup (Optional)
```kotlin
import com.cactus.services.CactusTelemetry

// Initialize telemetry for usage analytics (optional)
CactusTelemetry.setTelemetryToken("your_token_here")
```

## Language Model (LLM)

The `CactusLM` class provides text completion capabilities with support for function calling (WIP).

### Basic Usage
```kotlin
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import com.cactus.ChatMessage
import kotlinx.coroutines.runBlocking

runBlocking {
    val lm = CactusLM()

    // Download a model (default: qwen3-0.6)
    val downloadSuccess = lm.downloadModel("qwen3-0.6")
    
    // Initialize the model
    val initSuccess = lm.initializeModel(
        CactusInitParams(
            model = "qwen3-0.6",
            contextSize = 2048
        )
    )

    // Generate completion
    val result = lm.generateCompletion(
        messages = listOf(
            ChatMessage(content = "Hello, how are you?", role = "user")
        ),
        params = CactusCompletionParams(
            maxTokens = 100,
            temperature = 0.7,
            topK = 40,
            topP = 0.95
        )
    )

    result?.let { response ->
        if (response.success) {
            println("Response: ${response.response}")
            println("Tokens per second: ${response.tokensPerSecond}")
            println("Time to first token: ${response.timeToFirstTokenMs}ms")
        }
    }

    // Clean up
    lm.unload()
}
```

### Streaming Completions
```kotlin
val result = lm.generateCompletion(
    messages = listOf(ChatMessage("Tell me a story", "user")),
    params = CactusCompletionParams(maxTokens = 200),
    onToken = { token, tokenId ->
        print(token) // Print each token as it's generated
    }
)
```

### Function Calling (Experimental)
```kotlin
import com.cactus.models.ToolParameter
import com.cactus.models.createTool

val tools = listOf(
    createTool(
        name = "get_weather",
        description = "Get current weather for a location",
        parameters = mapOf(
            "location" to ToolParameter(
                type = "string", 
                description = "City name", 
                required = true
            )
        )
    )
)

val result = lm.generateCompletion(
    messages = listOf(ChatMessage("What's the weather in New York?", "user")),
    params = CactusCompletionParams(
        maxTokens = 100,
        tools = tools
    )
)
```

### Inference Modes

The `generateCompletion` method supports different inference modes through the `mode` parameter, which takes an `InferenceMode` enum value. This allows you to control whether the completion is generated locally on the device or remotely using a compatible API.

- `InferenceMode.LOCAL`: (Default) Generates the completion using the local on-device model.
- `InferenceMode.REMOTE`: Generates the completion using a remote API. Requires an `apiKey`.
- `InferenceMode.LOCAL_FIRST`: Attempts to generate the completion locally first. If it fails, it falls back to the remote API.
- `InferenceMode.REMOTE_FIRST`: Attempts to generate the completion remotely first. If it fails, it falls back to the local on-device model.

**Example using a remote-first strategy:**
```kotlin
val result = lm.generateCompletion(
    messages = listOf(ChatMessage("What's the weather in New York?", "user")),
    params = CactusCompletionParams(
        maxTokens = 100,
        mode = InferenceMode.REMOTE_FIRST,
        cactusToken = "your_cactus_token"
    ),
)
```

### Available Models
You can get a list of available models:
```kotlin
lm.getModels()
```

### LLM API Reference

#### CactusLM Class
- `suspend fun downloadModel(model: String = "qwen3-0.6"): Boolean` - Download a model
- `suspend fun initializeModel(params: CactusInitParams): Boolean` - Initialize model for inference
- `suspend fun generateCompletion(messages: List<ChatMessage>, params: CactusCompletionParams, onToken: CactusStreamingCallback? = null): CactusCompletionResult?` - Generate text completion. Supports different inference modes (local, remote, and fallbacks).
- `fun unload()` - Free model from memory
- `suspend fun getModels(): List<CactusModel>` - Get available LLM models
- `fun isLoaded(): Boolean` - Check if model is loaded

#### Data Classes
- `CactusInitParams(model: String?, contextSize: Int?)` - Model initialization parameters
- `CactusCompletionParams(temperature: Double, topK: Int, topP: Double, maxTokens: Int, stopSequences: List<String>, bufferSize: Int, tools: List<Tool>?, mode: InferenceMode, cactusToken: String)` - Completion parameters
- `ChatMessage(content: String, role: String, timestamp: Long?)` - Chat message format
- `CactusCompletionResult` - Contains response, timing metrics, and success status
- `CactusEmbeddingResult(success: Boolean, embeddings: List<Double>, dimension: Int, errorMessage: String?)` - Embedding generation result

## Embeddings

The `CactusLM` class also provides text embedding generation capabilities for semantic similarity, search, and other NLP tasks.

### Basic Usage
```kotlin
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val lm = CactusLM()

    // Download and initialize a model (same as for completions)
    lm.downloadModel("qwen3-0.6")
    lm.initializeModel(CactusInitParams(model = "qwen3-0.6", contextSize = 2048))

    // Generate embeddings for a text
    val result = lm.generateEmbedding(
        text = "This is a sample text for embedding generation",
        bufferSize = 2048
    )

    result?.let { embedding ->
        if (embedding.success) {
            println("Embedding dimension: ${embedding.dimension}")
            println("Embedding vector length: ${embedding.embeddings.size}")
        } else {
            println("Embedding generation failed: ${embedding.errorMessage}")
        }
    }

    lm.unload()
}
```

### Embedding API Reference

#### CactusLM Class (Embedding Methods)
- `suspend fun generateEmbedding(text: String, bufferSize: Int = 2048): CactusEmbeddingResult?` - Generate text embeddings

#### Embedding Data Classes
- `CactusEmbeddingResult(success: Boolean, embeddings: List<Double>, dimension: Int, errorMessage: String?)` - Contains the generated embedding vector and metadata

## Speech-to-Text (STT)

The `CactusSTT` class provides speech recognition capabilities using on-device models from providers like **Vosk** and **Whisper**.

### Choosing a Transcription Provider

You can select a transcription provider when initializing `CactusSTT`. The available providers are:
- `TranscriptionProvider.VOSK` (Default): Uses Vosk for transcription.
- `TranscriptionProvider.WHISPER`: Uses Whisper for transcription.

```kotlin
import com.cactus.CactusSTT
import com.cactus.TranscriptionProvider

// Initialize with the VOSK provider (default)
val sttVosk = CactusSTT() 

// Or explicitly initialize with the WHISPER provider
val sttWhisper = CactusSTT(TranscriptionProvider.WHISPER)
```

### Basic Usage

#### With Vosk
```kotlin
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT() // Defaults to VOSK provider

    // Download STT model (e.g., vosk-en-us)
    val downloadSuccess = stt.download("vosk-en-us")
    
    // Initialize the model
    val initSuccess = stt.init("vosk-en-us")

    // Transcribe from microphone
    val result = stt.transcribe(
        SpeechRecognitionParams(
            maxSilenceDuration = 1000L,
            maxDuration = 30000L,
            sampleRate = 16000
        )
    )

    result?.let { transcription ->
        if (transcription.success) {
            println("Transcribed: ${transcription.text}")
            println("Processing time: ${transcription.processingTime}ms")
        }
    }

    // Stop transcription
    stt.stop()
}
```

#### With Whisper
```kotlin
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import com.cactus.TranscriptionProvider
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT(TranscriptionProvider.WHISPER)

    // Download a Whisper model (e.g., whisper-tiny)
    val downloadSuccess = stt.download("whisper-tiny")
    
    // Initialize the model
    val initSuccess = stt.init("whisper-tiny")

    // Transcribe from an audio file
    val fileResult = stt.transcribe(
        params = SpeechRecognitionParams(),
        filePath = "/path/to/audio.wav"
    )

    fileResult?.let { transcription ->
        if (transcription.success) {
            println("Transcribed: ${transcription.text}")
        }
    }

    // Stop transcription
    stt.stop()
}
```

### Transcription Modes

`CactusSTT` supports multiple transcription modes for flexibility between on-device and cloud-based processing. This is controlled by the `mode` parameter in the `transcribe` function.

- `TranscriptionMode.LOCAL`: (Default) Performs transcription locally on the device.
- `TranscriptionMode.REMOTE`: Performs transcription using a remote API (e.g., Wispr). Requires `filePath` and `apiKey`.
- `TranscriptionMode.LOCAL_FIRST`: Attempts local transcription first. If it fails, it falls back to the remote API.
- `TranscriptionMode.REMOTE_FIRST`: Attempts remote transcription first. If it fails, it falls back to the local model.

**Example using local-first fallback for a file:**
```kotlin
// Transcribe from audio file with remote fallback
val fileResult = stt.transcribe(
    params = SpeechRecognitionParams(),
    filePath = "/path/to/audio.wav",
    mode = TranscriptionMode.LOCAL_FIRST,
    apiKey = "your_wispr_api_key"
)
```

### Available Voice Models
You can get a list of available models for the configured provider.
```kotlin
// For VOSK (default)
val voskModels = CactusSTT().getVoiceModels()

// For WHISPER
val whisperModels = CactusSTT().getVoiceModels(TranscriptionProvider.WHISPER)

// Check if a model is downloaded
stt.isModelDownloaded("vosk-en-us")
```

### STT API Reference

#### CactusSTT Class
- `CactusSTT(provider: TranscriptionProvider = TranscriptionProvider.VOSK)` - Constructor to specify the transcription provider.
- `suspend fun download(model: String): Boolean` - Download an STT model (e.g., "vosk-en-us" or "whisper-tiny-en").
- `suspend fun init(model: String): Boolean` - Initialize an STT model for transcription.
- `suspend fun transcribe(params: SpeechRecognitionParams = SpeechRecognitionParams(), filePath: String? = null, mode: TranscriptionMode = TranscriptionMode.LOCAL, apiKey: String? = null): SpeechRecognitionResult?` - Transcribe speech from microphone or file. Supports different transcription modes.
- `suspend fun warmUpWispr(apiKey: String)` - Warms up the remote Wispr service for lower latency.
- `fun stop()` - Stop ongoing transcription.
- `fun isReady(): Boolean` - Check if the STT service is initialized and ready.
- `suspend fun getVoiceModels(provider: TranscriptionProvider = TranscriptionProvider.VOSK): List<VoiceModel>` - Get a list of available voice models for the configured provider.
- `suspend fun isModelDownloaded(modelName: String): Boolean` - Check if a specific model has been downloaded.

#### Data Classes
- `TranscriptionProvider` - Enum for selecting the provider (`VOSK`, `WHISPER`).
- `SpeechRecognitionParams(maxSilenceDuration: Long, maxDuration: Long, sampleRate: Int)` - Parameters for controlling speech recognition.
- `SpeechRecognitionResult(success: Boolean, text: String?, processingTime: Double?)` - The result of a transcription.
- `VoiceModel` - Contains information about an available voice model.

## Platform-Specific Setup

### Android
- Works automatically - native libraries included
- Requires API 24+ (Android 7.0)
- ARM64 architecture supported

### iOS
- Add the Cactus package dependency in Xcode
- Requires iOS 12.0+
- Supports ARM64 and Simulator ARM64

## Building the Library

To build the library from source:

```bash
# Build the library and publish to localMaven
./build_library.sh

```

## Example App

Navigate to the example app and run it:

```bash
cd kotlin/example

# For desktop
./gradlew :composeApp:run

# For Android/iOS - use Android Studio or Xcode
```

The example app demonstrates:
- Model downloading and initialization
- Text completion with streaming
- Function calling
- Speech-to-text transcription
- Error handling and status management

## Performance Tips

1. **Model Selection**: Choose smaller models for faster inference on mobile devices
2. **Context Size**: Reduce context size for lower memory usage
3. **Memory Management**: Always call `unload()` when done with models
4. **Batch Processing**: Reuse initialized models for multiple completions

## Support

- 📖 [Documentation](https://cactuscompute.com/docs)
- 💬 [Discord Community](https://discord.gg/bNurx3AXTJ)
- 🐛 [Issues](https://github.com/cactus-compute/cactus-kotlin/issues)
- 🤗 [Models on Hugging Face](https://huggingface.co/Cactus-Compute/models)

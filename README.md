# Cactus Kotlin Multiplatform Library

![Cactus Logo](https://github.com/cactus-compute/cactus-kotlin/blob/main/assets/logo.png)

Official Kotlin Multiplatform library for Cactus, a framework for deploying LLM models and speech-to-text locally in your app. Requires iOS 12.0+, Android API 24+.

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
                implementation("com.cactus:library:0.3.6-beta")
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

### Context Initialization (Required)
Initialize the Cactus context in your Activity's `onCreate()` method before using any SDK functionality:
```kotlin
import com.cactus.CactusContextInitializer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Cactus context (required)
        CactusContextInitializer.initialize(this)
        
        // ... rest of your code
    }
}
```

### Telemetry Setup (Optional)
```kotlin
import com.cactus.services.CactusTelemetry

// Initialize telemetry for usage analytics (optional)
CactusTelemetry.setTelemetryToken("your_token_here")
```

## Language Model (LLM)

The `CactusLM` class provides text completion capabilities with high-performance local inference.

### Basic Usage
```kotlin
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import com.cactus.ChatMessage
import kotlinx.coroutines.runBlocking

runBlocking {
    val lm = CactusLM()

    try {
        // Download a model by slug (e.g., "qwen3-0.6", "gemma3-270m")
        // If no model is specified, it defaults to "qwen3-0.6"
        val downloadSuccess = lm.downloadModel("qwen3-0.6")
        
        // Initialize the model
        val initSuccess = lm.initializeModel(
            CactusInitParams(
                model = "qwen3-0.6",
                contextSize = 2048
            )
        )

        // Generate completion with default parameters
        val result = lm.generateCompletion(
            messages = listOf(
                ChatMessage(content = "Hello, how are you?", role = "user")
            )
        )

        result?.let { response ->
            if (response.success) {
                println("Response: ${response.response}")
                println("Tokens per second: ${response.tokensPerSecond}")
                println("Time to first token: ${response.timeToFirstTokenMs}ms")
            }
        }
    } finally {
        // Clean up
        lm.unload()
    }
}
```

### Streaming Completions
```kotlin
runBlocking {
    val lm = CactusLM()
    
    // Download model (defaults to "qwen3-0.6" if model parameter is omitted)
    lm.downloadModel()
    lm.initializeModel(CactusInitParams())

    // Get the streaming response
    val result = lm.generateCompletion(
        messages = listOf(ChatMessage(content = "Tell me a story", role = "user")),
        onToken = { token, tokenId ->
            print(token)
        }
    )

    // Final result after streaming is complete
    result?.let {
        if (it.success) {
            println("\nFinal response: ${it.response}")
            println("Tokens per second: ${it.tokensPerSecond}")
        }
    }

    lm.unload()
}
```

### Model Discovery
```kotlin
runBlocking {
    val lm = CactusLM()
    
    // Get list of available models
    val models = lm.getModels()
    
    models.forEach { model ->
        println("Model: ${model.name}")
        println("  Slug: ${model.slug}")
        println("  Size: ${model.size_mb} MB")
        println("  Tool calling: ${model.supports_tool_calling}")
        println("  Vision: ${model.supports_vision}")
        println("  Downloaded: ${model.isDownloaded}")
    }
}
```

### Function Calling (Experimental)
```kotlin
import com.cactus.models.CactusTool
import com.cactus.models.ToolParametersSchema
import com.cactus.models.ToolParameter

runBlocking {
    val lm = CactusLM()
    
    lm.downloadModel()
    lm.initializeModel(CactusInitParams())

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
        messages = listOf(ChatMessage(content = "What's the weather in New York?", role = "user")),
        params = CactusCompletionParams(
            tools = tools
        )
    )

    result?.toolCalls?.forEach { toolCall ->
        println("Tool: ${toolCall.name}")
        println("Arguments: ${toolCall.arguments}")
    }

    lm.unload()
}
```

### Embedding Generation
```kotlin
runBlocking {
    val lm = CactusLM()
    
    lm.downloadModel()
    lm.initializeModel(CactusInitParams())

    val result = lm.generateEmbedding(
        text = "The quick brown fox jumps over the lazy dog"
    )

    result?.let {
        if (it.success) {
            println("Embedding dimension: ${it.dimension}")
            println("First 5 values: ${it.embeddings.take(5)}")
        }
    }

    lm.unload()
}
```

### Inference Modes

`CactusLM` supports multiple inference modes for flexibility between on-device and cloud-based processing. This is controlled by the `mode` parameter in `CactusCompletionParams`.

- `InferenceMode.LOCAL`: (Default) Performs inference locally on the device.
- `InferenceMode.REMOTE`: Performs inference using a remote API. Requires `cactusToken`.
- `InferenceMode.LOCAL_FIRST`: Attempts local inference first. If it fails, it falls back to the remote API.
- `InferenceMode.REMOTE_FIRST`: Attempts remote inference first. If it fails, it falls back to the local model.

**Example using local-first fallback:**
```kotlin
val result = lm.generateCompletion(
    messages = listOf(ChatMessage(content = "Hello!", role = "user")),
    params = CactusCompletionParams(
        mode = InferenceMode.LOCAL_FIRST,
        cactusToken = "your_api_token"
    )
)
```

### LLM API Reference

#### CactusLM Class
- `suspend fun downloadModel(model: String = "qwen3-0.6"): Boolean` - Download an LLM model by slug (e.g., "qwen3-0.6", "gemma3-270m").
- `suspend fun initializeModel(params: CactusInitParams): Boolean` - Initialize a model for inference.
- `suspend fun generateCompletion(messages: List<ChatMessage>, params: CactusCompletionParams = CactusCompletionParams(), onToken: CactusStreamingCallback? = null): CactusCompletionResult?` - Generate text completion. Supports streaming via the `onToken` callback and different inference modes (local, remote, and fallbacks).
- `suspend fun generateEmbedding(text: String, modelName: String? = null): CactusEmbeddingResult?` - Generate embeddings for the given text.
- `suspend fun getModels(): List<CactusModel>` - Get a list of available models. Results are cached locally to reduce network requests.
- `fun unload()` - Unload the current model and free resources.
- `fun isLoaded(): Boolean` - Check if a model is currently loaded.

#### Data Classes
- `CactusInitParams(model: String?, contextSize: Int?)` - Parameters for model initialization.
- `CactusCompletionParams(temperature: Double, topK: Int, topP: Double, maxTokens: Int, stopSequences: List<String>, tools: List<CactusTool>, mode: InferenceMode, cactusToken: String?, model: String?)` - Parameters for text completion.
- `CactusCompletionResult(success: Boolean, response: String?, timeToFirstTokenMs: Double?, totalTimeMs: Double?, tokensPerSecond: Double?, prefillTokens: Int?, decodeTokens: Int?, totalTokens: Int?, toolCalls: List<ToolCall>?)` - The result of a text completion.
- `CactusEmbeddingResult(success: Boolean, embeddings: List<Double>, dimension: Int?, errorMessage: String?)` - The result of embedding generation.
- `ChatMessage(content: String, role: String, timestamp: Long?)` - A chat message with role (e.g., "user", "assistant").
- `CactusModel(slug: String, name: String, download_url: String, size_mb: Int, supports_tool_calling: Boolean, supports_vision: Boolean, isDownloaded: Boolean, quantization: Int)` - Information about an available model.
- `InferenceMode` - Enum for selecting inference mode (`LOCAL`, `REMOTE`, `LOCAL_FIRST`, `REMOTE_FIRST`).

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

Check out the example app in the `example/` directory for a complete Kotlin Multiplatform implementation showing:
- Model discovery and fetching available models
- Model downloading with progress tracking
- Text completion with both regular and streaming modes
- Speech-to-text transcription with multiple provider support (Vosk and Whisper)
- Voice model management and provider switching
- Embedding generation
- Function calling capabilities
- Error handling and status management
- Compose Multiplatform UI integration

To run the example:
```bash
cd example

# For desktop
./gradlew :composeApp:run

# For Android/iOS - use Android Studio or Xcode
```

## Performance Tips

1. **Model Selection**: Choose smaller models for faster inference on mobile devices
2. **Context Size**: Reduce context size for lower memory usage (e.g., 1024 instead of 2048)
3. **Memory Management**: Always call `unload()` when done with models
4. **Batch Processing**: Reuse initialized models for multiple completions
5. **Model Caching**: Use `getModels()` for efficient model discovery - results are cached locally to reduce network requests

## Support

- 📖 [Documentation](https://cactuscompute.com/docs)
- 💬 [Discord Community](https://discord.gg/bNurx3AXTJ)
- 🐛 [Issues](https://github.com/cactus-compute/cactus-kotlin/issues)
- 🤗 [Models on Hugging Face](https://huggingface.co/Cactus-Compute/models)

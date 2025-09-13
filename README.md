# Cactus Kotlin Multiplatform Library

![Cactus Logo](https://github.com/cactus-compute/cactus-kotlin/blob/main/assets/logo.png)

Official Kotlin Multiplatform library for Cactus, a framework for deploying LLM and STT models locally in your app. Requires iOS 12.0+, Android API 24+.

## Resources
[![cactus](https://img.shields.io/badge/cactus-000000?logo=github&logoColor=white)](https://github.com/cactus-compute/cactus) [![HuggingFace](https://img.shields.io/badge/HuggingFace-FFD21E?logo=huggingface&logoColor=black)](https://huggingface.co/Cactus-Compute/models?sort=downloads) [![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/bNurx3AXTJ) [![Documentation](https://img.shields.io/badge/Documentation-4285F4?logo=googledocs&logoColor=white)](https://cactuscompute.com/docs)

## Installation

Add to your KMP project's `build.gradle.kts`:
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

For iOS projects, add the dependency through Swift Package Manager:
- In Xcode: File → Add Package Dependencies → Paste `https://github.com/cactus-compute/cactus-kotlin` → Click Add

## Getting Started

### Telemetry Setup (Optional)
```kotlin
import com.cactus.services.CactusTelemetry

// Initialize telemetry for usage analytics (optional)
CactusTelemetry.setTelemetryToken("your_token_here")
```

## Language Model (LLM)

The `CactusLM` class provides text completion capabilities with support for function calling.

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

### Function Calling
```kotlin
import com.cactus.models.Tool
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
            ),
            "units" to ToolParameter(
                type = "string", 
                description = "Temperature units (celsius/fahrenheit)", 
                required = false
            )
        )
    )
)

val result = lm.generateCompletion(
    messages = listOf(ChatMessage("What's the weather in New York?", "user")),
    params = CactusCompletionParams(maxTokens = 100),
    tools = tools
)
```

### Available Models
You can download various models by their slug identifier:
- `"qwen3-0.6"` - Default lightweight model
- Check Cactus documentation for complete model list

### LLM API Reference

#### CactusLM Class
- `suspend fun downloadModel(model: String = "qwen3-0.6"): Boolean` - Download a model
- `suspend fun initializeModel(params: CactusInitParams): Boolean` - Initialize model for inference
- `suspend fun generateCompletion(messages: List<ChatMessage>, params: CactusCompletionParams, tools: List<Tool>? = null, onToken: CactusStreamingCallback? = null): CactusCompletionResult?` - Generate text completion
- `fun unload()` - Free model from memory
- `fun isLoaded(): Boolean` - Check if model is loaded

#### Data Classes
- `CactusInitParams(model: String?, contextSize: Int?)` - Model initialization parameters
- `CactusCompletionParams(temperature: Double, topK: Int, topP: Double, maxTokens: Int, stopSequences: List<String>, bufferSize: Int)` - Completion parameters
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

The `CactusSTT` class provides speech recognition capabilities using Vosk models.

### Basic Usage
```kotlin
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT()

    // Download STT model (default: vosk-en-us)
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

    // Transcribe from audio file
    val fileResult = stt.transcribe(
        SpeechRecognitionParams(),
        filePath = "/path/to/audio.wav"
    )

    // Stop transcription
    stt.stop()
}
```

### Available Voice Models
```kotlin
// Get list of available voice models
stt.getVoiceModels()

// Check if model is downloaded
stt.isModelDownloaded("vosk-en-us")
```

### STT API Reference

#### CactusSTT Class
- `suspend fun download(model: String = "vosk-en-us"): Boolean` - Download STT model
- `suspend fun init(model: String?): Boolean` - Initialize STT model
- `suspend fun transcribe(params: SpeechRecognitionParams, filePath: String? = null): SpeechRecognitionResult?` - Transcribe speech
- `fun stop()` - Stop ongoing transcription
- `fun isReady(): Boolean` - Check if STT is ready
- `suspend fun getVoiceModels(): List<VoiceModel>` - Get available voice models
- `suspend fun isModelDownloaded(modelName: String): Boolean` - Check if model is downloaded

#### Data Classes
- `SpeechRecognitionParams(maxSilenceDuration: Long, maxDuration: Long, sampleRate: Int)` - Speech recognition parameters
- `SpeechRecognitionResult(success: Boolean, text: String?, processingTime: Double?)` - Transcription result
- `VoiceModel` - Information about available voice models

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

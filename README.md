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
        mavenCentral()
    }
}
```

### 2. Add to your KMP project's `build.gradle.kts`:
```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("com.cactuscompute:cactus:1.0.1-beta")
            }
        }
    }
}
```

### 3. Add the permissions to your manifest (Android)
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

// Disable telemetry (optional, enabled by default)
CactusTelemetry.isTelemetryEnabled = false

// Set your organization's telemetry token (optional)
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
        // Throws exception on failure
        lm.downloadModel("qwen3-0.6")
        
        // Initialize the model
        // Throws exception on failure
        lm.initializeModel(
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
import com.cactus.models.CactusFunction
import com.cactus.models.ToolParametersSchema
import com.cactus.models.ToolParameter
import com.cactus.models.createTool

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
- `suspend fun downloadModel(model: String = "qwen3-0.6")` - Download an LLM model by slug (e.g., "qwen3-0.6", "gemma3-270m"). Throws exception on failure.
- `suspend fun initializeModel(params: CactusInitParams)` - Initialize a model for inference. Throws exception on failure.
- `suspend fun generateCompletion(messages: List<ChatMessage>, params: CactusCompletionParams = CactusCompletionParams(), onToken: CactusStreamingCallback? = null): CactusCompletionResult?` - Generate text completion. Supports streaming via the `onToken` callback and different inference modes (local, remote, and fallbacks).
- `suspend fun generateEmbedding(text: String, modelName: String? = null): CactusEmbeddingResult?` - Generate embeddings for the given text.
- `suspend fun getModels(): List<CactusModel>` - Get a list of available models. Results are cached locally to reduce network requests.
- `fun unload()` - Unload the current model and free resources.
- `fun isLoaded(): Boolean` - Check if a model is currently loaded.

#### Data Classes
- `CactusInitParams(model: String? = null, contextSize: Int? = null)` - Parameters for model initialization.
- `CactusCompletionParams(model: String? = null, temperature: Double? = null, topK: Int? = null, topP: Double? = null, maxTokens: Int = 200, stopSequences: List<String> = listOf("<|im_end|>", "<end_of_turn>"), tools: List<CactusTool> = emptyList(), mode: InferenceMode = InferenceMode.LOCAL, cactusToken: String? = null)` - Parameters for text completion.
- `CactusCompletionResult(success: Boolean, response: String? = null, timeToFirstTokenMs: Double? = null, totalTimeMs: Double? = null, tokensPerSecond: Double? = null, prefillTokens: Int? = null, decodeTokens: Int? = null, totalTokens: Int? = null, toolCalls: List<ToolCall>? = emptyList())` - The result of a text completion.
- `CactusEmbeddingResult(success: Boolean, embeddings: List<Double> = listOf(), dimension: Int? = null, errorMessage: String? = null)` - The result of embedding generation.
- `ChatMessage(content: String, role: String, timestamp: Long? = null)` - A chat message with role (e.g., "user", "assistant").
- `CactusModel(created_at: String, slug: String, download_url: String, size_mb: Int, supports_tool_calling: Boolean, supports_vision: Boolean, name: String, isDownloaded: Boolean = false, quantization: Int = 8)` - Information about an available model.
- `InferenceMode` - Enum for selecting inference mode (`LOCAL`, `REMOTE`, `LOCAL_FIRST`, `REMOTE_FIRST`).
- `ToolCall(name: String, arguments: Map<String, String>)` - Represents a tool call returned by the model.
- `CactusTool(type: String = "function", function: CactusFunction)` - Defines a tool that can be called by the model.
- `CactusFunction(name: String, description: String, parameters: ToolParametersSchema)` - Function definition for a tool.
- `ToolParametersSchema(type: String = "object", properties: Map<String, ToolParameter>, required: List<String>)` - Schema for tool parameters.
- `ToolParameter(type: String, description: String, required: Boolean = false)` - A parameter definition for a tool.

#### Helper Functions
- `createTool(name: String, description: String, parameters: Map<String, ToolParameter>): CactusTool` - Helper function to create a tool with the correct schema.

## Tool Filtering

The `ToolFilterService` enables intelligent filtering of tools to optimize function calling by selecting only the most relevant tools for a given user query. This is particularly useful when you have many tools defined and want to reduce token usage and improve model performance.

### How It Works

Tool filtering is automatically enabled in `CactusLM` when tools are provided. The filtering happens before the completion request is sent to the model, analyzing the user's message to determine which tools are most relevant.

### Filter Strategies

Two filtering strategies are available:

#### SIMPLE (Default)
Fast keyword-based matching with fuzzy scoring. This strategy:
- Extracts keywords from the user query
- Matches keywords against tool names and descriptions
- Scores and ranks tools based on match quality
- Filters out tools below the similarity threshold

#### SEMANTIC
More accurate but slower semantic matching using embeddings. This strategy:
- Generates embeddings for the user query
- Generates embeddings for each tool's description
- Calculates cosine similarity between query and tools
- Falls back to SIMPLE strategy if embedding generation fails

### Configuration

Configure tool filtering when creating a `CactusLM` instance:

```kotlin
import com.cactus.CactusLM
import com.cactus.services.ToolFilterConfig
import com.cactus.services.ToolFilterStrategy

// Enable with default settings (SIMPLE strategy, max 3 tools)
val lm = CactusLM(
    enableToolFiltering = true,
    toolFilterConfig = ToolFilterConfig.simple(maxTools = 3)
)

// Custom configuration with SIMPLE strategy
val lm = CactusLM(
    enableToolFiltering = true,
    toolFilterConfig = ToolFilterConfig(
        strategy = ToolFilterStrategy.SIMPLE,
        maxTools = 5,
        similarityThreshold = 0.3
    )
)

// Use SEMANTIC strategy for more accurate filtering
val lm = CactusLM(
    enableToolFiltering = true,
    toolFilterConfig = ToolFilterConfig(
        strategy = ToolFilterStrategy.SEMANTIC,
        maxTools = 3,
        similarityThreshold = 0.5
    )
)

// Disable tool filtering
val lm = CactusLM(enableToolFiltering = false)
```

### Configuration Parameters

- `strategy` - The filtering algorithm: `SIMPLE` (default, fast) or `SEMANTIC` (slower but more accurate)
- `maxTools` - Maximum number of tools to pass to the model (default: null, meaning no limit)
- `similarityThreshold` - Minimum score required for a tool to be included (default: 0.3)

### Example Usage

```kotlin
import com.cactus.CactusLM
import com.cactus.services.ToolFilterConfig
import com.cactus.services.ToolFilterStrategy
import com.cactus.models.CactusTool

runBlocking {
    val lm = CactusLM(
        enableToolFiltering = true,
        toolFilterConfig = ToolFilterConfig.simple(maxTools = 3)
    )
    
    lm.initializeModel(CactusInitParams(model = "qwen3-0.6"))
    
    // Define many tools
    val tools = listOf(
        CactusTool(/* weather tool */),
        CactusTool(/* calculator tool */),
        CactusTool(/* search tool */),
        CactusTool(/* email tool */),
        CactusTool(/* calendar tool */),
        // ... more tools
    )
    
    // Tool filtering automatically selects the most relevant tools
    val result = lm.generateCompletion(
        messages = listOf(
            ChatMessage(content = "What's the weather like today?", role = "user")
        ),
        params = CactusCompletionParams(
            tools = tools,  // All tools provided
            temperature = 0.7f
        )
    )
    
    // Only the most relevant tools (e.g., weather tool) are sent to the model
    // Console output will show: "Tool filtering: 10 -> 3 tools"
}
```

### Performance Considerations

- **SIMPLE strategy**: Fast, suitable for real-time applications and mobile devices
- **SEMANTIC strategy**: Requires embedding generation for each tool, slower but more accurate for complex queries
- **Threshold tuning**: Lower thresholds include more tools, higher thresholds are more selective
- **Max tools**: Limit the number of tools to reduce token usage and improve model focus

### Fallback Behavior

- If no tools meet the similarity threshold, all tools are returned (up to `maxTools` limit)
- If SEMANTIC strategy fails (e.g., model not supporting embeddings), it falls back to SIMPLE strategy
- Tool filtering can be disabled entirely by setting `enableToolFiltering = false`

## Speech-to-Text (STT)

The `CactusSTT` class provides speech recognition capabilities using on-device models from **Whisper**.

### Basic Usage

```kotlin
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT()

    // Download a Whisper model (e.g., whisper-tiny)
    val downloadSuccess = stt.download("whisper-tiny")
    
    // Initialize the model
    val initSuccess = stt.init("whisper-tiny")

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

#### Transcribing from an Audio File
```kotlin
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT()

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
You can get a list of available Whisper models.
```kotlin
val whisperModels = CactusSTT().getVoiceModels()

// Check if a model is downloaded
stt.isModelDownloaded("whisper-tiny")
```

### STT API Reference

#### CactusSTT Class
- `CactusSTT(provider: TranscriptionProvider = TranscriptionProvider.WHISPER)` - Constructor to specify the transcription provider.
- `suspend fun download(model: String = "whisper-tiny"): Boolean` - Download an STT model (e.g., "whisper-tiny" or "whisper-base"). Defaults to last downloaded model.
- `suspend fun init(model: String = "whisper-tiny"): Boolean` - Initialize an STT model for transcription. Defaults to last downloaded model.
- `suspend fun transcribe(params: SpeechRecognitionParams = SpeechRecognitionParams(), filePath: String? = null, mode: TranscriptionMode = TranscriptionMode.LOCAL, apiKey: String? = null): SpeechRecognitionResult?` - Transcribe speech from microphone or file. Supports different transcription modes.
- `suspend fun warmUpWispr(apiKey: String)` - Warms up the remote Wispr service for lower latency.
- `fun stop()` - Stop ongoing transcription.
- `fun isReady(): Boolean` - Check if the STT service is initialized and ready.
- `suspend fun getVoiceModels(provider: TranscriptionProvider = this.provider): List<VoiceModel>` - Get a list of available voice models for the specified provider. Defaults to the instance's provider.
- `suspend fun isModelDownloaded(modelName: String = "whisper-tiny"): Boolean` - Check if a specific model has been downloaded. Defaults to last downloaded model.

#### Data Classes
- `TranscriptionProvider` - Enum for selecting the provider (`WHISPER`).
- `SpeechRecognitionParams(maxSilenceDuration: Long = 1000L, maxDuration: Long = 30000L, sampleRate: Int = 16000, model: String?)` - Parameters for controlling speech recognition.
- `SpeechRecognitionResult(success: Boolean, text: String? = null, eventSuccess: Boolean = true, processingTime: Double? = null)` - The result of a transcription.
- `VoiceModel(created_at: String, slug: String, language: String, url: String, size_mb: Int, file_name: String, provider: String = "whisper", isDownloaded: Boolean = false)` - Contains information about an available voice model.
- `TranscriptionMode` - Enum for transcription mode (`LOCAL`, `REMOTE`, `LOCAL_FIRST`, `REMOTE_FIRST`).

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
- Speech-to-text transcription with Whisper
- Voice model management
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

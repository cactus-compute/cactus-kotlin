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
                implementation("com.cactuscompute:cactus:1.2.0-beta")
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
Initialize the Cactus context in your Activity's `onCreate()` method before using any SDK functionality:f
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
- `ChatMessage(content: String, role: String, timestamp: Long? = null, images: List<String>)` - A chat message with role (e.g., "user", "assistant").
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

## Vision / Image Analysis

The `CactusLM` class supports vision models that can analyze and describe images. Vision models can process both text prompts and image inputs to provide detailed descriptions and analysis.

### Basic Vision Usage

```kotlin
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import com.cactus.ChatMessage
import kotlinx.coroutines.runBlocking

runBlocking {
    val lm = CactusLM()

    try {
        // Get available vision models
        val models = lm.getModels()
        val visionModels = models.filter { it.supports_vision }

        // Download and initialize a vision model
        lm.downloadModel(visionModels.first().slug)
        lm.initializeModel(CactusInitParams(model = visionModels.first().slug))

        // Analyze an image
        val result = lm.generateCompletion(
            messages = listOf(
                ChatMessage("You are a helpful AI assistant that can analyze images.", "system"),
                ChatMessage(
                    content = "Describe this image in detail.",
                    role = "user",
                    images = listOf("/path/to/image.jpg")
                )
            ),
            params = CactusCompletionParams(maxTokens = 300)
        )

        result?.let { response ->
            if (response.success) {
                println("Analysis: ${response.response}")
                println("Tokens per second: ${response.tokensPerSecond}")
                println("Time to first token: ${response.timeToFirstTokenMs}ms")
            }
        }
    } finally {
        lm.unload()
    }
}
```

### Streaming Vision Analysis

```kotlin
runBlocking {
    val lm = CactusLM()

    // Download and initialize a vision model
    val visionModel = lm.getModels().first { it.supports_vision }
    lm.downloadModel(visionModel.slug)
    lm.initializeModel(CactusInitParams(model = visionModel.slug))

    var streamingResponse = ""

    val result = lm.generateCompletion(
        messages = listOf(
            ChatMessage("You are a helpful AI assistant that can analyze images.", "system"),
            ChatMessage(
                content = "What do you see in this image?",
                role = "user",
                images = listOf("/path/to/image.jpg")
            )
        ),
        params = CactusCompletionParams(maxTokens = 300),
        onToken = { token, _ ->
            streamingResponse += token
            print(token)
        }
    )

    println("\n\nFinal analysis: ${result?.response}")

    lm.unload()
}
```

### Vision API Notes

- Vision models are identified by the `supports_vision` property in `CactusModel`
- Images are passed via the `images` parameter in `ChatMessage` as file paths
- Supports both streaming and non-streaming modes
- Compatible with all standard completion parameters (temperature, maxTokens, etc.)
- See the `VisionPage.kt` example in `example/composeApp/src/commonMain/kotlin/com/cactus/example/pages/` for a complete UI implementation

## Speech-to-Text (STT)

The `CactusSTT` class provides speech recognition capabilities using on-device models from **Whisper**.

### Basic Usage

```kotlin
import com.cactus.CactusSTT
import com.cactus.CactusInitParams
import com.cactus.CactusTranscriptionParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT()

    try {
        // Download a Whisper model (e.g., whisper-tiny)
        // Throws exception on failure
        stt.downloadModel("whisper-tiny")

        // Initialize the model
        // Throws exception on failure
        stt.initializeModel(CactusInitParams(model = "whisper-tiny"))

        // Transcribe from an audio file
        val result = stt.transcribe(
            filePath = "/path/to/audio.wav",
            params = CactusTranscriptionParams()
        )

        result?.let { transcription ->
            if (transcription.success) {
                println("Transcribed: ${transcription.text}")
                println("Total time: ${transcription.totalTimeMs}ms")
            }
        }
    } catch (e: Exception) {
        println("Transcription failed: ${e.message}")
    }
}
```

### Streaming Transcription

```kotlin
import com.cactus.CactusSTT
import com.cactus.CactusInitParams
import com.cactus.CactusTranscriptionParams
import kotlinx.coroutines.runBlocking

runBlocking {
    val stt = CactusSTT()

    try {
        // Download and initialize model
        stt.downloadModel("whisper-tiny")
        stt.initializeModel(CactusInitParams(model = "whisper-tiny"))

        // Transcribe with token streaming
        val result = stt.transcribe(
            filePath = "/path/to/audio.wav",
            params = CactusTranscriptionParams(),
            onToken = { token, _ ->
                print(token)
            }
        )

        result?.let { transcription ->
            if (transcription.success) {
                println("\nFinal transcription: ${transcription.text}")
            }
        }
    } catch (e: Exception) {
        println("Transcription failed: ${e.message}")
    }
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
    filePath = "/path/to/audio.wav",
    params = CactusTranscriptionParams(),
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
- `CactusSTT()` - Constructor for the STT service.
- `suspend fun downloadModel(model: String = "whisper-tiny")` - Download an STT model (e.g., "whisper-tiny" or "whisper-base"). Defaults to last initialized model. Throws exception on failure.
- `suspend fun initializeModel(params: CactusInitParams)` - Initialize an STT model for transcription using the model specified in params. Throws exception on failure.
- `suspend fun transcribe(filePath: String, prompt: String = "<|startoftranscript|><|en|><|transcribe|><|notimestamps|>", params: CactusTranscriptionParams = CactusTranscriptionParams(), onToken: CactusStreamingCallback? = null, mode: TranscriptionMode = TranscriptionMode.LOCAL, apiKey: String? = null): CactusTranscriptionResult?` - Transcribe speech from an audio file. Supports streaming via the `onToken` callback and different transcription modes (local, remote, and fallbacks).
- `suspend fun warmUpWispr(apiKey: String)` - Warms up the remote Wispr service for lower latency.
- `fun isReady(): Boolean` - Check if the STT service is initialized and ready.
- `suspend fun getVoiceModels(): List<VoiceModel>` - Get a list of available voice models. Results are cached locally to reduce network requests.
- `suspend fun isModelDownloaded(modelName: String = "whisper-tiny"): Boolean` - Check if a specific model has been downloaded. Defaults to last initialized model.

#### Data Classes
- `CactusInitParams(model: String? = null, contextSize: Int? = null)` - Parameters for model initialization (shared with CactusLM).
- `CactusTranscriptionParams(model: String? = null, maxTokens: Int = 512, stopSequences: List<String> = listOf("<|im_end|>", "<end_of_turn>"))` - Parameters for controlling speech transcription.
- `CactusTranscriptionResult(success: Boolean, text: String? = null, totalTimeMs: Double? = null)` - The result of a transcription.
- `VoiceModel(created_at: String, slug: String, download_url: String, size_mb: Int, quantization: Int, isDownloaded: Boolean = false)` - Contains information about an available voice model.
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

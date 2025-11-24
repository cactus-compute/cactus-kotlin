# Cactus Kotlin Multiplatform SDK Examples

This directory contains comprehensive examples demonstrating all the features of the Cactus Kotlin SDK. Each example is implemented as a separate page with a clean, consistent UI and complete functionality.

## 📱 Example Structure

The examples are organized as a single Kotlin Multiplatform app with navigation to different feature demonstrations:

### Main Navigation (`App.kt`)
- **HomePage**: Central navigation hub with links to all examples
- Clean Material Design interface
- Easy access to all SDK features

### Feature Examples

#### 1. **Basic Completion** (`BasicCompletionPage.kt`)
- **What it demonstrates**: Simple, straightforward text completion
- **Features**:
  - Model downloading and initialization
  - Single-turn text completion
  - Performance metrics (TTFT, TPS)

#### 2. **Streaming Completion** (`StreamingCompletionPage.kt`)  
- **What it demonstrates**: Real-time streaming text generation
- **Features**:
  - Model setup (download + initialization)
  - Live streaming text generation
  - Real-time UI updates as tokens arrive
  - Performance metrics

#### 3. **Function Calling** (`FunctionCallingPage.kt`)
- **What it demonstrates**: Tool/function calling capabilities
- **Features**:
  - Model setup
  - Structured function definitions
  - Tool call execution
  - Function response handling

#### 4. **Hybrid Completion** (`HybridCompletionPage.kt`)
- **What it demonstrates**: Cloud fallback functionality
- **Features**:
  - Cloud-based completion without local model
  - Cactus token authentication
  - Seamless local/cloud switching

#### 5. **Fetch Models** (`FetchModelsPage.kt`)
- **What it demonstrates**: Model discovery and management
- **Features**:
  - Available models listing
  - Model metadata (size, capabilities, download status)
  - Model filtering and search
  - Refresh functionality

#### 6. **Embedding Generation** (`EmbeddingPage.kt`)
- **What it demonstrates**: Text embedding generation
- **Features**:
  - Model setup
  - Text-to-vector conversion
  - Embedding dimensions and vector inspection

#### 7. **Vision / Image Analysis** (`VisionPage.kt`)
- **What it demonstrates**: Image analysis using vision models
- **Features**:
  - Vision model discovery and filtering
  - Model downloading and initialization
  - Image file selection
  - Real-time streaming image analysis
  - Vision-specific performance metrics (TTFT, TPS)
  - Multi-modal input handling (text + images)

## 🚀 How to Run

1. **Setup Dependencies**:
   ```bash
   ./gradlew build
   ```

2. **Run the App**:
   ```bash
   # Android
   ./gradlew composeApp:installDebug
   
   # iOS Simulator
   ./gradlew composeApp:iosSimulatorArm64Test
   ```

3. **Navigate Examples**:
   - Start from the main page
   - Tap any example to explore
   - Follow the step-by-step UI prompts

## 📋 Example Flow

### Typical Usage Pattern:
1. **Download Model** → Download required AI model
2. **Initialize Model** → Load model into memory  
3. **Use Features** → Generate text, embeddings, search, etc.
4. **View Results** → See outputs, metrics, and data

## 🔧 Configuration

- **Telemetry Token**: Set in each example for analytics
- **Model Selection**: Default to `qwen3-0.6` (customizable)
- **RAG Database**: Stored in-memory using Kotlin collections
- **Sample Data**: Landmark information for RAG demonstration

This example app serves as both a demonstration and a learning resource for integrating the Cactus SDK into Kotlin Multiplatform applications.
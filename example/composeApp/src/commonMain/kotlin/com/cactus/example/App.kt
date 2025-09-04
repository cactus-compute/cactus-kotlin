package com.cactus.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cactus.*
import com.cactus.services.CactusTelemetry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var lm by remember { mutableStateOf(CactusLM()) }
    var stt by remember { mutableStateOf(CactusSTT()) }

    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }

    var isSttDownloaded by remember { mutableStateOf(false) }
    var isSttLoaded by remember { mutableStateOf(false) }
    var isSttDownloading by remember { mutableStateOf(false) }
    var isSttInitializing by remember { mutableStateOf(false) }

    var outputText by remember { mutableStateOf("Welcome to Cactus Demo!") }
    var lastResponse by remember { mutableStateOf<String?>(null) }
    var lastTPS by remember { mutableStateOf<Double?>(null) }
    var lastTTFT by remember { mutableStateOf<Double?>(null) }
    var availableModels by remember { mutableStateOf<List<CactusModel>>(emptyList()) }

    @Composable
    fun StatItem(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(value)
        }
    }

    // Initialize telemetry
    LaunchedEffect(Unit) {
        try {
            val deviceId = CactusTelemetry.fetchDeviceId()
            deviceId?.let { it ->
                CactusTelemetry.init("f3a1c0b0-4c6f-4261-ac15-0c03b12d83a2", it)
            }
        } catch (e: Exception) {
            println("App: Exception during telemetry init: $e")
        }
    }

    fun downloadModel() {
        scope.launch {
            isDownloading = true
            outputText = "Downloading model..."

            try {
                val downloadSuccess = lm.downloadModel()
                if (downloadSuccess) {
                    isModelDownloaded = true
                    outputText = "Model downloaded successfully! Click \"Initialize\" to load it."
                } else {
                    outputText = "Failed to download model."
                }
            } catch (e: Exception) {
                outputText = "Error downloading model: ${e.message}"
            } finally {
                isDownloading = false
            }
        }
    }

    fun downLoadSttModel() {
        scope.launch {
            isSttDownloading = true
            outputText = "Downloading STT model..."

            try {
                val downloadSuccess = stt.download()
                if (downloadSuccess) {
                    isSttDownloaded = true
                    outputText = "STT Model downloaded successfully! Click \"Initialize\" to load it."
                } else {
                    outputText = "Failed to download STT model."
                }
            } catch (e: Exception) {
                outputText = "Error downloading STT model: ${e.message}"
            } finally {
                isSttDownloading = false
            }
        }
    }

    fun initializeModel() {
        scope.launch {
            isInitializing = true
            outputText = "Initializing model..."

            try {
                val loadSuccess = lm.initializeModel(params = CactusInitParams(contextSize = 2048))
                if (loadSuccess) {
                    isModelLoaded = true
                    outputText = "Model initialized successfully! Ready to generate completions."
                } else {
                    outputText = "Failed to initialize model."
                }
            } catch (e: Exception) {
                outputText = "Error initializing model: ${e.message}"
            } finally {
                isInitializing = false
            }
        }
    }

    fun initializeSTTModel() {
        scope.launch {
            isSttInitializing = true
            outputText = "Initializing STT model..."

            try {
                val loadSuccess = stt.init()
                if (loadSuccess) {
                    isSttLoaded = true
                    outputText = "STT Model initialized successfully! Ready to transcribe."
                } else {
                    outputText = "Failed to initialize STT model."
                }
            } catch (e: Exception) {
                outputText = "Error initializing STT model: ${e.message}"
            } finally {
                isSttInitializing = false
            }
        }
    }

    fun generateCompletion() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }

        scope.launch {
            isInitializing = true
            outputText = "Generating response..."

            try {
                val resp = lm.generateCompletion(
                    messages = listOf(ChatMessage("Tell me a joke about computers.", "user")),
                    params = CactusCompletionParams(
                        maxTokens = 50,
                        temperature = 0.7
                    )
                )

                if (resp != null && resp.success) {
                    lastResponse = resp.response
                    lastTPS = resp.tokensPerSecond
                    lastTTFT = resp.timeToFirstTokenMs
                    outputText = "Generation completed successfully!"
                } else {
                    outputText = "Failed to generate response."
                    lastResponse = null
                    lastTPS = null
                    lastTTFT = null
                }
            } catch (e: Exception) {
                outputText = "Error generating response: ${e.message}"
                lastResponse = null
                lastTPS = null
                lastTTFT = null
            } finally {
                isInitializing = false
            }
        }
    }

    fun transcribe() {
        scope.launch {
            outputText = "Listening..."
            val result = stt.transcribe()
            outputText = result?.text ?: "Transcription failed."
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Cactus Demo") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // LLM Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Language Model", style = MaterialTheme.typography.titleLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { downloadModel() },
                                enabled = !isDownloading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isModelDownloaded) "Downloaded ✓" else "Download")
                            }
                            Button(
                                onClick = { initializeModel() },
                                enabled = isModelDownloaded && !isInitializing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isModelLoaded) "Initialized ✓" else "Initialize")
                            }
                        }
                        Button(
                            onClick = { generateCompletion() },
                            enabled = isModelLoaded && !isInitializing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Completion")
                        }
                    }
                }

                // STT Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Speech-to-Text", style = MaterialTheme.typography.titleLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { downLoadSttModel() },
                                enabled = !isSttDownloading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isSttDownloaded) "Downloaded ✓" else "Download")
                            }
                            Button(
                                onClick = { initializeSTTModel() },
                                enabled = isSttDownloaded && !isSttInitializing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isSttLoaded) "Initialized ✓" else "Initialize")
                            }
                        }
                        Button(
                            onClick = { transcribe() },
                            enabled = isSttLoaded && !isSttInitializing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Transcription")
                        }
                    }
                }

                // Status Section
                if (isDownloading || isInitializing || isSttDownloading || isSttInitializing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            outputText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Output Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Output", style = MaterialTheme.typography.titleLarge)
                        Text(outputText)

                        lastResponse?.let { response ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Response:", style = MaterialTheme.typography.titleMedium)
                            Text(response)

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("TTFT", "${lastTTFT ?: 0.0} ms")
                                StatItem("TPS", "${lastTPS ?: 0.0}")
                            }
                        }

                        if (availableModels.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Available Models:", style = MaterialTheme.typography.titleMedium)
                            Column {
                                availableModels.forEach { model ->
                                    Text(
                                        "• ${model.name} (${model.sizeMb}MB)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
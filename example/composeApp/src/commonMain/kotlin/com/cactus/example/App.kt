package com.cactus.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.cactus.*
import com.cactus.services.CactusTelemetry
import com.cactus.services.CactusUtils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var lm by remember { mutableStateOf(CactusLM()) }
    
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("") }
    var lastResponse by remember { mutableStateOf<String?>(null) }
    var lastTPS by remember { mutableStateOf<Double?>(null) }
    var lastTTFT by remember { mutableStateOf<Double?>(null) }
    var availableModels by remember { mutableStateOf<List<CactusModel>>(emptyList()) }
    
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
                    outputText = "Model downloaded successfully! Click \"Initialize Model\" to load it."
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
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cactus Demo",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Buttons section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { downloadModel() },
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isModelDownloaded) "Model Downloaded ✓" else "Download Model")
                    }
                    
                    Button(
                        onClick = { initializeModel() },
                        enabled = !isInitializing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isModelLoaded) "Model Initialized ✓" else "Initialize Model")
                    }
                    
                    Button(
                        onClick = { generateCompletion() },
                        enabled = !isDownloading && !isInitializing && isModelLoaded,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate")
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Status section
                if (isDownloading || isInitializing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Processing...")
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Output section
                Card(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Text(
                            "Output:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(outputText)
                        
                        lastResponse?.let { response ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Response:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                LazyColumn {
                                    item {
                                        Text(response)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "TTFT",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("$lastTTFT ms")
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "TPS",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("$lastTPS")
                                }
                            }
                        }
                        
                        // Available models section
                        if (availableModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Available Models:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier.height(100.dp)
                            ) {
                                items(availableModels) { model ->
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
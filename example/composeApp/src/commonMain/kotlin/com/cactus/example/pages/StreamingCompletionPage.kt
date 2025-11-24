package com.cactus.example.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cactus.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingCompletionPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }
    
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Click \"Download Model\" to begin.") }
    var streamingResponse by remember { mutableStateOf("") }
    var lastTPS by remember { mutableStateOf(0.0) }
    var lastTTFT by remember { mutableStateOf(0.0) }

    fun downloadModel() {
        scope.launch {
            isDownloading = true
            outputText = "Downloading model..."

            try {
                lm.downloadModel()
                if (isActive) {
                    isModelDownloaded = true
                    outputText = "Model downloaded successfully! Click \"Initialize Model\" to load it."
                }
            } catch (e: Exception) {
                if (isActive) {
                    outputText = "Error downloading model: ${e.message}"
                }
            } finally {
                if (isActive) {
                    isDownloading = false
                }
            }
        }
    }

    fun initializeModel() {
        scope.launch {
            isInitializing = true
            outputText = "Initializing model..."

            try {
                lm.initializeModel(CactusInitParams())
                if (isActive) {
                    isModelLoaded = true
                    outputText = "Model initialized successfully! Ready to generate completions."
                }
            } catch (e: Exception) {
                if (isActive) {
                    outputText = "Error initializing model: ${e.message}"
                }
            } finally {
                if (isActive) {
                    isInitializing = false
                }
            }
        }
    }

    fun generateStreamingCompletion() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }

        scope.launch {
            isGenerating = true
            streamingResponse = ""
            outputText = "Generating streaming response..."
            var firstToken = true

            try {
                val resp = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage("You are Cactus, a very capable AI assistant running offline on a smartphone", "system"),
                        ChatMessage("Tell me a short story about a robot learning to paint.", "user")
                    ),
                    params = CactusCompletionParams(
                        maxTokens = 200
                    ),
                    onToken = { token, _ ->
                        if (firstToken) {
                            streamingResponse = ""
                            firstToken = false
                        }
                        streamingResponse += token
                    }
                )

                if (isActive) {
                    if (resp != null && resp.success) {
                        lastTPS = resp.tokensPerSecond ?: 0.0
                        lastTTFT = resp.timeToFirstTokenMs ?: 0.0
                        outputText = "Streaming completion generated successfully!"
                    } else {
                        outputText = "Failed to generate streaming response."
                        lastTPS = 0.0
                        lastTTFT = 0.0
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    outputText = "Error generating streaming response: ${e.message}"
                    lastTPS = 0.0
                    lastTTFT = 0.0
                }
            } finally {
                if (isActive) {
                    isGenerating = false
                }
            }
        }
    }

    @Composable
    fun StatItem(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(value)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            lm.unload()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streaming Completion") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Buttons section
            Button(
                onClick = { downloadModel() },
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Downloading...")
                    }
                } else {
                    Text(if (isModelDownloaded) "Model Downloaded ✓" else "Download Model")
                }
            }

            Button(
                onClick = { initializeModel() },
                enabled = !isInitializing && !isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isInitializing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Initializing...")
                    }
                } else {
                    Text(if (isModelLoaded) "Model Initialized ✓" else "Initialize Model")
                }
            }

            Button(
                onClick = { generateStreamingCompletion() },
                enabled = !isDownloading && !isInitializing && !isGenerating && isModelLoaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Generating...")
                    }
                } else {
                    Text("Generate Streaming Completion")
                }
            }

            // Output section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Output:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(outputText)
                    
                    if (streamingResponse.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Streaming Response:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = streamingResponse,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
                        if (!isGenerating && lastTPS > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("TTFT", "${lastTTFT.toStringAsFixed(2)} ms")
                                StatItem("TPS", lastTPS.toStringAsFixed(2))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Double.toStringAsFixed(digits: Int): String {
    return this.toString().let { str ->
        val dotIndex = str.indexOf('.')
        if (dotIndex == -1) {
            str + "." + "0".repeat(digits)
        } else {
            val afterDot = str.substring(dotIndex + 1)
            val formatted = str.substring(0, dotIndex + 1) + 
                if (afterDot.length >= digits) {
                    afterDot.substring(0, digits)
                } else {
                    afterDot + "0".repeat(digits - afterDot.length)
                }
            formatted
        }
    }
}
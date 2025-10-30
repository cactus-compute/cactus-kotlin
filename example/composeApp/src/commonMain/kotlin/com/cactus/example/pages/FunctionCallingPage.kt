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
import com.cactus.models.ToolParameter
import com.cactus.models.createTool
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionCallingPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }
    
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Click \"Download Model\" to begin.") }
    var lastResponse by remember { mutableStateOf<CactusCompletionResult?>(null) }
    var lastTPS by remember { mutableStateOf(0.0) }
    var lastTTFT by remember { mutableStateOf(0.0) }

    fun downloadModel() {
        scope.launch {
            isDownloading = true
            outputText = "Downloading model..."
            
            try {
                lm.downloadModel()
                isModelDownloaded = true
                outputText = "Model downloaded successfully! Click \"Initialize Model\" to load it."
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
                lm.initializeModel(CactusInitParams())
                isModelLoaded = true
                outputText = "Model initialized successfully! Ready to generate completions."
            } catch (e: Exception) {
                outputText = "Error initializing model: ${e.message}"
            } finally {
                isInitializing = false
            }
        }
    }

    fun generateFunctionCall() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }
        
        scope.launch {
            isGenerating = true
            outputText = "Generating response with function calling..."
            
            try {
                val resp = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage("You are a helpful AI assistant with access to weather information.", "system"),
                        ChatMessage("What's the weather like in New York City?", "user")
                    ),
                    params = CactusCompletionParams(
                        maxTokens = 200,
                        tools = listOf(
                            createTool(
                                name = "get_weather",
                                description = "Get current weather information for a specific location",
                                parameters = mapOf(
                                    "location" to ToolParameter("string", "The city name to get weather for", true),
                                    "units" to ToolParameter("string", "Temperature units (celsius or fahrenheit)", false)
                                )
                            )
                        )
                    )
                )
                
                if (resp != null && resp.success) {
                    lastResponse = resp
                    lastTPS = resp.tokensPerSecond ?: 0.0
                    lastTTFT = resp.timeToFirstTokenMs ?: 0.0
                    outputText = "Function calling demonstration completed successfully!"
                } else {
                    outputText = "Failed to generate response with function calling."
                    lastResponse = null
                    lastTPS = 0.0
                    lastTTFT = 0.0
                }
            } catch (e: Exception) {
                outputText = "Error generating response: ${e.message}"
                lastResponse = null
                lastTPS = 0.0
                lastTTFT = 0.0
            } finally {
                isGenerating = false
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
                title = { Text("Function Calling") },
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
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Function Calling Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This example demonstrates how the AI model can call structured functions. We'll ask about weather and see if the model generates a proper function call.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

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
                onClick = { generateFunctionCall() },
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
                    Text("Run function calling example")
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
                    
                    lastResponse?.let { response ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Model Response:",
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
                                text = "Tool Call: ${response.toolCalls?.firstOrNull()?.name} \n" +
                                        "Arguments: ${response.toolCalls?.firstOrNull()?.arguments}",
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
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
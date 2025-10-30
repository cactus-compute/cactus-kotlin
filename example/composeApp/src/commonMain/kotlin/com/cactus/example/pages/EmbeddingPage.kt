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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddingPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }
    
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Click \"Download Model\" to begin.") }
    var embeddingResult by remember { mutableStateOf<String?>(null) }
    var sampleText by remember { mutableStateOf("What's the weather in New York?") }

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

    fun generateEmbedding() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }
        
        scope.launch {
            isGenerating = true
            outputText = "Generating embedding..."
            
            try {
                val resp = lm.generateEmbedding(text = sampleText)
                
                if (resp != null && resp.success) {
                    val preview = resp.embeddings.take(10).joinToString(", ") { it.toStringAsFixed(3) }
                    embeddingResult = buildString {
                        appendLine("✅ Embedding Generated Successfully!")
                        appendLine()
                        appendLine("📊 Dimensions: ${resp.dimension}")
                        appendLine("📏 Vector Length: ${resp.embeddings.size}")
                        appendLine("🔍 First 10 values: [$preview...]")
                        appendLine()
                        appendLine("📝 Input Text:")
                        appendLine("\"$sampleText\"")
                    }
                    outputText = "Embedding generation completed successfully!"
                } else {
                    outputText = "Failed to generate embedding."
                    embeddingResult = null
                }
            } catch (e: Exception) {
                outputText = "Error generating embedding: ${e.message}"
                embeddingResult = null
            } finally {
                isGenerating = false
            }
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
                title = { Text("Embedding Generation") },
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Text Embedding Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Convert text into numerical vectors (embeddings) that capture semantic meaning. These vectors can be used for similarity search, clustering, and other ML tasks.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Input text section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Sample Text",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = sampleText,
                        onValueChange = { sampleText = it },
                        label = { Text("Text to embed") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
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
                onClick = { generateEmbedding() },
                enabled = !isDownloading && !isInitializing && !isGenerating && isModelLoaded && sampleText.isNotBlank(),
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
                    Text("Generate Embedding")
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
                    
                    embeddingResult?.let { result ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Embedding Result:",
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
                                text = result,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
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
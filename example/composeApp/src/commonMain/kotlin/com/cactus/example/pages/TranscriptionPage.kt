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
fun TranscriptionPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stt = remember { CactusSTT() }
    
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Click \"Download Model\" to begin.") }
    var lastResponse by remember { mutableStateOf<SpeechRecognitionResult?>(null) }
    fun downloadModel() {
        scope.launch {
            isDownloading = true
            outputText = "Downloading model..."
            
            try {
                val downloadSuccess = stt.download()
                
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
                val loadSuccess = stt.init()
                if (loadSuccess) {
                    isModelLoaded = true
                    outputText = "Model initialized successfully! Ready to test transcription."
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

    fun transcribeAudio() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }
        
        scope.launch {
            isTranscribing = true
            outputText = "Listening..."
            
            try {
                val resp = stt.transcribe()
                
                if (resp != null && resp.success) {
                    lastResponse = resp
                    outputText = "Transcription completed successfully!"
                } else {
                    outputText = "Failed to transcribe."
                    lastResponse = null
                }
            } catch (e: Exception) {
                outputText = "Error transcribing: ${e.message}"
                lastResponse = null
            } finally {
                isTranscribing = false
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
            stt.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription") },
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
                        "Transcription Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This example demonstrates transcription capabilities using a local speech-to-text model. You can download the model, initialize it, and then transcribe audio input.",
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
                onClick = { transcribeAudio() },
                enabled = !isDownloading && !isInitializing && !isTranscribing && isModelLoaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTranscribing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Listening...")
                    }
                } else {
                    Text("Transcribe Audio")
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
                                text = lastResponse?.text ?: "",
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
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
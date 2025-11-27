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
import com.cactus.example.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    
    var stt by remember { mutableStateOf(CactusSTT()) }
    
    var voiceModels by remember { mutableStateOf<List<VoiceModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("whisper-medium") }
    
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var isUsingDefaultModel by remember { mutableStateOf(false) }
    var isPreparingFile by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Select a model and initialize to begin.") }
    var lastResponse by remember { mutableStateOf<CactusTranscriptionResult?>(null) }
    var downloadProgress by remember { mutableStateOf("") }
    var downloadPercentage by remember { mutableStateOf<Float?>(null) }

    fun resetState() {
        isModelLoaded = false
        isDownloading = false
        isInitializing = false
        isTranscribing = false
        isLoadingModels = false
        isUsingDefaultModel = false
        isPreparingFile = false
        voiceModels = emptyList()
        lastResponse = null
        downloadProgress = ""
        downloadPercentage = null
        selectedModel = "whisper-medium"
        outputText = "Ready to start. Select a model and initialize to begin."
    }

    fun loadVoiceModels() {
        scope.launch {
            isLoadingModels = true
            
            try {
                val models = stt.getVoiceModels()
                voiceModels = models
                isLoadingModels = false
                isUsingDefaultModel = false
                
                if (models.isNotEmpty()) {
                    if (!models.any { it.slug == selectedModel }) {
                        selectedModel = models.first().slug
                    }
                    outputText = "Models loaded. Select model and click 'Download & Initialize Model' to begin."
                } else {
                    outputText = "No models available."
                }
            } catch (e: Exception) {
                voiceModels = emptyList()
                selectedModel = "whisper-tiny"
                isLoadingModels = false
                isUsingDefaultModel = true
                outputText = "Network error loading models. Using default model"
            }
        }
    }

    fun downloadAndInitializeModel() {
        scope.launch {
            isDownloading = true
            isInitializing = true
            outputText = "Downloading and initializing model..."
            downloadProgress = "Starting download..."
            downloadPercentage = null
            
            try {
                try {
                    stt.downloadModel(
                        model = selectedModel,
                    )
                    isDownloading = false
                    downloadProgress = ""
                    downloadPercentage = null
                    outputText = "Model downloaded successfully! Initializing..."
                } catch (e: Exception) {
                    isDownloading = false
                    isInitializing = false
                    downloadProgress = ""
                    downloadPercentage = null
                    outputText = "Failed to download model."
                    return@launch
                }
                
                try {
                    stt.initializeModel(CactusInitParams(model = selectedModel))
                    isInitializing = false
                    isModelLoaded = true
                    outputText = "Model downloaded and initialized successfully! Ready to transcribe audio."
                } catch (e: Exception) {
                    outputText = "Failed to initialize model."
                    return@launch
                }
            } catch (e: Exception) {
                isDownloading = false
                isInitializing = false
                downloadProgress = ""
                downloadPercentage = null
                outputText = "Error: ${e.message}"
            }
        }
    }

//    fun transcribeFromMicrophone() {
//        if (!isModelLoaded) {
//            outputText = "Please initialize the model first."
//            return
//        }
//
//        scope.launch {
//            try {
//                isTranscribing = true
//                outputText = "Listening for audio... Speak now!"
//
//                val params = SpeechRecognitionParams(
//                    sampleRate = 16000,
//                    maxDuration = 30000, // 30 seconds
//                    maxSilenceDuration = 3000, // 3 seconds of silence
//                )
//
//                val result = withContext(Dispatchers.Default) {
//                    stt.transcribe(params = params)
//                }
//
//                isTranscribing = false
//                if (result != null && result.success) {
//                    lastResponse = result
//                    outputText = "Transcription completed successfully!"
//                } else {
//                    outputText = result?.text ?: "Failed to transcribe audio."
//                    lastResponse = null
//                }
//            } catch (e: Exception) {
//                isTranscribing = false
//                outputText = "Error during transcription: ${e.message}"
//                lastResponse = null
//            }
//        }
//    }

    val filePickerLauncher = rememberFilePickerLauncher(
    onFileSelected = { selectedPath ->
        scope.launch {
            when {
                selectedPath != null -> {
                    try {
                        isPreparingFile = false
                        isTranscribing = true
                        outputText = "Preparing audio file for transcription..."
                        outputText = "Transcribing audio file: ${selectedPath.substringAfterLast('/')}"
                        
                        val params = SpeechRecognitionParams(
                            sampleRate = 16000,
                            model = selectedModel
                        )
                        
                        val result = withContext(Dispatchers.Default) {
                            stt.transcribe(
                                filePath = selectedPath,
                                prompt = "<|startoftranscript|><|en|><|transcribe|><|notimestamps|>"
                            )
                        }
                        
                        isTranscribing = false
                        if (result != null && result.success) {
                            lastResponse = result
                            outputText = "File transcription completed successfully!"
                        } else {
                            outputText = result?.text ?: "Failed to transcribe audio file."
                            lastResponse = null
                        }
                    } catch (e: Exception) {
                        isTranscribing = false
                        outputText = "Error during file transcription: ${e.message}"
                        lastResponse = null
                    }
                }
                else -> {
                    isPreparingFile = false
                    outputText = "File selection cancelled."
                }
            }
        }
    },
    mimeType = "audio/*"
)
    fun transcribeFromFile() {
        scope.launch {
            isPreparingFile = true
            filePickerLauncher.launch()
        }
    }

    fun stopTranscription() {
        scope.launch {
            try {
                outputText = "Transcribing..."
                withContext(Dispatchers.Default) {
                    stt.stop()
                }
                outputText = "Processing recorded audio..."
            } catch (e: Exception) {
                outputText = "Error stopping transcription: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadVoiceModels()
    }

    DisposableEffect(Unit) {
        onDispose {
            stt.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Speech-to-Text") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Speech-to-Text Transcription Demo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        "This example demonstrates speech-to-text transcription using CactusSTT. Select a provider and model, initialize it, then you can transcribe from microphone input or from audio files.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDownloading || isInitializing || isTranscribing || isLoadingModels) {
                            LinearProgressIndicator(
                                progress = { downloadPercentage ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Text(
                        "Model Selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    when {
                        isLoadingModels -> {
                            Text("Loading models...")
                        }
                        isUsingDefaultModel -> {
                            Text("Using default model: $selectedModel")
                        }
                        voiceModels.isEmpty() -> {
                            Text("No models available")
                        }
                        else -> {
                            var modelExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = modelExpanded,
                                onExpandedChange = { 
                                    if (!isModelLoaded) modelExpanded = !modelExpanded 
                                }
                            ) {
                                OutlinedTextField(
                                    value = voiceModels.find { it.slug == selectedModel }?.let { 
                                        "${it.slug} (${it.size_mb}MB)"
                                    } ?: selectedModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !isModelLoaded,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false }
                                ) {
                                    voiceModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text("${model.slug} (${model.size_mb}MB)") },
                                            onClick = {
                                                selectedModel = model.slug
                                                modelExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { downloadAndInitializeModel() },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloading || isInitializing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadPercentage ?: 0f },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            if (downloadProgress.isNotEmpty()) downloadProgress 
                            else if (isDownloading) "Downloading..." 
                            else "Initializing..."
                        )
                    }
                } else {
                    Text(if (isModelLoaded) "Model Ready ✓" else "Download & Initialize Model")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDownloading && downloadPercentage != null -> {
                        LinearProgressIndicator(
                            progress = { downloadPercentage!! },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    isTranscribing || isPreparingFile -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        if (isTranscribing) {
                            stopTranscription()
                        }
                    },
                    enabled = !isDownloading && !isInitializing && isModelLoaded && !isLoadingModels,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTranscribing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Stop")
                        }
                    } else {
                        Text("Microphone")
                    }
                }
                
                Button(
                    onClick = { transcribeFromFile() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("File")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Output:",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(outputText)
                    
                    lastResponse?.let { response ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            "Transcription Result:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                response.text?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                
                                response.totalTimeMs?.let { time ->
                                    Text(
                                        text = "Processing time: ${time.toInt()}ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

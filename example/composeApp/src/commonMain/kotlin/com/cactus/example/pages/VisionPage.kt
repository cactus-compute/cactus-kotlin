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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }

    var availableModels by remember { mutableStateOf<List<CactusModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Select a vision model and pick an image.") }
    var streamingResponse by remember { mutableStateOf("") }
    var lastTPS by remember { mutableStateOf(0.0) }
    var lastTTFT by remember { mutableStateOf(0.0) }
    var selectedImagePath by remember { mutableStateOf<String?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberFilePickerLauncher(
        onFileSelected = { path ->
        if (path != null) {
            selectedImagePath = path
            outputText = "Image selected! Click \"Analyze Image\" to process."
        } else {
            outputText = "No image selected or an error occurred."
        }
        },
        mimeType = "image/*"
    )

    // Fetch available vision models on launch
    LaunchedEffect(Unit) {
        try {
            val models = lm.getModels()
            val visionModels = models.filter { it.supports_vision }
            availableModels = visionModels
            if (visionModels.isNotEmpty() && selectedModel == null) {
                selectedModel = visionModels.first().slug
            }
        } catch (e: Exception) {
            outputText = "Error fetching models: ${e.message}"
        }
    }

    fun downloadModel() {
        if (selectedModel == null) {
            outputText = "Please select a vision model first."
            return
        }

        scope.launch {
            isDownloading = true
            outputText = "Downloading model..."

            try {
                lm.downloadModel(
                    model = selectedModel!!
                )
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
        if (selectedModel == null) {
            outputText = "Please select a vision model first."
            return
        }

        scope.launch {
            isInitializing = true
            outputText = "Initializing model..."

            try {
                lm.initializeModel(CactusInitParams(model = selectedModel))
                if (isActive) {
                    isModelLoaded = true
                    outputText = "Model initialized successfully! Pick an image to analyze."
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

    fun analyzeImage() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }

        if (selectedImagePath == null) {
            outputText = "Please pick an image first."
            return
        }

        scope.launch {
            isGenerating = true
            streamingResponse = ""
            outputText = "Analyzing image..."
            var firstToken = true

            try {
                val resp = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage("You are a helpful AI assistant that can analyze images.", "system"),
                        ChatMessage(
                            content = "Describe this image in detail.",
                            role = "user",
                            images = listOf(selectedImagePath!!)
                        )
                    ),
                    params = CactusCompletionParams(
                        maxTokens = 300
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
                        outputText = "Image analysis completed successfully!"
                    } else {
                        outputText = "Failed to analyze image."
                        lastTPS = 0.0
                        lastTTFT = 0.0
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    outputText = "Error analyzing image: ${e.message}"
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
                title = { Text("Vision Example") },
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
            // Model selection dropdown
            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel?.let { model ->
                        availableModels.find { it.slug == model }?.let {
                            "${it.slug} (${it.size_mb}MB)"
                        } ?: model
                    } ?: "Select a model",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vision Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text("${model.slug} (${model.size_mb}MB)") },
                            onClick = {
                                selectedModel = model.slug
                                // Reset states when model changes
                                isModelDownloaded = false
                                isModelLoaded = false
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // Download and Initialize buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { downloadModel() },
                    enabled = !isDownloading && selectedModel != null,
                    modifier = Modifier.weight(1f)
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
                    enabled = !isInitializing && !isDownloading && selectedModel != null,
                    modifier = Modifier.weight(1f)
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
                        Text(if (isModelLoaded) "Initialized ✓" else "Initialize Model")
                    }
                }
            }

            // Change Image and Analyze buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (selectedImagePath != null) "Change Image" else "Pick Image")
                }

                Button(
                    onClick = { analyzeImage() },
                    enabled = !isDownloading && !isInitializing && !isGenerating && isModelLoaded && selectedImagePath != null,
                    modifier = Modifier.weight(1f)
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
                            Text("Analyzing...")
                        }
                    } else {
                        Text("Analyze Image")
                    }
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
                            "Response:",
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
                                StatItem("Model", selectedModel ?: "")
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

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
fun ContextTestPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }
    
    var isModelDownloaded by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(false) }
    var isRunningTest by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Ready to start. Click \"Download Model\" to begin.") }
    var testResponse by remember { mutableStateOf<String?>(null) }
    var testTPS by remember { mutableStateOf(0.0) }
    var testTTFT by remember { mutableStateOf(0.0) }
    var testTotalTime by remember { mutableStateOf(0.0) }
    var testPrefillTokens by remember { mutableStateOf(0) }
    var testDecodeTokens by remember { mutableStateOf(0) }
    var testTotalTokens by remember { mutableStateOf(0) }
    var model by remember { mutableStateOf("qwen3-0.6") }
    var availableModels by remember { mutableStateOf<List<CactusModel>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val models = lm.getModels()
            if (isActive) {
                availableModels = models
            }
        } catch (e: Exception) {
            println("Error fetching models: ${e.message}")
        }
    }

    fun downloadModel() {
        scope.launch {
            isDownloading = true
            outputText = "Downloading model..."

            try {
                lm.downloadModel(
                    model = model
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
        scope.launch {
            isInitializing = true
            outputText = "Initializing model..."

            try {
                lm.initializeModel(CactusInitParams(model = model))
                if (isActive) {
                    isModelLoaded = true
                    outputText = "Model initialized successfully! Ready to run 4K context test."
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

    fun run4KContextTest() {
        if (!isModelLoaded) {
            outputText = "Please download and initialize model first."
            return
        }

        scope.launch {
            isRunningTest = true
            outputText = "Running 4K context test..."
            testResponse = null
            testTPS = 0.0
            testTTFT = 0.0
            testTotalTime = 0.0
            testPrefillTokens = 0
            testDecodeTokens = 0
            testTotalTokens = 0

            try {
                // Build system message with 230 context items
                val systemContent = buildString {
                    append("/no_think You are helpful. ")
                    repeat(230) { i ->
                        append("Context $i: Background knowledge. ")
                    }
                }

                // Build user message with 230 data items
                val userContent = buildString {
                    repeat(230) { i ->
                        val dataValue = i * 3.14159
                        append("Data $i = $dataValue. ")
                    }
                    append("Explain the data.")
                }

                println("System message length: ${systemContent.length} chars")
                println("User message length: ${userContent.length} chars")

                val resp = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage(systemContent, "system"),
                        ChatMessage(userContent, "user")
                    ),
                    params = CactusCompletionParams(
                        maxTokens = 100
                    )
                )

                if (isActive) {
                    if (resp != null && resp.success) {
                        testResponse = resp.response
                        testTPS = resp.tokensPerSecond ?: 0.0
                        testTTFT = resp.timeToFirstTokenMs ?: 0.0
                        testTotalTime = resp.totalTimeMs ?: 0.0
                        testPrefillTokens = resp.prefillTokens ?: 0
                        testDecodeTokens = resp.decodeTokens ?: 0
                        testTotalTokens = resp.totalTokens ?: 0
                        outputText = "4K context test completed successfully!"
                    } else {
                        outputText = "Test failed to generate response."
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    outputText = "Error running test: ${e.message}"
                }
            } finally {
                if (isActive) {
                    isRunningTest = false
                }
            }
        }
    }

    @Composable
    fun MetricRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label + ":",
                fontWeight = FontWeight.Medium
            )
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
                title = { Text("4K Context Test") },
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
            // Model selector
            if (availableModels.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = availableModels.find { it.slug == model }?.let { 
                            "${it.slug} (${it.size_mb}MB)"
                        } ?: model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableModels.forEach { modelItem ->
                            DropdownMenuItem(
                                text = { Text("${modelItem.slug} (${modelItem.size_mb}MB)") },
                                onClick = {
                                    model = modelItem.slug
                                    expanded = false
                                }
                            )
                        }
                    }
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
                onClick = { run4KContextTest() },
                enabled = !isDownloading && !isInitializing && !isRunningTest && isModelLoaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRunningTest) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Running Test...")
                    }
                } else {
                    Text("Run 4K Context Test")
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
                        "Test Results:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(outputText)
                    
                    testResponse?.let { response ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            "Performance Metrics:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MetricRow("Model", model)
                        MetricRow("Time to First Token", "${testTTFT.toStringAsFixed(2)} ms")
                        MetricRow("Total Time", "${testTotalTime.toStringAsFixed(2)} ms")
                        MetricRow("Tokens Per Second", testTPS.toStringAsFixed(2))
                        MetricRow("Prefill Tokens", "$testPrefillTokens")
                        MetricRow("Decode Tokens", "$testDecodeTokens")
                        MetricRow("Total Tokens", "$testTotalTokens")
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            "Response:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(response)
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

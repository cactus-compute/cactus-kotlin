package com.cactus.example.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cactus.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HybridCompletionPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }
    
    var cactusToken by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("Enter your Cactus token and test cloud-based completion.") }
    var lastResponse by remember { mutableStateOf<String?>(null) }
    var lastTPS by remember { mutableStateOf(0.0) }
    var lastTTFT by remember { mutableStateOf(0.0) }

    fun generateCloudCompletion() {
        if (cactusToken.isBlank()) {
            outputText = "Please enter your Cactus token first."
            return
        }

        scope.launch {
            isGenerating = true
            outputText = "Generating cloud-based response..."

            try {
                val resp = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage("You are Cactus, a helpful AI assistant.", "system"),
                        ChatMessage("Explain quantum computing in simple terms.", "user")
                    ),
                    params = CactusCompletionParams(
                        maxTokens = 200,
                        mode = InferenceMode.REMOTE,
                        cactusToken = cactusToken
                    )
                )

                if (isActive) {
                    if (resp != null && resp.success) {
                        lastResponse = resp.response
                        lastTPS = resp.tokensPerSecond ?: 0.0
                        lastTTFT = resp.timeToFirstTokenMs ?: 0.0
                        outputText = "Cloud completion generated successfully!"
                    } else {
                        outputText = "Failed to generate cloud response. Check your token and connection."
                        lastResponse = null
                        lastTPS = 0.0
                        lastTTFT = 0.0
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    outputText = "Error generating cloud response: ${e.message}"
                    lastResponse = null
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hybrid Completion") },
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
                        "Cloud Fallback Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This example demonstrates cloud-based completion without needing a local model. Useful when you want instant access or fallback functionality.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Token input section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Cactus Token",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = cactusToken,
                        onValueChange = { cactusToken = it },
                        label = { Text("Enter your Cactus token") },
                        placeholder = { Text("cact_...") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "Get your token from the Cactus dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Generate button
            Button(
                onClick = { generateCloudCompletion() },
                enabled = !isGenerating && cactusToken.isNotBlank(),
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
                    Text("Generate Cloud Completion")
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
                            "Cloud Response:",
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
                                text = response,
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
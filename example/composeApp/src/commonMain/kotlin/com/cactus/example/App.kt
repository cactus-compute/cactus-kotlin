package com.cactus.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.cactus.CactusLM

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var lm by remember { mutableStateOf(CactusLM()) }
    
    var logs by remember { mutableStateOf(listOf<String>()) }

    fun addLog(message: String) {
        logs = logs + "${logs.size + 1}. $message"
    }
    
    LaunchedEffect(Unit) {
        addLog("App started - Cactus Modular Demo")
        addLog("Available: Language Model, Vision, Speech-to-Text, Text-to-Speech")
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
                    text = "Cactus Modular Demo",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Language Model Section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Language Model", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Model Operations
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        addLog("Downloading LM model...")
                                        val success = lm.download(
                                            url = "https://ytmrvwsckmqyfpnwfcme.supabase.co/storage/v1/object/sign/cactus-models/qwen3-600m.zip?token=eyJraWQiOiJzdG9yYWdlLXVybC1zaWduaW5nLWtleV9kMjQzNjhmOS02MmEzLTQ2NDQtYjI0Ni01NjdjZWEyYjk2MTIiLCJhbGciOiJIUzI1NiJ9.eyJ1cmwiOiJjYWN0dXMtbW9kZWxzL3F3ZW4zLTYwMG0uemlwIiwiaWF0IjoxNzU2MjcwNzI5LCJleHAiOjE3ODc4MDY3Mjl9.UJoA6ORgZ67FKXneN_ekyU3lTe1fJ4siryFM6uR3pMU",
                                            filename = "qwen3-600m.zip"
                                        )
                                        addLog(if (success) "LM model downloaded" else "LM download failed")
                                    }
                                }
                            ) { Text("Download") }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        addLog("Loading LM model")
                                        val success = lm.init(filename = "qwen3-600m")
                                        addLog(if (success) "LM model loaded" else "LM load failed")
                                    }
                                }
                            ) { Text("Load") }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        addLog("Generating text...")
                                        val result = lm.completion("What is AI?", maxTokens = 50)
                                        addLog("LM: ${result ?: "No response"}")
                                    }
                                }
                            ) { Text("Generate") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Logs:", style = MaterialTheme.typography.titleMedium)
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
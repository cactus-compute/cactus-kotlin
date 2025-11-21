package com.cactus.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.services.CactusTelemetry
import com.cactus.example.theme.AppTheme
import com.cactus.example.pages.BasicCompletionPage
import com.cactus.example.pages.ChatPage
import com.cactus.example.pages.EmbeddingPage
import com.cactus.example.pages.FetchModelsPage
import com.cactus.example.pages.FunctionCallingPage
import com.cactus.example.pages.HybridCompletionPage
import com.cactus.example.pages.StreamingCompletionPage
import com.cactus.example.pages.TranscriptionPage
import com.cactus.example.pages.VisionPage

data class ExampleItem(
    val title: String,
    val description: String,
    val page: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var currentPage by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    
    // Set telemetry token
    LaunchedEffect(Unit) {
        CactusTelemetry.setTelemetryToken("a83c7f7a-43ad-4823-b012-cbeb587ae788")
    }

    val examples = listOf(
        ExampleItem(
            title = "Basic Completion",
            description = "Simple, straightforward text completion"
        ) { BasicCompletionPage { currentPage = null } },
        ExampleItem(
            title = "Streaming Completion",
            description = "Real-time streaming text generation"
        ) { StreamingCompletionPage { currentPage = null } },
        ExampleItem(
            title = "Function Calling",
            description = "Tool/function calling capabilities"
        ) { FunctionCallingPage { currentPage = null } },
        ExampleItem(
            title = "Hybrid Completion",
            description = "Cloud fallback functionality"
        ) { HybridCompletionPage { currentPage = null } },
        ExampleItem(
            title = "Fetch Models",
            description = "Model discovery and management"
        ) { FetchModelsPage { currentPage = null } },
        ExampleItem(
            title = "Embedding",
            description = "Text embedding generation"
        ) { EmbeddingPage { currentPage = null } },
        ExampleItem(
            title = "Transcription",
            description = "Audio transcription capabilities"
        ) { TranscriptionPage { currentPage = null } },
        ExampleItem(
            title = "Vision",
            description = "Image analysis with vision models"
        ) { VisionPage { currentPage = null } },
        ExampleItem(
            title = "Chat",
            description = "Chat with cactus"
        ) { ChatPage { currentPage = null } },
    )

    AppTheme {
        if (currentPage != null) {
            currentPage!!()
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Cactus Examples") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(examples) { example ->
                        Card(
                            onClick = { currentPage = example.page },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = example.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = example.description,
                                    style = MaterialTheme.typography.bodyMedium,
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
package com.cactus.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.cactus.CactusSTT

@Composable
fun SpeechRecordButton(
    stt: CactusSTT,
    modifier: Modifier = Modifier,
    onTextRecognized: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    var isRecording by remember { mutableStateOf(false) }
    var lastRecognizedText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                if (!isRecording) {
                    isRecording = true
                    scope.launch {
                        try {
                            val result = stt.transcribe()
                            isRecording = false
                            
                            if (result != null && result.text.isNotBlank()) {
                                lastRecognizedText = result.text
                                onTextRecognized(result.text)
                            } else {
                                onError("No speech detected")
                            }
                        } catch (e: Exception) {
                            isRecording = false
                            onError("Speech recognition failed: ${e.message}")
                        }
                    }
                } else {
                    stt.stop()
                    isRecording = false
                }
            },
            enabled = stt.isReady(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error 
                               else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                when {
                    !stt.isReady() -> "Setup STT First"
                    isRecording -> "Recording... (Tap to Stop)"
                    else -> "Speech Recognition"
                }
            )
        }
        
        if (lastRecognizedText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recognized:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$lastRecognizedText\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        if (isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Listening...", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
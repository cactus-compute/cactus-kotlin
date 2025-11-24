package com.cactus.example.pages

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cactus.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lm = remember { CactusLM() }

    var chatMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var messageText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Setup CactusLM on first composition
    LaunchedEffect(Unit) {
        try {
            lm.downloadModel()
            lm.initializeModel(params = CactusInitParams(model = "qwen3-0.6", contextSize = 4096))
            // Warm up with system message
            lm.generateCompletion(
                messages = listOf(
                    ChatMessage("You are Cactus, a very capable AI assistant running offline on a smartphone", "system")
                ),
                params = CactusCompletionParams(maxTokens = 0)
            )
            isLoading = false
        } catch (e: Exception) {
            println("Error setting up CactusLM: ${e.message}")
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            lm.unload()
        }
    }

    fun sendMessage() {
        val message = messageText.trim()
        if (message.isEmpty()) return

        scope.launch {
            // Add user message
            chatMessages = chatMessages + Message(content = message, role = MessageRole.User)
            messageText = ""

            // Add typing indicator
            chatMessages = chatMessages + Message(content = "", role = MessageRole.Typing)
            isLoading = true

            // Scroll to bottom
            listState.animateScrollToItem(chatMessages.size - 1)

            try {
                val assistantResponse = StringBuilder()
                val messagesToPass = chatMessages.filter { it.role != MessageRole.Typing }
                    .map { ChatMessage(it.content, it.role.toApiRole()) }

                val result = lm.generateCompletion(
                    messages = messagesToPass,
                    onToken = { token, _ ->
                        // Remove typing indicator and update/add assistant message
                        chatMessages = chatMessages.filter { it.role != MessageRole.Typing }

                        assistantResponse.append(token)

                        val lastMessage = chatMessages.lastOrNull()
                        chatMessages = if (lastMessage?.role == MessageRole.Assistant) {
                             chatMessages.dropLast(1) +
                                lastMessage.copy(content = assistantResponse.toString(), result = null)
                        } else {
                            chatMessages + Message(
                                content = assistantResponse.toString(),
                                role = MessageRole.Assistant,
                                result = null
                            )
                        }

                        // Scroll to bottom
                        scope.launch {
                            if (isActive) {
                                listState.animateScrollToItem(chatMessages.size - 1)
                            }
                        }
                    }
                )

                if (isActive) {
                    // Update the last assistant message with the result
                    val lastMessage = chatMessages.lastOrNull()
                    if (lastMessage?.role == MessageRole.Assistant) {
                        chatMessages = chatMessages.dropLast(1) +
                            lastMessage.copy(result = result)
                    }

                    isLoading = false
                }
            } catch (e: Exception) {
                if (isActive) {
                    // Remove typing indicator
                    chatMessages = chatMessages.filter { it.role != MessageRole.Typing }
                    println("Error generating response: ${e.message}")
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            chatMessages = emptyList()
                        }
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear conversation")
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
        ) {
            // Chat messages
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatMessages.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Start a conversation",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(chatMessages) { message ->
                            when (message.role) {
                                MessageRole.User -> UserMessageBubble(message)
                                MessageRole.Assistant -> AssistantMessageBubble(message)
                                MessageRole.Typing -> TypingIndicator()
                            }
                        }
                    }
                }
            }

            // Input area
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask anything...") },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(24.dp),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (!isLoading && messageText.isNotBlank()) {
                                    sendMessage()
                                }
                            }
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        )
                    )

                    IconButton(
                        onClick = { sendMessage() },
                        enabled = !isLoading && messageText.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(40.dp),
                                tint = if (messageText.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: Message) {
    var showThinking by remember { mutableStateOf(false) }
    val parsedContent = remember(message.content) { parseThinkingContent(message.content) }
    val hasThinking = parsedContent.thinking.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Thinking section (collapsible)
            if (hasThinking) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { showThinking = !showThinking }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Thinking...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                if (showThinking) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showThinking) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showThinking) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = parsedContent.thinking,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Main response
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = parsedContent.response,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 20.sp
                    )

                    if (message.result?.tokensPerSecond != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tokens: ${message.result.totalTokens ?: 0} • TTFT: ${message.result.timeToFirstTokenMs?.toInt() ?: 0} ms • ${message.result.tokensPerSecond ?: 0.0} tok/sec",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200)
                        ),
                        label = "dot_scale_$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp * scale)
                            .background(
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        }
    }
}

// Data classes
data class Message(
    val content: String,
    val role: MessageRole,
    val result: CactusCompletionResult? = null
)

enum class MessageRole {
    User, Assistant, Typing;

    fun toApiRole(): String = when (this) {
        User -> "user"
        Assistant -> "assistant"
        Typing -> "typing"
    }
}

data class ParsedContent(
    val thinking: String,
    val response: String
)

// Helper functions
private fun parseThinkingContent(content: String): ParsedContent {
    // Use (?s) flag to make . match newlines
    val thinkingRegex = Regex("(?s)<think>(.*?)</think>")
    val thinkingMatch = thinkingRegex.find(content)

    return if (thinkingMatch != null) {
        val thinking = thinkingMatch.groups[1]?.value?.trim() ?: ""
        val response = content.replace(thinkingRegex, "").trim()
        ParsedContent(thinking, cleanContent(response))
    } else {
        ParsedContent("", cleanContent(content))
    }
}

private fun cleanContent(content: String): String {
    return content
        .replace(Regex("<\\|im_end\\|>"), "")
        .replace(Regex("</s>"), "")
        .trim()
}
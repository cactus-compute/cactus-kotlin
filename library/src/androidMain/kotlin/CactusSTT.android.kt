package com.cactus

import android.content.Context

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual fun getSpeechRecognitionProvider(provider: TranscriptionProvider): SpeechRecognitionProvider {
    return when (provider) {
        TranscriptionProvider.WHISPER -> WhisperSpeechRecognitionProvider()
    }
}

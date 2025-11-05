package com.cactus

actual fun getSpeechRecognitionProvider(provider: TranscriptionProvider): SpeechRecognitionProvider {
    return when (provider) {
        TranscriptionProvider.WHISPER -> WhisperSpeechRecognitionProvider()
    }
}

package com.cactus.example

import androidx.compose.runtime.Composable

/**
 * Audio recorder interface for recording PCM audio data
 * Records at 16kHz sample rate, mono, 16-bit PCM (Whisper standard format)
 */
interface AudioRecorder {
    /**
     * Start recording audio
     * @param onAudioData Callback invoked with audio chunks as they're recorded
     * @param onError Callback invoked if an error occurs
     */
    fun startRecording(
        onAudioData: (ByteArray) -> Unit,
        onError: (String) -> Unit
    )

    /**
     * Stop recording and return all accumulated audio data
     * @return Complete PCM audio buffer, or null if recording failed
     */
    suspend fun stopRecording(): ByteArray?

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean

    /**
     * Release resources
     */
    fun release()
}

@Composable
expect fun rememberAudioRecorder(): AudioRecorder

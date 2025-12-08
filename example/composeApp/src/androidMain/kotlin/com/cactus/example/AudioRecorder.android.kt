package com.cactus.example

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class AndroidAudioRecorder : AudioRecorder {
    companion object {
        private const val SAMPLE_RATE = 16000 // Whisper standard
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private val audioBuffer = ByteArrayOutputStream()
    private var onAudioDataCallback: ((ByteArray) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    override fun startRecording(
        onAudioData: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRecording) {
            onError("Already recording")
            return
        }

        try {
            onAudioDataCallback = onAudioData
            onErrorCallback = onError

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onError("Failed to get buffer size")
                return
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioBuffer.reset()
            audioRecord?.startRecording()
            isRecording = true

            // Start reading audio data in a coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(minBufferSize)

                while (isRecording && isActive) {
                    try {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                        if (readSize > 0) {
                            val audioChunk = buffer.copyOf(readSize)
                            audioBuffer.write(audioChunk)

                            // Callback with audio chunk for real-time processing
                            withContext(Dispatchers.Main) {
                                onAudioDataCallback?.invoke(audioChunk)
                            }
                        } else if (readSize < 0) {
                            withContext(Dispatchers.Main) {
                                onErrorCallback?.invoke("Error reading audio: $readSize")
                            }
                            break
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onErrorCallback?.invoke("Recording error: ${e.message}")
                        }
                        break
                    }
                }
            }

        } catch (e: SecurityException) {
            onError("Microphone permission not granted")
        } catch (e: Exception) {
            onError("Failed to start recording: ${e.message}")
        }
    }

    override suspend fun stopRecording(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isRecording) {
            return@withContext null
        }

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            val recordedData = audioBuffer.toByteArray()
            audioBuffer.reset()
            return@withContext recordedData
        } catch (e: Exception) {
            onErrorCallback?.invoke("Error stopping recording: ${e.message}")
            return@withContext null
        } finally {
            audioRecord?.release()
            audioRecord = null
        }
    }

    override fun isRecording(): Boolean = isRecording

    override fun release() {
        if (isRecording) {
            isRecording = false
            recordingJob?.cancel()
        }
        audioRecord?.release()
        audioRecord = null
        audioBuffer.reset()
        onAudioDataCallback = null
        onErrorCallback = null
    }
}

@Composable
actual fun rememberAudioRecorder(): AudioRecorder {
    val context = LocalContext.current

    return remember {
        // Check for microphone permission
        val hasPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            // In a real app, you should request the permission here
            // For now, we'll just return the recorder and let it fail with a proper error
            println("Warning: RECORD_AUDIO permission not granted")
        }

        AndroidAudioRecorder()
    }
}

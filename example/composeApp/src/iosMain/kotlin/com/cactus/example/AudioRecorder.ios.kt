@file:OptIn(ExperimentalForeignApi::class)
package com.cactus.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.NSError
import platform.darwin.NSObject

class IOSAudioRecorder : AudioRecorder {
    companion object {
        private const val SAMPLE_RATE = 16000.0 // Whisper standard
    }

    private var audioEngine: AVAudioEngine? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<ByteArray>()
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

            // Configure audio session
            val audioSession = AVAudioSession.sharedInstance()
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()

                audioSession.setCategory(
                    AVAudioSessionCategoryRecord,
                    error = error.ptr
                )

                if (error.value != null) {
                    onError("Failed to set audio session category: ${error.value?.localizedDescription}")
                    return
                }

                audioSession.setActive(true, error = error.ptr)

                if (error.value != null) {
                    onError("Failed to activate audio session: ${error.value?.localizedDescription}")
                    return
                }
            }

            // Create and configure audio engine
            audioEngine = AVAudioEngine()
            val inputNode = audioEngine!!.inputNode

            // Use the input node's native format (required by iOS)
            val inputFormat = inputNode.outputFormatForBus(0u)

            if (inputFormat == null) {
                onError("Failed to get input format")
                return
            }

            audioBuffer.clear()

            // Install tap on input node to capture audio using native format
            inputNode.installTapOnBus(
                bus = 0u,
                bufferSize = 4096u,
                format = inputFormat
            ) { buffer, _ ->
                buffer?.let { audioBuffer ->
                    val pcmBuffer = audioBuffer as? AVAudioPCMBuffer
                    pcmBuffer?.let {
                        val frameLength = it.frameLength.toInt()
                        if (frameLength > 0) {
                            // Get float channel data (iOS records in Float32 by default)
                            val floatData = it.floatChannelData?.get(0)
                            floatData?.let { dataPtr ->
                                // Convert Float32 to Int16 PCM and resample if needed
                                val sourceSampleRate = inputFormat.sampleRate.toInt()
                                val targetSampleCount = if (sourceSampleRate != SAMPLE_RATE.toInt()) {
                                    (frameLength * SAMPLE_RATE / sourceSampleRate).toInt()
                                } else {
                                    frameLength
                                }

                                val byteArray = ByteArray(targetSampleCount * 2) // 2 bytes per sample

                                for (i in 0 until targetSampleCount) {
                                    // Simple resampling: pick nearest sample
                                    val sourceIndex = if (sourceSampleRate != SAMPLE_RATE.toInt()) {
                                        (i * sourceSampleRate / SAMPLE_RATE.toInt()).coerceIn(0, frameLength - 1)
                                    } else {
                                        i
                                    }

                                    // Convert Float32 [-1.0, 1.0] to Int16 [-32768, 32767]
                                    val floatSample = dataPtr[sourceIndex].coerceIn(-1.0f, 1.0f)
                                    val int16Sample = (floatSample * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

                                    // Store as little-endian
                                    byteArray[i * 2] = (int16Sample.toInt() and 0xFF).toByte()
                                    byteArray[i * 2 + 1] = ((int16Sample.toInt() shr 8) and 0xFF).toByte()
                                }

                                this@IOSAudioRecorder.audioBuffer.add(byteArray)

                                // Callback with audio chunk
                                CoroutineScope(Dispatchers.Main).launch {
                                    onAudioDataCallback?.invoke(byteArray)
                                }
                            }
                        }
                    }
                }
            }

            // Start the audio engine
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                audioEngine?.startAndReturnError(error.ptr)

                if (error.value != null) {
                    onError("Failed to start audio engine: ${error.value?.localizedDescription}")
                    return
                }
            }

            isRecording = true

        } catch (e: Exception) {
            onError("Failed to start recording: ${e.message}")
        }
    }

    override suspend fun stopRecording(): ByteArray? = withContext(Dispatchers.Default) {
        if (!isRecording) {
            return@withContext null
        }

        isRecording = false

        try {
            // Stop and clean up
            audioEngine?.inputNode?.removeTapOnBus(0u)
            audioEngine?.stop()

            // Deactivate audio session
            val audioSession = AVAudioSession.sharedInstance()
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setActive(false, error = error.ptr)
            }

            // Combine all audio chunks
            val totalSize = audioBuffer.sumOf { it.size }
            val combinedBuffer = ByteArray(totalSize)
            var offset = 0
            for (chunk in audioBuffer) {
                chunk.copyInto(combinedBuffer, offset)
                offset += chunk.size
            }

            audioBuffer.clear()
            return@withContext combinedBuffer

        } catch (e: Exception) {
            onErrorCallback?.invoke("Error stopping recording: ${e.message}")
            return@withContext null
        } finally {
            audioEngine = null
        }
    }

    override fun isRecording(): Boolean = isRecording

    override fun release() {
        if (isRecording) {
            CoroutineScope(Dispatchers.IO).launch {
                stopRecording()
            }
        }
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        audioEngine = null
        audioBuffer.clear()
        onAudioDataCallback = null
        onErrorCallback = null
    }
}

@Composable
actual fun rememberAudioRecorder(): AudioRecorder {
    return remember {
        IOSAudioRecorder()
    }
}

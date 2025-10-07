package com.cactus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

// External JNI functions that will be loaded from the cactus library
// Note: These are compiled into libcactus.so along with other JNI functions
external fun whisperInitFromFile(modelPath: String): Long
external fun whisperFree(ctx: Long)
external fun whisperFullDefaultParams(strategy: Int): Long
external fun whisperFreeParams(params: Long)
external fun whisperFull(ctx: Long, params: Long, samples: FloatArray, nSamples: Int): Int
external fun whisperFullNSegments(ctx: Long): Int
external fun whisperFullGetSegmentText(ctx: Long, iSegment: Int): String?
external fun whisperFullGetSegmentT0(ctx: Long, iSegment: Int): Long
external fun whisperFullGetSegmentT1(ctx: Long, iSegment: Int): Long

class WhisperSpeechRecognitionProvider : SpeechRecognitionProvider {
    private var whisperContext: Long = 0L
    private var isModelReady = false
    private var isListening = false
    private var audioRecord: AudioRecord? = null
    private var stopCurrentRecognition: (() -> Unit)? = null

    companion object {
        // Sampling strategies
        const val WHISPER_SAMPLING_GREEDY = 0
        const val WHISPER_SAMPLING_BEAM_SEARCH = 1
    }

    override suspend fun initialize(modelFolder: String, spkModelFolder: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val baseDir = File(applicationContext.filesDir, "models")
            val modelDir = File(baseDir, modelFolder)

            println("Initializing Whisper with modelDir: ${modelDir.absolutePath}")

            if (!modelDir.exists() || !modelDir.isDirectory) {
                println("ERROR: Whisper model directory not found at ${modelDir.absolutePath}")
                return@withContext false
            }

            // Look for the model file (typically ggml-*.bin)
            val modelFile = modelDir.listFiles()?.firstOrNull { it.name.endsWith(".bin") }
            if (modelFile == null || !modelFile.exists()) {
                println("ERROR: Whisper model file not found in $modelDir")
                println("Available files: ${modelDir.listFiles()?.joinToString { it.name }}")
                return@withContext false
            }

            val modelPath = modelFile.absolutePath
            println("Loading Whisper model from: $modelPath")

            // Initialize Whisper context
            whisperContext = whisperInitFromFile(modelPath)

            if (whisperContext != 0L) {
                println("Whisper model loaded successfully")
                isModelReady = true
                println("Whisper speech recognition initialized successfully")
            } else {
                println("Failed to load Whisper model")
                isModelReady = false
            }

            isModelReady
        } catch (e: Exception) {
            println("Failed to initialize Whisper speech recognition: $e")
            e.printStackTrace()
            false
        }
    }

    override suspend fun requestPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(value = Manifest.permission.RECORD_AUDIO, conditional = true)
    override suspend fun performRecognition(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? =
        suspendCancellableCoroutine { continuation ->
            if (!isModelReady || whisperContext == 0L) {
                println("Whisper model not ready")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Whisper model not initialized"
                    ))
                }
                return@suspendCancellableCoroutine
            }

            if (filePath != null) {
                performFileBasedRecognition(filePath, params, continuation)
            } else {
                performRealTimeRecognition(params, continuation)
            }
        }

    private fun performFileBasedRecognition(
        filePath: String,
        params: SpeechRecognitionParams,
        continuation: kotlinx.coroutines.CancellableContinuation<SpeechRecognitionResult?>
    ) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Audio file not found: $filePath"
                ))
                return
            }

            val startTime = System.currentTimeMillis()

            // Read WAV file
            val audioData = readWavFile(filePath)
            if (audioData == null) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Failed to read audio file"
                ))
                return
            }

            // Get default parameters
            val paramsPtr = whisperFullDefaultParams(WHISPER_SAMPLING_GREEDY)
            if (paramsPtr == 0L) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Failed to get whisper parameters"
                ))
                return
            }

            try {
                // Process audio
                val result = whisperFull(whisperContext, paramsPtr, audioData, audioData.size)

                if (result != 0) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Whisper processing failed with code: $result"
                    ))
                    return
                }

                // Extract text from segments
                val nSegments = whisperFullNSegments(whisperContext)
                val textBuilder = StringBuilder()

                for (i in 0 until nSegments) {
                    val segmentText = whisperFullGetSegmentText(whisperContext, i)
                    if (segmentText != null) {
                        textBuilder.append(segmentText)
                    }
                }

                val processingTime = (System.currentTimeMillis() - startTime).toDouble()
                val text = textBuilder.toString().trim()

                continuation.resume(SpeechRecognitionResult(
                    success = text.isNotEmpty(),
                    text = text.ifEmpty { null },
                    processingTime = processingTime
                ))
            } finally {
                whisperFreeParams(paramsPtr)
            }
        } catch (e: Exception) {
            println("Error processing audio file: $e")
            e.printStackTrace()
            continuation.resume(SpeechRecognitionResult(
                success = false,
                text = "Error processing audio file: ${e.message}"
            ))
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun performRealTimeRecognition(
        params: SpeechRecognitionParams,
        continuation: CancellableContinuation<SpeechRecognitionResult?>
    ) {
        if (isListening) {
            println("Already listening")
            continuation.resume(null)
            return
        }

        val permissionGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            println("No microphone permission")
            continuation.resume(SpeechRecognitionResult(
                success = false,
                eventSuccess = false,
                text = "Microphone permission not granted"
            ))
            return
        }

        try {
            val sampleRate = params.sampleRate
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    eventSuccess = false,
                    text = "Failed to get audio buffer size"
                ))
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    eventSuccess = false,
                    text = "Failed to initialize audio recording"
                ))
                return
            }

            isListening = true
            val hasResumed = AtomicBoolean(false)
            val accumulatedAudio = mutableListOf<Float>()
            val recordingStartTime = System.currentTimeMillis()
            var silenceStartTime = 0L

            fun stopAndFinalize() {
                if (!hasResumed.getAndSet(true)) {
                    isListening = false
                    audioRecord?.stop()

                    try {
                        if (accumulatedAudio.isEmpty()) {
                            continuation.resume(SpeechRecognitionResult(
                                success = false,
                                eventSuccess = false,
                                text = "No audio data captured"
                            ))
                            return
                        }

                        val startTime = System.currentTimeMillis()
                        val audioArray = accumulatedAudio.toFloatArray()

                        // Get default parameters
                        val paramsPtr = whisperFullDefaultParams(WHISPER_SAMPLING_GREEDY)
                        if (paramsPtr == 0L) {
                            continuation.resume(SpeechRecognitionResult(
                                success = false,
                                text = "Failed to get whisper parameters"
                            ))
                            return
                        }

                        try {
                            // Process audio
                            val result = whisperFull(whisperContext, paramsPtr, audioArray, audioArray.size)

                            if (result != 0) {
                                continuation.resume(SpeechRecognitionResult(
                                    success = false,
                                    text = "Whisper processing failed with code: $result"
                                ))
                                return
                            }

                            // Extract text from segments
                            val nSegments = whisperFullNSegments(whisperContext)
                            val textBuilder = StringBuilder()

                            for (i in 0 until nSegments) {
                                val segmentText = whisperFullGetSegmentText(whisperContext, i)
                                if (segmentText != null) {
                                    textBuilder.append(segmentText)
                                }
                            }

                            val processingTime = (System.currentTimeMillis() - startTime).toDouble()
                            val text = textBuilder.toString().trim()

                            continuation.resume(SpeechRecognitionResult(
                                success = text.isNotEmpty(),
                                text = text.ifEmpty { null },
                                processingTime = processingTime
                            ))
                        } finally {
                            whisperFreeParams(paramsPtr)
                        }
                    } catch (e: Exception) {
                        println("Error during finalization: $e")
                        e.printStackTrace()
                        continuation.resume(SpeechRecognitionResult(
                            success = false,
                            text = "Error processing audio: ${e.message}"
                        ))
                    }
                }
            }

            stopCurrentRecognition = ::stopAndFinalize

            continuation.invokeOnCancellation {
                stopCurrentRecognition?.invoke()
            }

            // Start recording in a background thread
            Thread {
                try {
                    audioRecord?.startRecording()
                    val buffer = ShortArray(bufferSize / 2)

                    while (isListening && !hasResumed.get()) {
                        val currentTime = System.currentTimeMillis()

                        // Check max duration
                        if (currentTime - recordingStartTime > params.maxDuration) {
                            stopCurrentRecognition?.invoke()
                            break
                        }

                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            // Convert PCM16 to float and detect voice activity
                            var audioLevel = 0.0
                            for (i in 0 until read) {
                                val sample = buffer[i] / 32768.0f
                                accumulatedAudio.add(sample)
                                audioLevel += abs(sample.toDouble())
                            }
                            audioLevel /= read
                            val hasVoiceActivity = audioLevel > 0.015

                            // Voice activity detection for silence detection
                            if (hasVoiceActivity) {
                                silenceStartTime = 0L
                            } else {
                                if (accumulatedAudio.isNotEmpty()) {
                                    if (silenceStartTime == 0L) {
                                        silenceStartTime = currentTime
                                    } else if (currentTime - silenceStartTime > params.maxSilenceDuration) {
                                        stopCurrentRecognition?.invoke()
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Error during audio recording: $e")
                    e.printStackTrace()
                    if (hasResumed.compareAndSet(false, true)) {
                        continuation.resume(SpeechRecognitionResult(
                            success = false,
                            eventSuccess = false,
                            text = "Error during audio recording: ${e.message}"
                        ))
                    }
                }
            }.start()

        } catch (e: Exception) {
            println("Failed to start Whisper speech recognition: $e")
            e.printStackTrace()
            stopCurrentRecognition?.invoke()
            continuation.resume(SpeechRecognitionResult(
                success = false,
                eventSuccess = false,
                text = e.message
            ))
        }
    }

    private fun readWavFile(filePath: String): FloatArray? {
        return try {
            val file = RandomAccessFile(filePath, "r")
            file.use {
                // Skip WAV header (44 bytes) - same approach as Vosk
                file.skipBytes(44)
                
                // Read remaining audio data
                val remaining = (file.length() - 44).toInt()
                val audioBytes = ByteArray(remaining)
                file.read(audioBytes)
                
                // Convert to float array (assuming 16-bit PCM)
                val audioBuffer = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
                val numSamples = remaining / 2
                val samples = FloatArray(numSamples)
                
                for (i in 0 until numSamples) {
                    samples[i] = audioBuffer.short / 32768.0f
                }
                
                return samples
            }
        } catch (e: Exception) {
            println("Error reading WAV file: $e")
            e.printStackTrace()
            null
        }
    }

    override fun stop() {
        try {
            stopCurrentRecognition?.invoke()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isListening = false
        } catch (e: Exception) {
            println("Error stopping Whisper audio recording: $e")
        }
    }

    override fun isAvailable(): Boolean = isModelReady

    override fun isAuthorized(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    protected fun finalize() {
        if (whisperContext != 0L) {
            whisperFree(whisperContext)
            whisperContext = 0L
        }
    }
}

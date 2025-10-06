package com.cactus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import org.json.JSONObject
import android.annotation.SuppressLint
import org.vosk.SpeakerModel

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

// Provider implementations
class VoskSpeechRecognitionProvider: SpeechRecognitionProvider {
    private var model: Model? = null
    private var spkModel: SpeakerModel? = null
    private var isModelReady = false
    private var isListening = false
    private var audioRecord: AudioRecord? = null

    override suspend fun initialize(modelFolder: String, spkModelFolder: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)

            val baseDir = File(applicationContext.filesDir, "models")
            val modelDir = File(baseDir, modelFolder)
            val spkModelDir = File(baseDir, spkModelFolder)

            println("Initializing Vosk with modelDir: ${modelDir.absolutePath}, spkModelDir: ${spkModelDir.absolutePath}")

            if (!modelDir.exists() || !modelDir.isDirectory || !spkModelDir.exists() || !spkModelDir.isDirectory) {
                return@withContext false
            }

            if (modelDir.listFiles()?.isNotEmpty() == true) {
                try {
                    model = Model(modelDir.absolutePath)
                    spkModel = SpeakerModel(spkModelDir.absolutePath)
                    isModelReady = true
                } catch (e: Exception) {
                    println("Failed to load Vosk model: $e")
                }
            }

            true
        } catch (e: Exception) {
            println("Error initializing Vosk: $e")
            false
        }
    }

    override suspend fun requestPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun performRecognition(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? =
        suspendCancellableCoroutine { continuation ->
            if (!isModelReady || model == null) {
                println("Model not ready, returning setup message")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Model not initialized"
                    ))
                }
                return@suspendCancellableCoroutine
            }

            if (filePath != null) {
                performFileBasedRecognition(filePath, params, continuation)
            } else {
                if (isListening) {
                    println("Already listening, returning null")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                    return@suspendCancellableCoroutine
                }

                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    println("No microphone permission")
                    if (continuation.isActive) {
                        continuation.resume(SpeechRecognitionResult(
                            success = false,
                            text = "Microphone permission required. Please grant RECORD_AUDIO permission in app settings."
                        ))
                    }
                    return@suspendCancellableCoroutine
                }

                performMicrophoneBasedRecognition(params, continuation)
            }
        }

    override fun stop() {
        try {
            isListening = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun isAvailable(): Boolean {
        return isModelReady && model != null
    }

    override fun isAuthorized(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun parseRecognitionResult(resultJson: String): String? {
        return try {
            val json = JSONObject(resultJson)
            val text = json.optString("text", "")
            if (text.isNotEmpty()) text else null
        } catch (e: Exception) {
            println("Error parsing recognition result: $e")
            null
        }
    }

    private fun createSpeechResult(text: String?, processingTime: Double?, success: Boolean = true): SpeechRecognitionResult? {
        return if (text != null && text.isNotEmpty()) {
            SpeechRecognitionResult(
                success = success,
                text = text,
                processingTime = processingTime
            )
        } else {
            null
        }
    }

    private fun performFileBasedRecognition(
        filePath: String,
        params: SpeechRecognitionParams,
        continuation: CancellableContinuation<SpeechRecognitionResult?>
    ) {
        try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Audio file not found: $filePath"
                    ))
                }
                return
            }

            val recognizer = Recognizer(model, params.sampleRate.toFloat(), spkModel)
            val startTime = System.currentTimeMillis()

            audioFile.inputStream().use { inputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var finalResult = ""

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        parseRecognitionResult(recognizer.result)?.let { text ->
                            finalResult = if (finalResult.isEmpty()) text else "$finalResult $text"
                        }
                    }
                }

                parseRecognitionResult(recognizer.finalResult)?.let { text ->
                    finalResult = if (finalResult.isEmpty()) text else "$finalResult $text"
                }

                val processingTime = (System.currentTimeMillis() - startTime).toDouble()

                val result = if (finalResult.isNotEmpty()) {
                    SpeechRecognitionResult(
                        success = true,
                        text = finalResult.trim(),
                        processingTime = processingTime
                    )
                } else {
                    SpeechRecognitionResult(
                        success = false,
                        text = "No speech detected in audio file"
                    )
                }

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

        } catch (e: Exception) {
            println("Failed to recognize speech from file: $e")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Error processing audio file: ${e.message}"
                ))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun performMicrophoneBasedRecognition(
        params: SpeechRecognitionParams,
        continuation: CancellableContinuation<SpeechRecognitionResult?>
    ) {
        try {
            isListening = true

            val recognizer = Recognizer(model, params.sampleRate.toFloat(), spkModel)

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(
                params.sampleRate,
                channelConfig,
                audioFormat
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                params.sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                isListening = false
                if (continuation.isActive) {
                    continuation.resume(null)
                }
                return
            }

            audioRecord?.startRecording()
            val audioBuffer = ShortArray(bufferSize / 2)
            var isRecording = true

            if (continuation.isActive) {
                continuation.invokeOnCancellation {
                    isRecording = false
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    isListening = false
                }
            }

            Thread {
                try {
                    var finalResult: String? = null
                    var lastPartialResult = ""
                    var silenceStartTime = 0L
                    val recordingStartTime = System.currentTimeMillis()

                    while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - recordingStartTime > params.maxDuration) {
                            isRecording = false
                            break
                        }

                        val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

                        if (bytesRead > 0) {
                            val byteBuffer = ByteArray(bytesRead * 2)
                            for (i in 0 until bytesRead) {
                                val sample = audioBuffer[i]
                                byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                            }

                            var audioLevel = 0.0
                            for (i in audioBuffer.indices) {
                                audioLevel += if (audioBuffer[i] < 0) -audioBuffer[i].toDouble() else audioBuffer[i].toDouble()
                            }
                            audioLevel /= audioBuffer.size
                            val hasVoiceActivity = audioLevel > 500.0

                            if (recognizer.acceptWaveForm(byteBuffer, bytesRead * 2)) {
                                try {
                                    val partialJson = JSONObject(recognizer.partialResult)
                                    val partialText = partialJson.optString("partial", "")
                                    if (partialText.isNotEmpty() && partialText != lastPartialResult) {
                                        lastPartialResult = partialText
                                        silenceStartTime = 0L
                                    }
                                } catch (e: Exception) { /* Ignore */ }

                                parseRecognitionResult(recognizer.result)?.let { text ->
                                    finalResult = text
                                    silenceStartTime = 0L
                                }
                            }

                            if (hasVoiceActivity) {
                                silenceStartTime = 0L
                            } else {
                                if (finalResult != null || lastPartialResult.isNotEmpty()) {
                                    if (silenceStartTime == 0L) {
                                        silenceStartTime = currentTime
                                    } else if (currentTime - silenceStartTime > params.maxSilenceDuration) {
                                        isRecording = false
                                        break
                                    }
                                }
                            }
                        }

                        Thread.sleep(10)
                    }

                    if (finalResult == null) {
                        finalResult = parseRecognitionResult(recognizer.finalResult)
                            ?: lastPartialResult.takeIf { it.isNotEmpty() }
                    }

                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    isListening = false

                    val processingTime = (System.currentTimeMillis() - recordingStartTime).toDouble()
                    val result = createSpeechResult(finalResult, processingTime)

                    if (continuation.isActive) {
                        continuation.resume(result)
                    }

                } catch (e: Exception) {
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    isListening = false
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }.start()

        } catch (e: Exception) {
            println("Failed to start Vosk speech recognition: $e")
            isListening = false
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = e.message
                ))
            }
        }
    }
}
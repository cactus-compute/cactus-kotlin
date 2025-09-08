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
private var model: Model? = null
private var spkModel: SpeakerModel? = null
private var isModelReady = false
private var isListening = false
private var audioRecord: AudioRecord? = null

actual suspend fun initializeSpeechRecognition(modelFolder: String, spkModelFolder: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        LibVosk.setLogLevel(LogLevel.WARNINGS)

        val baseDir = File(applicationContext.filesDir, "models/vosk")
        val modelDir = File(baseDir, modelFolder)
        val spkModelDir = File(baseDir, spkModelFolder)

        print("Initializing speech recognition with modelDir: ${modelDir.absolutePath}, spkModelDir: ${spkModelDir.absolutePath}")

        if (!modelDir.exists() || !modelDir.isDirectory || !spkModelDir.exists() || !spkModelDir.isDirectory) {
            return@withContext false
        }

        if (modelDir.listFiles()?.isNotEmpty() == true) {
            try {
                model = Model(modelDir.absolutePath)
                spkModel = SpeakerModel(spkModelDir.absolutePath)
                isModelReady = true
            } catch (e: Exception) {
                // Model loading failed
            }
        }

        true
    } catch (e: Exception) {
        false
    }
}

actual suspend fun requestSpeechPermissions(): Boolean {
    return ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

actual suspend fun performSpeechRecognition(params: SpeechRecognitionParams): SpeechRecognitionResult? =
    suspendCancellableCoroutine { continuation ->

        if (!isModelReady || model == null) {
            println("Model not ready, returning setup message")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Setting up offline speech recognition...",
                    confidence = 0.0f,
                    isPartial = false,
                    alternatives = emptyList()
                ))
            }
            return@suspendCancellableCoroutine
        }

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
                    text = "Microphone permission required. Please grant RECORD_AUDIO permission in app settings.",
                    confidence = 0.0f,
                    isPartial = false,
                    alternatives = emptyList()
                ))
            }
            return@suspendCancellableCoroutine
        }

        try {
            isListening = true
            val recognizer = Recognizer(model, params.sampleRate.toFloat(), spkModel)

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(params.sampleRate, channelConfig, audioFormat)

            @SuppressLint("MissingPermission")
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
                return@suspendCancellableCoroutine
            }

            audioRecord?.startRecording()

            val audioBuffer = ShortArray(bufferSize / 2)
            var finalResult: String? = null
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
                    var lastSpeechTime = System.currentTimeMillis()
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
                                val partialResult = recognizer.partialResult
                                try {
                                    val partialJson = JSONObject(partialResult)
                                    val partialText = partialJson.optString("partial", "")

                                    if (partialText.isNotEmpty() && partialText != lastPartialResult) {
                                        lastPartialResult = partialText
                                        lastSpeechTime = currentTime
                                        silenceStartTime = 0L
                                    }
                                } catch (e: Exception) {
                                }

                                val result = recognizer.result
                                try {
                                    val json = JSONObject(result)
                                    val text = json.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        finalResult = text
                                        lastSpeechTime = currentTime
                                        silenceStartTime = 0L
                                    }
                                } catch (e: Exception) {
                                }
                            }

                            if (hasVoiceActivity) {
                                lastSpeechTime = currentTime
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
                        val finalJson = recognizer.finalResult
                        try {
                            val json = JSONObject(finalJson)
                            finalResult = json.optString("text", "")
                        } catch (e: Exception) {
                            finalResult = lastPartialResult.takeIf { it.isNotEmpty() }
                        }
                    }

                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    isListening = false

                    val endTime = System.currentTimeMillis()
                    val responseTimeMs = (endTime - recordingStartTime).toDouble()

                    val result = if (!finalResult.isNullOrEmpty()) {
                        SpeechRecognitionResult(
                            success = true,
                            text = finalResult,
                            confidence = 0.9f,
                            isPartial = false,
                            alternatives = emptyList(),
                            responseTime = responseTimeMs
                        )
                    } else {
                        null
                    }

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
                continuation.resume(null)
            }
        }
    }

actual fun stopSpeechRecognition() {
    try {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    } catch (e: Exception) {
        // Ignore
    }
}

actual fun isSpeechRecognitionAvailable(): Boolean {
    return isModelReady && model != null
}

actual fun isSpeechRecognitionAuthorized(): Boolean {
    return ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

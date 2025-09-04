package com.cactus

import cnames.structs.VoskModel
import cnames.structs.VoskRecognizer
import cnames.structs.VoskSpkModel
import com.vosk.native.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.AVFAudio.*
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSinceReferenceDate
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private var model: CValuesRef<VoskModel>? = null
@OptIn(ExperimentalForeignApi::class)
private var spkModel: CValuesRef<VoskSpkModel>? = null
@OptIn(ExperimentalForeignApi::class)
private var voskRecognizer: CValuesRef<VoskRecognizer>? = null
private var audioEngine: AVAudioEngine? = null
private var isModelReady = false
private var isListening = false

private var stopCurrentRecognition: (() -> Unit)? = null

@OptIn(ExperimentalForeignApi::class)
actual suspend fun initializeSpeechRecognition(modelFolder: String, spkModelFolder: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        vosk_set_log_level(-1) // -1 for warnings, to match Android's LogLevel.WARNINGS

        val cachesDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return@withContext false

        val baseDir = "$cachesDir/models/vosk"

        val modelDir = "$baseDir/$modelFolder"
        val spkModelDir = "$baseDir/$spkModelFolder"

        println("Initializing speech recognition with modelDir: $modelDir, spkModelDir: $spkModelDir")

        val fileManager = NSFileManager.defaultManager
        val modelExists = fileManager.fileExistsAtPath(modelDir)
        val spkModelExists = fileManager.fileExistsAtPath(spkModelDir)

        if (!modelExists || !spkModelExists) {
            println("Model exists: $modelExists, Speaker model exists: $spkModelExists")
            if (!modelExists) println("ERROR: Model directory not found at $modelDir")
            if (!spkModelExists) println("ERROR: Speaker model directory not found at $spkModelDir")
            return@withContext false
        }

        model = vosk_model_new(modelDir)
        spkModel = vosk_spk_model_new(spkModelDir)

        if (model != null) {
            println("Vosk model loaded successfully")
            audioEngine = AVAudioEngine()
            val audioSession = AVAudioSession.sharedInstance()
            try {
                audioSession.setCategory(AVAudioSessionCategoryRecord, null)
                audioSession.setActive(true, null)
                isModelReady = true
                println("Vosk speech recognition initialized successfully")
            } catch (e: Exception) {
                println("Failed to set up audio session: $e")
            }
        } else {
            println("Failed to load Vosk model")
        }

        isModelReady
    } catch (e: Exception) {
        println("Failed to initialize Vosk speech recognition: $e")
        false
    }
}

actual suspend fun requestSpeechPermissions(): Boolean = suspendCancellableCoroutine { continuation ->
    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
        continuation.resume(granted)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun performSpeechRecognition(params: SpeechRecognitionParams, sampleRate: Int): SpeechRecognitionResult? =
    suspendCancellableCoroutine { continuation ->

        if (!isModelReady || model == null) {
            println("Model not ready, returning setup message")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    text = "Setting up offline speech recognition...",
                    confidence = 0.5f,
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

        if (AVAudioSession.sharedInstance().recordPermission() != AVAudioSessionRecordPermissionGranted) {
            println("No microphone permission")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
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
            voskRecognizer = vosk_recognizer_new_spk(model, sampleRate.toFloat(), spkModel)

            if (voskRecognizer == null) {
                isListening = false
                if (continuation.isActive) {
                    continuation.resume(null)
                }
                return@suspendCancellableCoroutine
            }

            val inputNode = audioEngine!!.inputNode
            val recordingFormat = inputNode.outputFormatForBus(0u)

            var finalResultText: String? = null
            var lastPartialResult = ""
            var silenceStartTime = 0L
            var lastSpeechTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()
            val maxSilenceDuration = 1000L
            val maxRecordingDuration = 30000L
            val recordingStartTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()

            val hasResumed = atomic(false)

            fun stopAndFinalize() {
                if (hasResumed.getAndSet(true)) return
                stopCurrentRecognition = null

                println("Stopping Vosk speech recognition...")
                audioEngine?.stop()
                audioEngine?.inputNode?.removeTapOnBus(0u)

                var resultText = finalResultText
                if (resultText.isNullOrEmpty()) {
                    voskRecognizer?.let { recognizer ->
                        val finalJson = vosk_recognizer_final_result(recognizer)?.toKString()
                        if (finalJson != null) {
                            try {
                                val json = Json.parseToJsonElement(finalJson).jsonObject
                                resultText = json["text"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                            } catch (e: Exception) {
                                println("Error parsing final Vosk result JSON: $e")
                            }
                        }
                    }
                }
                if (resultText.isNullOrEmpty()) {
                    resultText = lastPartialResult.takeIf { it.isNotEmpty() }
                }

                voskRecognizer?.let {
                    vosk_recognizer_free(it)
                    voskRecognizer = null
                }
                isListening = false

                val result = if (!resultText.isNullOrEmpty()) {
                    SpeechRecognitionResult(
                        text = resultText!!,
                        confidence = 0.9f,
                        isPartial = false,
                        alternatives = emptyList()
                    )
                } else {
                    null
                }
                println("Vosk result: $result")
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            stopCurrentRecognition = ::stopAndFinalize

            continuation.invokeOnCancellation {
                stopCurrentRecognition?.invoke()
            }

            inputNode.installTapOnBus(
                bus = 0u,
                bufferSize = 4096u,
                format = recordingFormat
            ) { buffer, _ ->
                if (!isListening || hasResumed.value) return@installTapOnBus

                val currentTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()

                if (currentTime - recordingStartTime > maxRecordingDuration) {
                    stopCurrentRecognition?.invoke()
                    return@installTapOnBus
                }

                buffer?.let { audioBuffer ->
                    val frameLength = audioBuffer.frameLength.toInt()
                    if (frameLength == 0) return@let

                    val floatChannelData = audioBuffer.floatChannelData?.get(0) ?: return@let

                    var audioLevel = 0.0
                    for (i in 0 until frameLength) {
                        audioLevel += if (floatChannelData[i] < 0) -floatChannelData[i].toDouble() else floatChannelData[i].toDouble()
                    }
                    audioLevel /= frameLength
                    val hasVoiceActivity = audioLevel > 0.015
                    val downsampleRatio = (recordingFormat.sampleRate / sampleRate).toInt()
                    val outputSamples = frameLength / downsampleRatio
                    if (outputSamples == 0) return@let
                    val pcmBytes = ByteArray(outputSamples * 2)

                    for (i in 0 until outputSamples) {
                        val floatSample = floatChannelData[i * downsampleRatio]
                        val int16Sample = (floatSample * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                        pcmBytes[i * 2] = (int16Sample.toInt() and 0xFF).toByte()
                        pcmBytes[i * 2 + 1] = ((int16Sample.toInt() shr 8) and 0xFF).toByte()
                    }

                    pcmBytes.usePinned { pinned ->
                        val shortPtr = pinned.addressOf(0).reinterpret<ShortVar>()
                        val sampleCount = pcmBytes.size / 2
                        if (vosk_recognizer_accept_waveform_s(voskRecognizer, shortPtr, sampleCount) == 1) {
                            val resultJson = vosk_recognizer_result(voskRecognizer)?.toKString()
                            if (resultJson != null) {
                                try {
                                    val json = Json.parseToJsonElement(resultJson).jsonObject
                                    val text = json["text"]?.jsonPrimitive?.content ?: ""
                                    if (text.isNotEmpty()) {
                                        finalResultText = text
                                        lastSpeechTime = currentTime
                                        silenceStartTime = 0L
                                    }
                                } catch (e: Exception) { /* Ignore */ }
                            }
                        } else {
                            val partialResult = vosk_recognizer_partial_result(voskRecognizer)?.toKString()
                            if (partialResult != null) {
                                try {
                                    val json = Json.parseToJsonElement(partialResult).jsonObject
                                    val partialText = json["partial"]?.jsonPrimitive?.content ?: ""
                                    if (partialText.isNotEmpty() && partialText != lastPartialResult) {
                                        lastPartialResult = partialText
                                        lastSpeechTime = currentTime
                                        silenceStartTime = 0L
                                    }
                                } catch (e: Exception) { /* Ignore */ }
                            }
                        }
                    }

                    if (hasVoiceActivity) {
                        lastSpeechTime = currentTime
                        silenceStartTime = 0L
                    } else {
                        if (finalResultText != null || lastPartialResult.isNotEmpty()) {
                            if (silenceStartTime == 0L) {
                                silenceStartTime = currentTime
                            } else if (currentTime - silenceStartTime > maxSilenceDuration) {
                                stopCurrentRecognition?.invoke()
                            }
                        }
                    }
                }
            }

            audioEngine!!.prepare()
            audioEngine!!.startAndReturnError(null)
            println("🎙️ Started Vosk listening...")

        } catch (e: Exception) {
            println("Failed to start Vosk speech recognition: $e")
            stopCurrentRecognition?.invoke()
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

@OptIn(ExperimentalForeignApi::class)
actual fun stopSpeechRecognition() {
    stopCurrentRecognition?.invoke()
}

@OptIn(ExperimentalForeignApi::class)
actual fun isSpeechRecognitionAvailable(): Boolean {
    return isModelReady && model != null
}

actual fun isSpeechRecognitionAuthorized(): Boolean {
    return AVAudioSession.sharedInstance().recordPermission() == AVAudioSessionRecordPermissionGranted
}

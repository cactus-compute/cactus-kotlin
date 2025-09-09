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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.AVFAudio.*
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSinceReferenceDate
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
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
actual suspend fun performSpeechRecognition(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? =
    suspendCancellableCoroutine { continuation ->

        // Check if model is ready
        if (!isModelReady || model == null) {
            println("Model not ready, returning setup message")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Setting up offline speech recognition..."
                ))
            }
            return@suspendCancellableCoroutine
        }

        // Route to appropriate recognition method
        if (filePath != null) {
            performFileBasedRecognition(filePath, params, continuation)
        } else {
            // Check microphone prerequisites
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
                        success = false,
                        text = "Microphone permission required. Please grant RECORD_AUDIO permission in app settings."
                    ))
                }
                return@suspendCancellableCoroutine
            }

            performMicrophoneBasedRecognition(params, continuation)
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

@OptIn(ExperimentalForeignApi::class)
private fun parseRecognitionResult(resultJson: String?): String? {
    return if (resultJson != null) {
        try {
            val json = Json.parseToJsonElement(resultJson).jsonObject
            val text = json["text"]?.jsonPrimitive?.content ?: ""
            if (text.isNotEmpty()) text else null
        } catch (e: Exception) {
            println("Error parsing recognition result: $e")
            null
        }
    } else null
}

@OptIn(ExperimentalForeignApi::class)
private fun parsePartialResult(resultJson: String?): String? {
    return if (resultJson != null) {
        try {
            val json = Json.parseToJsonElement(resultJson).jsonObject
            val text = json["partial"]?.jsonPrimitive?.content ?: ""
            if (text.isNotEmpty()) text else null
        } catch (e: Exception) {
            println("Error parsing partial result: $e")
            null
        }
    } else null
}

@OptIn(ExperimentalForeignApi::class)
private fun performFileBasedRecognition(
    filePath: String,
    params: SpeechRecognitionParams,
    continuation: CancellableContinuation<SpeechRecognitionResult?>
) {
    try {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(filePath)) {
            println("Audio file does not exist: $filePath")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Audio file not found: $filePath"
                ))
            }
            return
        }

        val audioData = NSData.dataWithContentsOfFile(filePath)
        if (audioData == null) {
            println("Failed to read audio file: $filePath")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Failed to read audio file"
                ))
            }
            return
        }

        val recognizer = vosk_recognizer_new_spk(model, params.sampleRate.toFloat(), spkModel)
        if (recognizer == null) {
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Failed to create recognizer"
                ))
            }
            return
        }

        val startTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()
        var finalResult = ""

        // Process audio data in chunks
        val dataLength = audioData.length.toInt()
        val chunkSize = 4000
        var offset = 0

        while (offset < dataLength) {
            val remainingBytes = dataLength - offset
            val currentChunkSize = kotlin.math.min(chunkSize, remainingBytes)

            audioData.bytes?.let { bytes ->
                val chunkPtr = bytes.reinterpret<ByteVar>().plus(offset.toLong())
                if (vosk_recognizer_accept_waveform_s(recognizer, chunkPtr?.reinterpret<ShortVar>(), currentChunkSize / 2) == 1) {
                    parseRecognitionResult(vosk_recognizer_result(recognizer)?.toKString())?.let { text ->
                        finalResult = if (finalResult.isEmpty()) text else "$finalResult $text"
                    }
                }
            }

            offset += currentChunkSize
        }

        // Get final result
        parseRecognitionResult(vosk_recognizer_final_result(recognizer)?.toKString())?.let { text ->
            finalResult = if (finalResult.isEmpty()) text else "$finalResult $text"
        }

        vosk_recognizer_free(recognizer)

        val endTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()
        val processingTime = (endTime - startTime).toDouble()

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

@OptIn(ExperimentalForeignApi::class)
private fun performMicrophoneBasedRecognition(
    params: SpeechRecognitionParams,
    continuation: CancellableContinuation<SpeechRecognitionResult?>
) {
    try {
        isListening = true
        voskRecognizer = vosk_recognizer_new_spk(model, params.sampleRate.toFloat(), spkModel)

        if (voskRecognizer == null) {
            isListening = false
            if (continuation.isActive) {
                continuation.resume(null)
            }
            return
        }

        val inputNode = audioEngine!!.inputNode
        val recordingFormat = inputNode.outputFormatForBus(0u)

        var finalResultText: String? = null
        var lastPartialResult = ""
        var silenceStartTime = 0L
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
                    resultText = parseRecognitionResult(vosk_recognizer_final_result(recognizer)?.toKString())
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

            val endTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()
            val responseTimeMs = (endTime - recordingStartTime).toDouble()

            val result = if (!resultText.isNullOrEmpty()) {
                SpeechRecognitionResult(
                    success = true,
                    text = resultText,
                    processingTime = responseTimeMs
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

            if (currentTime - recordingStartTime > params.maxDuration) {
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
                val downsampleRatio = (recordingFormat.sampleRate / params.sampleRate).toInt()
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
                        parseRecognitionResult(vosk_recognizer_result(voskRecognizer)?.toKString())?.let { text ->
                            finalResultText = text
                            silenceStartTime = 0L
                        }
                    } else {
                        parsePartialResult(vosk_recognizer_partial_result(voskRecognizer)?.toKString())?.let { partialText ->
                            if (partialText != lastPartialResult) {
                                lastPartialResult = partialText
                                silenceStartTime = 0L
                            }
                        }
                    }
                }

                if (hasVoiceActivity) {
                    silenceStartTime = 0L
                } else {
                    if (finalResultText != null || lastPartialResult.isNotEmpty()) {
                        if (silenceStartTime == 0L) {
                            silenceStartTime = currentTime
                        } else if (currentTime - silenceStartTime > params.maxSilenceDuration) {
                            stopCurrentRecognition?.invoke()
                        }
                    }
                }
            }
        }

        audioEngine!!.prepare()
        audioEngine!!.startAndReturnError(null)

    } catch (e: Exception) {
        println("Failed to start Vosk speech recognition: $e")
        stopCurrentRecognition?.invoke()
        if (continuation.isActive) {
            continuation.resume(SpeechRecognitionResult(
                success = false,
                text = e.message
            ))
        }
    }
}
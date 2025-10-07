package com.cactus

import cnames.structs.whisper_context
import com.whisper.native.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellableContinuation
import platform.AVFAudio.*
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.timeIntervalSinceReferenceDate
import platform.Foundation.NSURL
import utils.IOSFileUtils
import kotlin.coroutines.resume

// Whisper provider implementation for iOS
class WhisperSpeechRecognitionProvider : SpeechRecognitionProvider {
    @OptIn(ExperimentalForeignApi::class)
    private var whisperContext: CPointer<whisper_context>? = null
    private var audioEngine: AVAudioEngine? = null
    private var isModelReady = false
    private var isListening = false
    private var stopCurrentRecognition: (() -> Unit)? = null

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun initialize(modelFolder: String, spkModelFolder: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val baseDir = IOSFileUtils.getModelsDirectory() ?: return@withContext false
            val modelDir = "$baseDir/$modelFolder"

            println("Initializing Whisper with modelDir: $modelDir")

            val fileManager = NSFileManager.defaultManager
            val modelExists = fileManager.fileExistsAtPath(modelDir)

            if (!modelExists) {
                println("ERROR: Whisper model directory not found at $modelDir")
                return@withContext false
            }

            // Look for the model file - should be named {modelFolder}.bin
            val modelPath = "$modelDir/$modelFolder.bin"
            if (!fileManager.fileExistsAtPath(modelPath)) {
                println("ERROR: Whisper model file not found at $modelPath")
                // Try to find any .bin file in the directory
                val files = fileManager.contentsOfDirectoryAtPath(modelDir, null)
                println("Available files in model directory: $files")
                return@withContext false
            }

            // Initialize Whisper context
            whisperContext = whisper_init_from_file(modelPath)

            if (whisperContext != null) {
                println("Whisper model loaded successfully")
                audioEngine = AVAudioEngine()
                val audioSession = AVAudioSession.sharedInstance()
                try {
                    audioSession.setCategory(AVAudioSessionCategoryRecord, null)
                    audioSession.setActive(true, null)
                    isModelReady = true
                    println("Whisper speech recognition initialized successfully")
                } catch (e: Exception) {
                    println("Failed to set up audio session: $e")
                }
            } else {
                println("Failed to load Whisper model")
            }

            isModelReady
        } catch (e: Exception) {
            println("Failed to initialize Whisper speech recognition: $e")
            false
        }
    }

    override suspend fun requestPermissions(): Boolean = suspendCancellableCoroutine { continuation ->
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            continuation.resume(granted)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun performRecognition(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? =
        suspendCancellableCoroutine { continuation ->
            if (!isModelReady || whisperContext == null) {
                println("Whisper model not ready, returning setup message")
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
                if (isListening) {
                    println("Already listening, returning null")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                    return@suspendCancellableCoroutine
                }

                val permissionGranted = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
                if (!permissionGranted) {
                    println("No microphone permission")
                    if (continuation.isActive) {
                        continuation.resume(SpeechRecognitionResult(
                            success = false,
                            text = "Microphone permission required."
                        ))
                    }
                    return@suspendCancellableCoroutine
                }

                performMicrophoneBasedRecognition(params, continuation)
            }
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun performFileBasedRecognition(
        filePath: String,
        params: SpeechRecognitionParams,
        continuation: CancellableContinuation<SpeechRecognitionResult?>
    ) {
        val startTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()

        try {
            // Check if file exists
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

            // Use AVAudioFile to properly read audio files (including WAV)
            val fileUrl = NSURL.fileURLWithPath(filePath)
            val audioFile = try {
                AVAudioFile(fileUrl, null)
            } catch (e: Exception) {
                println("Failed to open audio file: $filePath, error: $e")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Failed to open audio file: ${e.message}"
                    ))
                }
                return
            }

            // Get the audio format
            val fileFormat = audioFile.processingFormat
            val sampleRate = fileFormat.sampleRate
            val channelCount = fileFormat.channelCount.toInt()
            
            println("Audio file format: sampleRate=$sampleRate, channels=$channelCount")

            // Read all frames from the file
            val frameCount = audioFile.length.toInt()
            if (frameCount == 0) {
                println("Audio file is empty")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Audio file is empty"
                    ))
                }
                return
            }

            // Create buffer to hold audio data
            val buffer = AVAudioPCMBuffer(fileFormat, frameCount.toUInt())
            if (buffer == null) {
                println("Failed to create audio buffer")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Failed to create audio buffer"
                    ))
                }
                return
            }

            // Read audio file into buffer
            try {
                audioFile.readIntoBuffer(buffer, null)
            } catch (e: Exception) {
                println("Failed to read audio data: $e")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Failed to read audio data: ${e.message}"
                    ))
                }
                return
            }

            val actualFrameCount = buffer.frameLength.toInt()
            if (actualFrameCount == 0) {
                println("No audio data read from file")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "No audio data read from file"
                    ))
                }
                return
            }

            // Get float channel data (mono or take first channel if stereo)
            val floatChannelData = buffer.floatChannelData?.get(0)
            if (floatChannelData == null) {
                println("Failed to get audio channel data")
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Failed to get audio channel data"
                    ))
                }
                return
            }

            // Resample if needed (Whisper expects 16kHz)
            val targetSampleRate = params.sampleRate.toDouble()
            val floatSamples = if (sampleRate != targetSampleRate) {
                val resampleRatio = sampleRate / targetSampleRate
                val outputSampleCount = (actualFrameCount / resampleRatio).toInt()
                FloatArray(outputSampleCount) { i ->
                    val sourceIndex = (i * resampleRatio).toInt()
                    floatChannelData[sourceIndex]
                }
            } else {
                FloatArray(actualFrameCount) { i ->
                    floatChannelData[i]
                }
            }
            
            val sampleCount = floatSamples.size
            println("Processed $sampleCount samples for Whisper")

            // Setup Whisper parameters and process audio
            memScoped {
                val paramsPtr = whisper_full_default_params_by_ref(whisper_sampling_strategy.WHISPER_SAMPLING_GREEDY)
                
                if (paramsPtr == null) {
                    println("Failed to get whisper parameters")
                    if (continuation.isActive) {
                        continuation.resume(SpeechRecognitionResult(
                            success = false,
                            text = "Failed to get whisper parameters"
                        ))
                    }
                    return@memScoped
                }

                try {
                    // Process audio with Whisper
                    floatSamples.usePinned { pinnedSamples ->
                        val result = whisper_full(
                            whisperContext,
                            paramsPtr.pointed.readValue(),
                            pinnedSamples.addressOf(0),
                            sampleCount
                        )

                        if (result != 0) {
                            println("Whisper processing failed with code: $result")
                            if (continuation.isActive) {
                                continuation.resume(SpeechRecognitionResult(
                                    success = false,
                                    text = "Whisper processing failed with code: $result"
                                ))
                            }
                            return@usePinned
                        }

                        // Get transcription results
                        val nSegments = whisper_full_n_segments(whisperContext)
                        val transcriptionBuilder = StringBuilder()

                        for (i in 0 until nSegments) {
                            val text = whisper_full_get_segment_text(whisperContext, i)?.toKString()
                            if (text != null) {
                                transcriptionBuilder.append(text)
                            }
                        }

                        val transcription = transcriptionBuilder.toString().trim()
                        val processingTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong() - startTime

                        if (continuation.isActive) {
                            continuation.resume(SpeechRecognitionResult(
                                success = transcription.isNotEmpty(),
                                text = transcription.ifEmpty { null },
                                processingTime = processingTime.toDouble()
                            ))
                        }
                    }
                } finally {
                    whisper_free_params(paramsPtr)
                }
            }

        } catch (e: Exception) {
            println("Error during Whisper file-based recognition: $e")
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    text = "Error during recognition: ${e.message}"
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
            val recordingStartTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong()

            val audioEngine = this.audioEngine ?: run {
                println("Audio engine not initialized")
                isListening = false
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Audio engine not initialized"
                    ))
                }
                return
            }

            val inputNode = audioEngine.inputNode
            val recordingFormat = inputNode.outputFormatForBus(0u)
            
            if (recordingFormat.sampleRate == 0.0) {
                println("Invalid recording format")
                isListening = false
                if (continuation.isActive) {
                    continuation.resume(SpeechRecognitionResult(
                        success = false,
                        text = "Invalid audio format"
                    ))
                }
                return
            }

            // Buffer to accumulate audio samples
            val accumulatedAudio = mutableListOf<Float>()
            var silenceStartTime = 0L
            val hasResumed = atomic(false)

            fun stopAndFinalize() {
                if (hasResumed.compareAndSet(expect = false, update = true)) {
                    isListening = false
                    try {
                        audioEngine.stop()
                        inputNode.removeTapOnBus(0u)
                    } catch (e: Exception) {
                        println("Error stopping audio engine: $e")
                    }

                    // Process accumulated audio
                    val processingTime = (NSDate.timeIntervalSinceReferenceDate * 1000).toLong() - recordingStartTime

                    if (accumulatedAudio.isEmpty()) {
                        if (continuation.isActive) {
                            continuation.resume(SpeechRecognitionResult(
                                success = false,
                                text = "No audio recorded"
                            ))
                        }
                        return
                    }

                    // Setup Whisper parameters and process accumulated audio
                    memScoped {
                        val paramsPtr = whisper_full_default_params_by_ref(whisper_sampling_strategy.WHISPER_SAMPLING_GREEDY)
                        
                        if (paramsPtr == null) {
                            println("Failed to get whisper parameters")
                            if (continuation.isActive) {
                                continuation.resume(SpeechRecognitionResult(
                                    success = false,
                                    text = "Failed to get whisper parameters"
                                ))
                            }
                            return@memScoped
                        }

                        try {
                            // Process audio with Whisper
                            val samples = accumulatedAudio.toFloatArray()
                            samples.usePinned { pinnedSamples ->
                                val result = whisper_full(
                                    whisperContext,
                                    paramsPtr.pointed.readValue(),
                                    pinnedSamples.addressOf(0),
                                    samples.size
                                )

                                if (result != 0) {
                                    println("Whisper processing failed with code: $result")
                                    if (continuation.isActive) {
                                        continuation.resume(SpeechRecognitionResult(
                                            success = false,
                                            text = "Whisper processing failed with code: $result"
                                        ))
                                    }
                                    return@usePinned
                                }

                                // Get transcription results
                                val nSegments = whisper_full_n_segments(whisperContext)
                                val transcriptionBuilder = StringBuilder()

                                for (i in 0 until nSegments) {
                                    val text = whisper_full_get_segment_text(whisperContext, i)?.toKString()
                                    if (text != null) {
                                        transcriptionBuilder.append(text)
                                    }
                                }

                                val transcription = transcriptionBuilder.toString().trim()

                                if (continuation.isActive) {
                                    continuation.resume(SpeechRecognitionResult(
                                        success = transcription.isNotEmpty(),
                                        text = transcription.ifEmpty { null },
                                        processingTime = processingTime.toDouble()
                                    ))
                                }
                            }
                        } finally {
                            whisper_free_params(paramsPtr)
                        }
                    }
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

                    // Calculate audio level for voice activity detection
                    var audioLevel = 0.0
                    for (i in 0 until frameLength) {
                        audioLevel += if (floatChannelData[i] < 0) -floatChannelData[i].toDouble() else floatChannelData[i].toDouble()
                    }
                    audioLevel /= frameLength
                    val hasVoiceActivity = audioLevel > 0.015

                    // Downsample if needed
                    val downsampleRatio = (recordingFormat.sampleRate / params.sampleRate).toInt().coerceAtLeast(1)
                    val outputSamples = frameLength / downsampleRatio
                    if (outputSamples == 0) return@let

                    // Add samples to buffer
                    for (i in 0 until outputSamples) {
                        accumulatedAudio.add(floatChannelData[i * downsampleRatio])
                    }

                    // Voice activity detection for silence detection
                    if (hasVoiceActivity) {
                        silenceStartTime = 0L
                    } else {
                        if (accumulatedAudio.isNotEmpty()) {
                            if (silenceStartTime == 0L) {
                                silenceStartTime = currentTime
                            } else if (currentTime - silenceStartTime > params.maxSilenceDuration) {
                                stopCurrentRecognition?.invoke()
                            }
                        }
                    }
                }
            }

            audioEngine.prepare()
            audioEngine.startAndReturnError(null)

        } catch (e: Exception) {
            println("Failed to start Whisper speech recognition: $e")
            stopCurrentRecognition?.invoke()
            if (continuation.isActive) {
                continuation.resume(SpeechRecognitionResult(
                    success = false,
                    eventSuccess = false,
                    text = e.message
                ))
            }
        }
    }

    override fun stop() {
        stopCurrentRecognition?.invoke()
        isListening = false
    }

    override fun isAvailable(): Boolean = isModelReady

    override fun isAuthorized(): Boolean {
        return AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
    }

    @OptIn(ExperimentalForeignApi::class)
    protected fun finalize() {
        whisperContext?.let { whisper_free(it) }
        whisperContext = null
    }
}

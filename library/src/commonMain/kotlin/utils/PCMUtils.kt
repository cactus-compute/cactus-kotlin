package utils

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Utility class for working with PCM audio data
 * PCM format: 16-bit signed little-endian samples at 16kHz (Whisper standard)
 */
object PCMUtils {
    const val WHISPER_SAMPLE_RATE = 16000
    const val BYTES_PER_SAMPLE = 2

    /**
     * Validates that the PCM buffer has the correct format
     * @param pcmData The PCM audio data as a byte array
     * @return true if the buffer is valid (length is a multiple of bytes per sample)
     */
    fun validatePCMBuffer(pcmData: ByteArray): Boolean {
        return pcmData.size % BYTES_PER_SAMPLE == 0
    }

    /**
     * Calculates the duration of the audio in seconds
     * @param pcmData The PCM audio data as a byte array
     * @param sampleRate The sample rate of the audio (default: 16000 Hz)
     * @return The duration in seconds
     */
    fun calculateDuration(pcmData: ByteArray, sampleRate: Int = WHISPER_SAMPLE_RATE): Double {
        val numSamples = pcmData.size / BYTES_PER_SAMPLE
        return numSamples.toDouble() / sampleRate
    }

    /**
     * Gets the number of samples in the PCM buffer
     * @param pcmData The PCM audio data as a byte array
     * @return The number of samples
     */
    fun getSampleCount(pcmData: ByteArray): Int {
        return pcmData.size / BYTES_PER_SAMPLE
    }

    /**
     * Generates synthetic audio data for testing
     * @param durationSeconds The duration of the audio in seconds
     * @param frequency The frequency of the sine wave (default: 440 Hz, A4 note)
     * @param amplitude The amplitude of the wave (0.0 to 1.0, default: 0.3)
     * @param sampleRate The sample rate (default: 16000 Hz)
     * @return PCM audio data as a byte array
     */
    fun generateSyntheticAudio(
        durationSeconds: Int,
        frequency: Double = 440.0,
        amplitude: Double = 0.3,
        sampleRate: Int = WHISPER_SAMPLE_RATE
    ): ByteArray {
        val numSamples = sampleRate * durationSeconds
        val pcmBytes = ByteArray(numSamples * BYTES_PER_SAMPLE)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val value = amplitude * sin(2.0 * PI * frequency * t)

            val sample = (value * 32767.0).roundToInt().coerceIn(-32768, 32767)

            // Store as little-endian 16-bit signed integer
            val byteIndex = i * BYTES_PER_SAMPLE
            pcmBytes[byteIndex] = (sample and 0xFF).toByte()
            pcmBytes[byteIndex + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        return pcmBytes
    }

    /**
     * Converts PCM 16-bit data to normalized Float32 samples (-1.0 to 1.0)
     * @param pcmData The PCM audio data as a byte array
     * @return Float array with normalized samples
     */
    fun pcmToFloat32(pcmData: ByteArray): FloatArray {
        val numSamples = pcmData.size / BYTES_PER_SAMPLE
        val samples = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val byteIndex = i * BYTES_PER_SAMPLE
            val low = pcmData[byteIndex].toInt() and 0xFF
            val high = pcmData[byteIndex + 1].toInt() and 0xFF
            val sample = (low or (high shl 8)).toShort().toInt()
            samples[i] = sample / 32768.0f
        }

        return samples
    }

    /**
     * Converts normalized Float32 samples to PCM 16-bit data
     * @param samples Float array with normalized samples (-1.0 to 1.0)
     * @return PCM audio data as a byte array
     */
    fun float32ToPCM(samples: FloatArray): ByteArray {
        val pcmBytes = ByteArray(samples.size * BYTES_PER_SAMPLE)

        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1.0f, 1.0f)
            val sample = (clamped * 32767.0f).roundToInt().coerceIn(-32768, 32767)

            // Store as little-endian 16-bit signed integer
            val byteIndex = i * BYTES_PER_SAMPLE
            pcmBytes[byteIndex] = (sample and 0xFF).toByte()
            pcmBytes[byteIndex + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        return pcmBytes
    }

    /**
     * Trims the PCM buffer to contain only valid complete samples
     * @param pcmData The PCM audio data as a byte array
     * @return Trimmed PCM data with only complete samples
     */
    fun trimToValidSamples(pcmData: ByteArray): ByteArray {
        if (pcmData.size % BYTES_PER_SAMPLE == 0) {
            return pcmData
        }
        val validLength = (pcmData.size / BYTES_PER_SAMPLE) * BYTES_PER_SAMPLE
        return pcmData.copyOfRange(0, validLength)
    }

    /**
     * Resamples PCM audio from one sample rate to another
     * Uses simple linear interpolation
     * @param pcmData The source PCM audio data
     * @param sourceSampleRate The source sample rate
     * @param targetSampleRate The target sample rate
     * @return Resampled PCM audio data
     */
    fun resample(
        pcmData: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ByteArray {
        if (sourceSampleRate == targetSampleRate) {
            return pcmData
        }

        val sourceFloats = pcmToFloat32(pcmData)
        val sourceSamples = sourceFloats.size
        val targetSamples = (sourceSamples.toDouble() * targetSampleRate / sourceSampleRate).roundToInt()
        val targetFloats = FloatArray(targetSamples)

        for (i in 0 until targetSamples) {
            val sourceIndex = i.toDouble() * sourceSampleRate / targetSampleRate
            val index = sourceIndex.toInt()
            val fraction = sourceIndex - index

            if (index + 1 < sourceSamples) {
                // Linear interpolation
                targetFloats[i] = (sourceFloats[index] * (1 - fraction) +
                                  sourceFloats[index + 1] * fraction).toFloat()
            } else {
                targetFloats[i] = sourceFloats[sourceSamples - 1]
            }
        }

        return float32ToPCM(targetFloats)
    }

    /**
     * Converts stereo PCM to mono by averaging the channels
     * @param stereoPcmData Stereo PCM data (interleaved left/right)
     * @return Mono PCM data
     */
    fun stereoToMono(stereoPcmData: ByteArray): ByteArray {
        val stereoSamples = pcmToFloat32(stereoPcmData)
        val monoSamples = FloatArray(stereoSamples.size / 2)

        for (i in monoSamples.indices) {
            val left = stereoSamples[i * 2]
            val right = stereoSamples[i * 2 + 1]
            monoSamples[i] = (left + right) / 2.0f
        }

        return float32ToPCM(monoSamples)
    }

    /**
     * Gets audio information as a formatted string
     * @param pcmData The PCM audio data
     * @param sampleRate The sample rate (default: 16000 Hz)
     * @return Formatted string with audio information
     */
    fun getAudioInfo(pcmData: ByteArray, sampleRate: Int = WHISPER_SAMPLE_RATE): String {
        val samples = getSampleCount(pcmData)
        val duration = calculateDuration(pcmData, sampleRate)
        val sizeKB = pcmData.size / 1024.0

        return buildString {
            appendLine("Audio Information:")
            appendLine("  Size: ${(sizeKB * 100).roundToInt() / 100.0} KB")
            appendLine("  Samples: $samples")
            appendLine("  Duration: ${(duration * 100).roundToInt() / 100.0} seconds")
            appendLine("  Sample Rate: $sampleRate Hz")
            appendLine("  Channels: 1 (Mono)")
            appendLine("  Bit Depth: 16-bit")
        }
    }
}

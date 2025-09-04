package com.cactus.services

import com.cactus.CactusContext.getBundleId
import com.cactus.CactusContext.sha1

object CactusId {

    // RFC 4122 URL namespace
    private val NAMESPACE_URL: ByteArray = "6ba7b811-9dad-11d1-80b4-00c04fd430c8".toUuidBytes()

    fun getProjectId(seed: String = "v1"): String {
        val bundleId = getBundleId()
        val name = "https://your.plugin/$bundleId/$seed"
        return uuidV5(name.encodeToByteArray())
    }

    private fun uuidV5(name: ByteArray): String {
        val toHash = ByteArray(NAMESPACE_URL.size + name.size)
        NAMESPACE_URL.copyInto(toHash, destinationOffset = 0)
        name.copyInto(toHash, destinationOffset = NAMESPACE_URL.size)

        val hash = sha1(toHash)              // 20 bytes
        val bytes = hash.copyOf(16)          // take first 16 bytes

        // version (5) in high nibble of byte 6
        bytes[6] = ((bytes[6].toInt() and 0x0F) or (0x05 shl 4)).toByte()
        // variant (RFC 4122) in byte 8
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        return bytes.toUuidString()
    }

    private fun String.toUuidBytes(): ByteArray {
        val hex = replace("-", "")
        require(hex.length == 32) { "UUID hex must be 32 chars" }
        return ByteArray(16) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toUuidString(): String {
        require(size == 16)
        fun b(i: Int) = (this[i].toInt() and 0xFF).toString(16).padStart(2, '0')
        return buildString(36) {
            append(b(0)); append(b(1)); append(b(2)); append(b(3)); append('-')
            append(b(4)); append(b(5)); append('-')
            append(b(6)); append(b(7)); append('-')
            append(b(8)); append(b(9)); append('-')
            append(b(10)); append(b(11)); append(b(12)); append(b(13)); append(b(14)); append(b(15))
        }
    }
}
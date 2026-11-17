package org.mewx.wenku8.util

import java.security.MessageDigest

/**
 * Deterministic UTF-8 SHA-256 hex hashing utility used for TOC sync deduplication (`toc_sha256`)
 * and offline chapter integrity checksums (`sha256_checksum`).
 */
object DigestUtils {

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(bytes.size * 2)
        val hexAlphabet = "0123456789abcdef"
        for (index in bytes.indices) {
            val unsignedByte = bytes[index].toInt() and 0xFF
            hexChars[index * 2] = hexAlphabet[unsignedByte ushr 4]
            hexChars[index * 2 + 1] = hexAlphabet[unsignedByte and 0x0F]
        }
        return String(hexChars)
    }
}

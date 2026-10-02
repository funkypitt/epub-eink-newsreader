/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.security

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Turns a secret (the WebDAV password) into the text kept in the settings
 * and back. Both directions throw when the key cannot be used; an empty
 * text stays empty.
 */
interface SecretCipher {
    fun encrypt(clear: String): String
    fun decrypt(stored: String): String
}

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_SIZE = 12
private const val TAG_BITS = 128

/**
 * AES-GCM with a fresh IV for every encryption; the stored text is
 * Base64(IV + ciphertext). The IV is left to the cipher because a key
 * held by the Android Keystore refuses one chosen by the caller.
 */
open class AesGcmCipher(private val key: () -> SecretKey) : SecretCipher {

    override fun encrypt(clear: String): String {
        if (clear.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(clear.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    override fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        val bytes = Base64.getDecoder().decode(stored)
        require(bytes.size > IV_SIZE) { "Stored secret is too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        val clear = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
        return String(clear, StandardCharsets.UTF_8)
    }
}

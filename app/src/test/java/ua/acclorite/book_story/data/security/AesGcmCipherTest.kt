/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AesGcmCipherTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmCipher { key }

    @Test
    fun `a password comes back unchanged`() {
        val password = "pässwörd: 5€ \"quoted\" 日本"
        assertEquals(password, cipher.decrypt(cipher.encrypt(password)))
    }

    @Test
    fun `the stored text does not contain the password`() {
        val password = "correct-horse-battery"
        val stored = cipher.encrypt(password)
        assertFalse(stored.contains(password))
        assertFalse(String(Base64.getDecoder().decode(stored), Charsets.ISO_8859_1).contains(password))
    }

    @Test
    fun `the same password is stored differently each time`() {
        assertNotEquals(cipher.encrypt("secret"), cipher.encrypt("secret"))
    }

    @Test
    fun `an empty password stays empty and needs no key`() {
        val noKey = AesGcmCipher { error("no key") }
        assertEquals("", noKey.encrypt(""))
        assertEquals("", noKey.decrypt(""))
    }

    @Test
    fun `another key cannot read the stored text`() {
        val stored = cipher.encrypt("secret")
        val other = newKey()
        assertThrows(Exception::class.java) { AesGcmCipher { other }.decrypt(stored) }
    }

    @Test
    fun `an altered stored text is refused`() {
        val bytes = Base64.getDecoder().decode(cipher.encrypt("secret"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        val altered = Base64.getEncoder().encodeToString(bytes)
        assertThrows(Exception::class.java) { cipher.decrypt(altered) }
    }

    @Test
    fun `a clear password left in place of the stored text is refused`() {
        assertThrows(Exception::class.java) { cipher.decrypt("my old clear password") }
    }
}

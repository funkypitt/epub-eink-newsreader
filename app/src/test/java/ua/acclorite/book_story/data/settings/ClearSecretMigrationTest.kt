/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.acclorite.book_story.data.local.data_store.DataStore
import ua.acclorite.book_story.data.security.AesGcmCipher
import ua.acclorite.book_story.data.security.SecretCipher
import javax.crypto.KeyGenerator

class ClearSecretMigrationTest {

    private class FakeDataStore : DataStore {
        val values = mutableMapOf<String, Any?>()

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> getNullableData(key: Preferences.Key<T>): T? = values[key.name] as T?

        override suspend fun <T> putData(key: Preferences.Key<T>, value: T) {
            values[key.name] = value
        }

        override suspend fun <T> removeData(key: Preferences.Key<T>) {
            values.remove(key.name)
        }
    }

    private object BrokenCipher : SecretCipher {
        override fun encrypt(clear: String): String = error("key unavailable")
        override fun decrypt(stored: String): String = error("key unavailable")
    }

    private val clearKey = stringPreferencesKey("kdrive_sync_password")
    private val encryptedKey = stringPreferencesKey("kdrive_sync_password_encrypted")

    private val secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCipher { secretKey }
    private val store = FakeDataStore()

    private fun migrate(with: SecretCipher = cipher) = runBlocking {
        migrateClearSecret(store, with, clearKey, encryptedKey)
    }

    @Test
    fun `a clear password is stored encrypted and its clear copy removed`() {
        store.values[clearKey.name] = "hunter2"

        assertTrue(migrate())

        assertFalse(store.values.containsKey(clearKey.name))
        val stored = store.values[encryptedKey.name] as String
        assertFalse(stored.contains("hunter2"))
        assertEquals("hunter2", cipher.decrypt(stored))
        assertFalse(store.values.values.any { it == "hunter2" })
    }

    @Test
    fun `an install without the old setting is left alone`() {
        assertTrue(migrate(BrokenCipher))
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `an empty clear password is simply removed`() {
        store.values[clearKey.name] = ""

        assertTrue(migrate(BrokenCipher))

        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `a second run changes nothing`() {
        store.values[clearKey.name] = "hunter2"
        migrate()
        val stored = store.values[encryptedKey.name]

        assertTrue(migrate())

        assertEquals(stored, store.values[encryptedKey.name])
        assertFalse(store.values.containsKey(clearKey.name))
    }

    @Test
    fun `a run stopped before the clear copy was removed is finished`() {
        val stored = cipher.encrypt("hunter2")
        store.values[clearKey.name] = "hunter2"
        store.values[encryptedKey.name] = stored

        assertTrue(migrate())

        assertEquals(stored, store.values[encryptedKey.name])
        assertFalse(store.values.containsKey(clearKey.name))
    }

    @Test
    fun `an empty encrypted value does not hide the clear password`() {
        store.values[clearKey.name] = "hunter2"
        store.values[encryptedKey.name] = ""

        assertTrue(migrate())

        assertEquals("hunter2", cipher.decrypt(store.values[encryptedKey.name] as String))
        assertFalse(store.values.containsKey(clearKey.name))
    }

    @Test
    fun `when the key cannot be used the password is kept for the next start`() {
        store.values[clearKey.name] = "hunter2"

        assertFalse(migrate(BrokenCipher))

        assertEquals("hunter2", store.values[clearKey.name])
        assertNull(store.values[encryptedKey.name])
    }
}

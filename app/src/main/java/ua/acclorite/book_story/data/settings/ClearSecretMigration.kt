/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.settings

import androidx.datastore.preferences.core.Preferences
import ua.acclorite.book_story.data.local.data_store.DataStore
import ua.acclorite.book_story.data.security.SecretCipher

/**
 * Moves a secret that an earlier version kept in clear under [clearKey] to
 * its encrypted form under [encryptedKey], then removes the clear copy.
 *
 * The clear copy is only removed once the encrypted one is written: if the
 * key cannot be used this time, nothing changes and the next start tries
 * again. Returns false in that case only.
 */
suspend fun migrateClearSecret(
    dataStore: DataStore,
    cipher: SecretCipher,
    clearKey: Preferences.Key<String>,
    encryptedKey: Preferences.Key<String>
): Boolean {
    val clear = dataStore.getNullableData(clearKey) ?: return true

    if (clear.isNotEmpty() && dataStore.getNullableData(encryptedKey).isNullOrEmpty()) {
        val encrypted = runCatching { cipher.encrypt(clear) }.getOrNull()
        if (encrypted.isNullOrEmpty()) return false
        dataStore.putData(encryptedKey, encrypted)
    }

    dataStore.removeData(clearKey)
    return true
}

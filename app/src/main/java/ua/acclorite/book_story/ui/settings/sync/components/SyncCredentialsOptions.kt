/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.sync.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.data.credentials.Credentials
import ua.acclorite.book_story.data.credentials.CredentialsShare
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.common.helpers.showToast

/** The share link or the WebDAV account as one Reader's credentials file, for another device. */
@Composable
fun SyncExportCredentialsOption() {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val title = stringResource(id = R.string.sync_export_credentials)
    val configured = settings.kdriveShareLink.value.isNotBlank() || settings.kdriveSyncUrl.value.isNotBlank()
    if (!configured) return
    TextSettingOption(
        title = title,
        currentValue = stringResource(id = R.string.sync_export_credentials_desc),
        onClick = {
            CredentialsShare.share(
                context,
                Credentials.build(
                    settings.kdriveShareLink.lastValue, settings.kdriveSyncUrl.lastValue,
                    settings.kdriveSyncUsername.lastValue, settings.kdriveSyncPassword.lastValue
                ),
                title
            )
        }
    )
}

/** A Reader's credentials file from another device or app sets the sync up in one step. */
@Composable
fun SyncImportCredentialsOption() {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val notCredentials = stringResource(id = R.string.sync_credentials_not_a_file)
    val nothingForUs = stringResource(id = R.string.sync_credentials_nothing)
    val imported = stringResource(id = R.string.sync_credentials_imported)
    val importedFrom = stringResource(id = R.string.sync_credentials_imported_from)
    val unreadable = stringResource(id = R.string.sync_credentials_unreadable)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val message = try {
            val got = Credentials.read(CredentialsShare.readText(context, uri))
            val a = got.account
            // a file that names a share link replaces the account: a link and an address never both apply
            if (a.share != null) {
                settings.kdriveShareLink.update(a.share)
                settings.kdriveSyncUrl.update(""); settings.kdriveSyncUsername.update(""); settings.kdriveSyncPassword.update("")
            } else {
                settings.kdriveShareLink.update("")
                a.url?.let { settings.kdriveSyncUrl.update(it) }
                a.username?.let { settings.kdriveSyncUsername.update(it) }
                a.password?.let { settings.kdriveSyncPassword.update(it) }
            }
            if (got.fromFallback) importedFrom else imported
        } catch (e: Credentials.NotCredentials) { notCredentials
        } catch (e: Credentials.NothingForUs) { nothingForUs
        } catch (e: Exception) { unreadable.format(e.message ?: e.javaClass.simpleName) }
        message.showToast(context)
    }
    TextSettingOption(
        title = stringResource(id = R.string.sync_import_credentials),
        currentValue = stringResource(id = R.string.sync_import_credentials_desc),
        onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
    )
}

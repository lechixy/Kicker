package com.lechixy.kick.screens.settings

import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.local.SettingsBackupManager
import com.lechixy.kick.data.settings.local.SettingsBackupManager.StartupBackup
import com.lechixy.kick.util.LanguageUtils.getCurrentAppLanguage
import com.lechixy.kick.util.LanguageUtils.updateAppLocale
import com.lechixy.kick.util.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SettingsBackupActions(
    val enabled: Boolean,
    val customLocationSelected: Boolean,
    val defaultPath: String,
    val importSettings: () -> Unit,
    val exportSettings: () -> Unit,
    val chooseAutomaticLocation: () -> Unit
)

internal val LocalSettingsBackupActions = compositionLocalOf<SettingsBackupActions?> { null }

@Composable
internal fun SettingsBackupHost(
    backup: SettingsBackupManager,
    content: @Composable (restorePending: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offerRestore by remember(backup) { mutableStateOf<StartupBackup?>(null) }
    var choosingRestoreFile by rememberSaveable { mutableStateOf(false) }
    var choosingLocation by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val customLocation by backup.customLocation.collectAsStateWithLifecycle()

    LaunchedEffect(backup) { offerRestore = backup.startupRestoreOffer() }

    fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun applyImportedLanguage(language: String?) {
        toast(R.string.settings_backup_imported)
        val activity = context.findActivity()
        if (language != null && activity != null && language != getCurrentAppLanguage(activity)) {
            updateAppLocale(activity, language)
        }
    }

    fun runBackupOperation(errorMessage: Int, operation: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SettingsBackupManager.UnsupportedVersion) {
                toast(R.string.settings_backup_unsupported_version)
            } catch (_: Exception) {
                toast(errorMessage)
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runBackupOperation(R.string.settings_backup_import_failed) {
            applyImportedLanguage(backup.importFrom(uri))
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        choosingRestoreFile = false
        result.data?.data?.let { uri ->
            runBackupOperation(R.string.settings_backup_import_failed) {
                val language = backup.restorePickedBackup(uri)
                offerRestore = StartupBackup.None
                applyImportedLanguage(language)
            }
        }
    }
    fun chooseRestoreFile() {
        val directory = backup.defaultPath.substringBeforeLast('/')
        val initialUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:$directory",
        )
        choosingRestoreFile = true
        restorePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        })
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SettingsBackupManager.MIME_TYPE)
    ) { uri ->
        if (uri != null) runBackupOperation(R.string.settings_backup_export_failed) {
            backup.exportTo(uri)
            toast(R.string.settings_backup_exported)
        }
    }
    val automaticLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SettingsBackupManager.MIME_TYPE)
    ) { uri ->
        if (uri != null) runBackupOperation(R.string.settings_backup_location_failed) {
            backup.setAutomaticLocation(uri)
            toast(R.string.settings_backup_location_saved)
        }
    }

    CompositionLocalProvider(
        LocalSettingsBackupActions provides SettingsBackupActions(
            enabled = !busy && offerRestore == StartupBackup.None,
            customLocationSelected = customLocation != null,
            defaultPath = backup.defaultPath,
            importSettings = { importLauncher.launch(arrayOf("*/*")) },
            exportSettings = { exportLauncher.launch(backup.fileName) },
            chooseAutomaticLocation = { choosingLocation = true }
        )
    ) { content(offerRestore != StartupBackup.None || choosingLocation) }

    val offer = offerRestore
    if (offer != null && offer != StartupBackup.None && !busy && !choosingRestoreFile) {
        val skip = {
            runBackupOperation(R.string.settings_backup_restore_choice_failed) {
                backup.finishStartupRestore(preserveBackup = true)
                offerRestore = StartupBackup.None
            }
        }
        AlertDialog(
            onDismissRequest = skip,
            title = { Text(stringResource(R.string.settings_backup_restore_title)) },
            text = {
                Column {
                    Text(stringResource(
                        if (offer is StartupBackup.Found) R.string.settings_backup_restore_message
                        else R.string.settings_backup_find_message,
                        backup.defaultPath,
                    ))
                    if (offer is StartupBackup.Found) {
                        TextButton(onClick = ::chooseRestoreFile) {
                            Text(stringResource(R.string.settings_backup_choose_file))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (offer is StartupBackup.Found) {
                        runBackupOperation(R.string.settings_backup_import_failed) {
                            val language = backup.restoreDefaultBackup()
                            offerRestore = StartupBackup.None
                            applyImportedLanguage(language)
                        }
                    } else {
                        chooseRestoreFile()
                    }
                }) { Text(stringResource(if (offer is StartupBackup.Found) R.string.settings_backup_restore else R.string.settings_backup_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = skip) { Text(stringResource(R.string.settings_backup_skip)) }
            }
        )
    }

    if (choosingLocation) {
        AlertDialog(
            onDismissRequest = { choosingLocation = false },
            title = { Text(stringResource(R.string.settings_backup_location)) },
            text = { Text(stringResource(R.string.settings_backup_location_message, backup.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    choosingLocation = false
                    automaticLocationLauncher.launch(backup.fileName)
                }) { Text(stringResource(R.string.settings_backup_location_choose)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingLocation = false
                    runBackupOperation(R.string.settings_backup_location_failed) {
                        backup.setAutomaticLocation(null)
                        toast(R.string.settings_backup_location_saved)
                    }
                }) { Text(stringResource(R.string.settings_backup_location_default)) }
            }
        )
    }
}

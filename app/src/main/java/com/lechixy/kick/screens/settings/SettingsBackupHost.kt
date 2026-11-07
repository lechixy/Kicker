package com.lechixy.kick.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.lechixy.kick.util.LanguageUtils.getCurrentAppLanguage
import com.lechixy.kick.util.LanguageUtils.updateAppLocale
import com.lechixy.kick.util.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SettingsBackupActions(
    val enabled: Boolean,
    val customLocationSelected: Boolean,
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
    var offerRestore by remember { mutableStateOf<Boolean?>(null) }
    var choosingLocation by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val customLocation by backup.customLocation.collectAsStateWithLifecycle()

    LaunchedEffect(backup) { offerRestore = backup.shouldOfferRestore() }

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
            enabled = !busy && offerRestore == false,
            customLocationSelected = customLocation != null,
            importSettings = { importLauncher.launch(arrayOf("*/*")) },
            exportSettings = { exportLauncher.launch(SettingsBackupManager.FILE_NAME) },
            chooseAutomaticLocation = { choosingLocation = true }
        )
    ) { content(offerRestore != false || choosingLocation) }

    if (offerRestore == true && !busy) {
        val skip = {
            runBackupOperation(R.string.settings_backup_restore_choice_failed) {
                backup.finishStartupRestore(preserveBackup = true)
                offerRestore = false
            }
        }
        AlertDialog(
            onDismissRequest = skip,
            title = { Text(stringResource(R.string.settings_backup_restore_title)) },
            text = { Text(stringResource(R.string.settings_backup_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    runBackupOperation(R.string.settings_backup_import_failed) {
                        val language = backup.restoreDefaultBackup()
                        offerRestore = false
                        applyImportedLanguage(language)
                    }
                }) { Text(stringResource(R.string.settings_backup_restore)) }
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
            text = { Text(stringResource(R.string.settings_backup_location_message)) },
            confirmButton = {
                TextButton(onClick = {
                    choosingLocation = false
                    automaticLocationLauncher.launch(SettingsBackupManager.FILE_NAME)
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

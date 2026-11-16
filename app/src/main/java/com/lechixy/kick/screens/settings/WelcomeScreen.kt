package com.lechixy.kick.screens.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.settings.local.SettingsBackupManager
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val LocalOpenWelcome = compositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun WelcomeHost(backupManager: SettingsBackupManager, content: @Composable (Boolean) -> Unit) {
    val settings = LocalSettingsRepository.current
    var welcomeAtStartup by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var replay by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings) {
        if (welcomeAtStartup == null) {
            val snapshot = settings.snapshot()
            welcomeAtStartup = snapshot[SettingsKeys.IS_FIRST_RUN.key] != false &&
                snapshot[SettingsKeys.WELCOME_COMPLETED.key] != true
        }
    }
    val startup = welcomeAtStartup ?: return
    SettingsBackupHost(backupManager, welcomeAtStartup = startup) { restorePending ->
        val backup = LocalSettingsBackupActions.current
        val scope = rememberCoroutineScope()
        var finishing by remember { mutableStateOf(false) }
        var finishFailed by remember { mutableStateOf(false) }
        CompositionLocalProvider(LocalOpenWelcome provides { replay = true }) {
            if (startup) {
                WelcomeScreen(
                    backup = backup,
                    isFinishing = finishing,
                    finishFailed = finishFailed,
                    onFinish = {
                        if (!finishing && backup != null) {
                            finishing = true
                            finishFailed = false
                            scope.launch {
                                try {
                                    backup.finishStartupRestore()
                                    settings.set(SettingsKeys.WELCOME_COMPLETED, true)
                                    welcomeAtStartup = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    finishFailed = true
                                } finally {
                                    finishing = false
                                }
                            }
                        }
                    }
                )
            } else {
                content(restorePending || replay)
                if (replay) {
                    Dialog(
                        onDismissRequest = { replay = false },
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        WelcomeScreen(backup = backup, onClose = { replay = false }, onFinish = { replay = false })
                    }
                }
            }
        }
    }
}

@Composable
internal fun WelcomeSetting() {
    val openWelcome = LocalOpenWelcome.current
    SettingCard(
        title = stringResource(R.string.welcome_settings_title),
        description = stringResource(R.string.welcome_settings_summary),
        icon = R.drawable.celebration_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        enabled = openWelcome != null,
        onClick = openWelcome
    )
}

private enum class WelcomeStep(@StringRes val title: Int, @StringRes val description: Int, @DrawableRes val icon: Int) {
    Intro(R.string.welcome_intro_title, R.string.welcome_intro_body, R.drawable.kick_logo),
    Account(R.string.welcome_account_title, R.string.welcome_account_body, R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    Backup(R.string.welcome_backup_title, R.string.welcome_backup_body, R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    Ready(R.string.welcome_ready_title, R.string.welcome_ready_body, R.drawable.check_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
}

@Composable
private fun WelcomeScreen(
    backup: SettingsBackupActions?,
    isFinishing: Boolean = false,
    finishFailed: Boolean = false,
    onClose: (() -> Unit)? = null,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(WelcomeStep.Intro) }
    val auth = AccountGraph.auth
    val session by auth.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val goBack: () -> Unit = {
        if (step.ordinal > 0) step = WelcomeStep.entries[step.ordinal - 1]
        else onClose?.invoke()
    }
    BackHandler { if (!isFinishing) goBack() }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val viewportHeight = maxHeight
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    if (step != WelcomeStep.Intro) {
                        IconButton(
                            onClick = goBack, enabled = !isFinishing,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                stringResource(R.string.welcome_back))
                        }
                    }
                    if (onClose != null) {
                        TextButton(onClose, Modifier.align(Alignment.CenterEnd)) {
                            Text(stringResource(R.string.welcome_close))
                        }
                    }
                }
                WelcomeProgress(step)
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                val shape = RoundedCornerShape(28.dp)
                Box(
                    Modifier.fillMaxWidth().height(132.dp).clip(shape).background(
                        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh))
                    ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        modifier = Modifier.padding(start = 20.dp).size(78.dp),
                        color = scheme.surfaceContainer,
                        contentColor = scheme.primary,
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = { fadeIn(tween(220, 45)) togetherWith fadeOut(tween(130)) },
                            label = "welcome_icon"
                        ) { current ->
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(current.icon), null, Modifier.size(34.dp))
                            }
                        }
                    }
                }
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(tween(240, 50)) togetherWith fadeOut(tween(130))).using(SizeTransform(clip = false))
                    },
                    label = "welcome_copy"
                ) { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(current.title), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                            color = scheme.onBackground
                        )
                        Text(
                            stringResource(current.description), style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (step) {
                    WelcomeStep.Intro -> WelcomePrimaryButton(R.string.welcome_get_started, onClick = { step = WelcomeStep.Account })
                    WelcomeStep.Account -> {
                        val connecting = session.status == SessionStatus.Connecting
                        val error = session.error
                        if (session.signedIn) {
                            val username = session.profile?.name
                            Text(
                                if (username != null) stringResource(R.string.welcome_account_connected_as, username)
                                else stringResource(R.string.welcome_account_connected),
                                color = scheme.primary, style = MaterialTheme.typography.labelLarge
                            )
                            WelcomePrimaryButton(R.string.welcome_continue, onClick = { step = WelcomeStep.Backup })
                        } else {
                            if (error != null) Text(error, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                            WelcomePrimaryButton(
                                if (connecting) R.string.account_waiting_login else R.string.account_login,
                                enabled = !connecting,
                                onClick = { auth.beginLogin(context) }
                            )
                            if (connecting) {
                                TextButton(auth::cancelLogin, Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.account_cancel_login))
                                }
                            } else {
                                TextButton({ step = WelcomeStep.Backup }, Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.welcome_skip))
                                }
                            }
                        }
                    }
                    WelcomeStep.Backup -> {
                        val restore = backup?.restoreFoundBackup
                        val enabled = backup?.welcomeImportEnabled == true
                        WelcomePrimaryButton(
                            if (restore != null) R.string.settings_backup_restore else R.string.welcome_import_backup,
                            enabled = enabled,
                            onClick = { if (restore != null) restore() else backup?.welcomeImport?.invoke() }
                        )
                        if (restore != null) {
                            TextButton(backup.welcomeImport, Modifier.fillMaxWidth(), enabled = enabled) {
                                Text(stringResource(R.string.settings_backup_choose_file))
                            }
                        }
                        TextButton({ step = WelcomeStep.Ready }, Modifier.fillMaxWidth(), enabled = enabled) {
                            Text(stringResource(R.string.welcome_continue))
                        }
                    }
                    WelcomeStep.Ready -> {
                        if (finishFailed) {
                            Text(stringResource(R.string.welcome_finish_failed), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                        }
                        WelcomePrimaryButton(
                            R.string.welcome_open_app, enabled = !isFinishing && (onClose != null || backup?.welcomeImportEnabled == true),
                            onClick = onFinish
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeProgress(step: WelcomeStep) {
    val steps = WelcomeStep.entries
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(7.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(step.ordinal + 1f, 0f..steps.size.toFloat(), steps.size - 1)
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEach { item ->
            val color by animateColorAsState(
                if (item.ordinal <= step.ordinal) scheme.primary else scheme.surfaceVariant,
                label = "welcome_progress_color"
            )
            val height by animateDpAsState(if (item == step) 7.dp else 4.dp, label = "welcome_progress_height")
            Box(Modifier.weight(1f).height(height).background(color, RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun WelcomePrimaryButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) {
        Text(stringResource(label))
    }
}

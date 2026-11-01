package com.lechixy.kick.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.components.ColoredAppText
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.util.AppUtils
import com.lechixy.kick.util.AppUtils.getVersionAndInfo

enum class SettingsDestinations(
    val title: String,
    val description: String,
    val icon: Int
) {
    DEVELOPER("Developer tools", "Runtime diagnostics and event previews", R.drawable.apps_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    GENERAL(
        "General",
        "Basic app behavior, localization, and system defaults",
        R.drawable.apps_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    CUSTOMIZATION(
        "Customization",
        "Themes, window styles, and visual tweaks",
        R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    PLAYBACK(
        "Playback",
        "Media controls, quality defaults, and auto-play options",
        R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSubsettingClick: (subsetting: SettingsDestinations) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val appInfo = getVersionAndInfo(context)
    val repository = LocalSettingsRepository.current
    val unlocked by repository.get(SettingsKeys.DEVELOPER_ENABLED).collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    var developerTaps by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            com.lechixy.kick.components.HomeTopBar(showAccount = false)

        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.lechixy.kick.components.SectionHeader(stringResource(R.string.settings))
                        Box(Modifier.fillMaxWidth()) {
                            androidx.compose.material3.ElevatedCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleMedium)
                                    com.lechixy.kick.components.account.AccountButton()
                                }
                            }
                        }
                    }
                }
                items(SettingsDestinations.entries.filter {
                    it != SettingsDestinations.DEVELOPER || BuildConfig.DEBUG || unlocked
                }, key = { it.name }) { dest ->
                            SettingCard(
                                title = if (dest == SettingsDestinations.DEVELOPER) stringResource(R.string.developer_tools) else stringResource(when (dest) {
                                    SettingsDestinations.GENERAL -> R.string.settings_general
                                    SettingsDestinations.CUSTOMIZATION -> R.string.settings_customization
                                    else -> R.string.player_playback
                                }),
                                description = if (dest == SettingsDestinations.DEVELOPER) stringResource(R.string.developer_description) else stringResource(when (dest) {
                                    SettingsDestinations.GENERAL -> R.string.settings_general_description
                                    SettingsDestinations.CUSTOMIZATION -> R.string.settings_customization_description
                                    else -> R.string.player_section_description
                                }),
                                icon = dest.icon,
                                onClick = {
                                    onSubsettingClick(dest)
                                }
                            )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        ColoredAppText()
                        Spacer(modifier = Modifier.height(40.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(20.dp)
                                )
                                .clip(shape = RoundedCornerShape(20.dp))
                                .clickable {
                                    if (!BuildConfig.DEBUG && !unlocked) {
                                        developerTaps++
                                        if (developerTaps >= 7) scope.launch {
                                            repository.set(SettingsKeys.DEVELOPER_ENABLED, true)
                                            Toast.makeText(context, R.string.developer_unlocked, Toast.LENGTH_SHORT).show()
                                        }
                                    } else Toast.makeText(context, "💖", Toast.LENGTH_SHORT).show()
                                }
                                .padding(25.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.settings_made_by),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_for_you),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(15.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(20.dp)
                                )
                                .clip(shape = RoundedCornerShape(20.dp))
                                .clickable {
                                    val browserIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        context.resources.getString(R.string.github).toUri()
                                    )
                                    context.startActivity(browserIntent)
                                }
                                .padding(15.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.settings_version, appInfo.versionName)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_updated, AppUtils.formatLastUpdateTime(appInfo.lastUpdateTimeMs)),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_github),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

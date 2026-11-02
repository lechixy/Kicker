package com.lechixy.kick.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.components.ColoredAppText
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.animatedCreatorOutline
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.util.AppUtils
import com.lechixy.kick.util.AppUtils.getVersionAndInfo
import kotlinx.coroutines.launch

enum class SettingsDestinations(val titleRes: Int, val descriptionRes: Int, val icon: Int) {
    GENERAL(
        R.string.settings_general,
        R.string.settings_general_description,
        R.drawable.apps_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    CUSTOMIZATION(
        R.string.settings_customization,
        R.string.settings_customization_description,
        R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    PLAYBACK(
        R.string.player_playback,
        R.string.player_section_description,
        R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    CHAT(
        R.string.chat,
        R.string.settings_chat_description,
        R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    CHANGELOG(
        R.string.settings_changelog,
        R.string.settings_changelog_description,
        R.drawable.history_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    DEVELOPER(
        R.string.developer_tools,
        R.string.developer_description,
        R.drawable.apps_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSubsettingClick: (subsetting: SettingsDestinations) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val appInfo = getVersionAndInfo(context)
    val repository = LocalSettingsRepository.current
    val unlocked by repository.get(SettingsKeys.DEVELOPER_ENABLED)
        .collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val locale = configuration.locales[0]
    val entries = settingsCatalog()
    val matches =
        entries.filter { it.destination != SettingsDestinations.DEVELOPER || BuildConfig.DEBUG || unlocked }
            .filter {
                matchesSettingsQuery(
                    query,
                    resources.getString(it.title),
                    it.description?.let(resources::getString).orEmpty(),
                    resources.getString(it.destination.titleRes),
                    locale
                )
            }
    var developerTaps by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            com.lechixy.kick.components.HomeTopBar()

        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val rowColumns =
                ((maxWidth.value.coerceAtMost(840f) - 24f + 8f) / 268f).toInt().coerceAtLeast(1)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.settings_search)) },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                painterResource(R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                null
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = {
                                query = ""
                            }) {
                                androidx.compose.material3.Icon(
                                    painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    stringResource(R.string.settings_clear_search)
                                )
                            }
                        })
                }
                if (query.isNotBlank()) {
                    if (matches.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.settings_no_results), Modifier.padding(16.dp))
                    }
                    items(matches, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(entry.destination.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                            )
                            entry.content()
                        }
                    }
                } else {
                    val destinations = SettingsDestinations.entries.filter {
                        it != SettingsDestinations.DEVELOPER || BuildConfig.DEBUG || unlocked
                    }
                    items(
                        destinations.chunked(rowColumns),
                        key = { it.first().name },
                        span = { GridItemSpan(maxLineSpan) }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { dest ->
                                SettingCard(
                                    title = stringResource(dest.titleRes),
                                    description = stringResource(dest.descriptionRes),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    icon = dest.icon,
                                    onClick = { onSubsettingClick(dest) })
                            }
                            repeat(rowColumns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        title = stringResource(R.string.about)
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        ColoredAppText()
                        Spacer(modifier = Modifier.height(48.dp))
                        // App Version & GitHub
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
                                        resources.getString(R.string.github).toUri()
                                    )
                                    context.startActivity(browserIntent)
                                }
                                .padding(15.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_version,
                                    appInfo.versionName
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_updated,
                                    AppUtils.formatLastUpdateTime(appInfo.lastUpdateTimeMs)
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_github),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // Developer tools & For you section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(20.dp)
                                )
                                .clip(shape = RoundedCornerShape(20.dp))
                                .animatedCreatorOutline()
                                .clickable {
                                    if (!BuildConfig.DEBUG && !unlocked) {
                                        developerTaps++
                                        if (developerTaps >= 7) scope.launch {
                                            repository.set(SettingsKeys.DEVELOPER_ENABLED, true)
                                            Toast.makeText(
                                                context,
                                                R.string.developer_unlocked,
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                    }
                }
            }
        }
    }
}

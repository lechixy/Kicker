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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.components.ColoredAppText
import com.lechixy.kick.components.ReleaseDialog
import com.lechixy.kick.components.animatedCreatorCard
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.components.settings.SettingsContentWidth
import com.lechixy.kick.data.releases.GitHubRelease
import com.lechixy.kick.data.releases.ReleaseDialogMode
import com.lechixy.kick.data.releases.ReleaseDialogRequest
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.util.AppUtils
import com.lechixy.kick.util.AppUtils.getVersionAndInfo
import kotlinx.coroutines.launch

enum class SettingsDestinations(val titleRes: Int, val descriptionRes: Int, val icon: Int) {
    ABOUT(R.string.about, R.string.settings_about_description, R.drawable.info_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
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
    DEVELOPER(
        R.string.developer_tools,
        R.string.developer_description,
        R.drawable.apps_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    )
}


@Composable
internal fun SettingsOverview(
    destinations: List<SettingsDestinations>,
    onSelect: (SettingsDestinations) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val columns = ((maxWidth.coerceAtMost(SettingsContentWidth).value - 16f) / 268f).toInt().coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.widthIn(max = SettingsContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("about") {
                val about = SettingsDestinations.ABOUT
                SettingCard(stringResource(about.titleRes), description = stringResource(about.descriptionRes),
                    icon = about.icon, onClick = { onSelect(about) })
            }
            items(destinations.filter { it != SettingsDestinations.ABOUT }.chunked(columns), key = { it.first().name }) { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { section ->
                        SettingCard(stringResource(section.titleRes), description = stringResource(section.descriptionRes),
                            icon = section.icon, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { onSelect(section) })
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item("about-summary") { AboutSummary() }
        }
    }
}

@Composable
internal fun SettingsSearchScreen(query: String, unlocked: Boolean, onBack: () -> Unit, onAbout: () -> Unit) {
    val resources = LocalResources.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val entries = settingsCatalog(expandGroups = query.isNotBlank()).filter { entry ->
        (entry.destination != SettingsDestinations.DEVELOPER || BuildConfig.DEBUG || unlocked) &&
            matchesSettingsQuery(query, resources.getString(entry.title),
                (listOfNotNull(entry.description) + entry.searchTerms).joinToString(" ", transform = resources::getString),
                resources.getString(entry.destination.titleRes) + " " + resources.getString(entry.group), locale)
    }
    val aboutMatches = matchesSettingsQuery(query, resources.getString(R.string.about),
        resources.getString(R.string.settings_about_description),
        resources.getString(R.string.settings_changelog), locale)
    SettingsPage(stringResource(R.string.settings_search), onBack) {
        if (aboutMatches) item("about") {
            SettingCard(stringResource(R.string.about), description = stringResource(R.string.settings_changelog_description),
                icon = SettingsDestinations.ABOUT.icon, onClick = onAbout)
        }
        if (entries.isEmpty() && !aboutMatches) item("empty") { Text(stringResource(R.string.settings_no_results)) }
        groupedSettings(entries, includeCategory = true)
    }
}

@Composable
internal fun SettingsAboutScreen(viewModel: ReleaseViewModel?, onBack: () -> Unit) {
    val state = viewModel?.history?.collectAsStateWithLifecycle()?.value ?: ReleaseHistoryState()
    LaunchedEffect(viewModel) { viewModel?.loadHistory() }
    SettingsAboutPage(state, { viewModel?.loadHistory(retry = true) }, onBack)
}

@Composable
internal fun SettingsAboutPage(state: ReleaseHistoryState, onRetry: () -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<GitHubRelease?>(null) }
    SettingsPage(stringResource(R.string.about), onBack) {
        item("about-summary") { AboutSummary() }
        releaseNotes(state, onRetry) { selected = it }
    }
    selected?.let { ReleaseDialog(ReleaseDialogRequest(ReleaseDialogMode.WhatsNew, it), { selected = null }) }
}

@Composable
private fun AboutSummary() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val appInfo = remember(context) { getVersionAndInfo(context) }
    val repository = LocalSettingsRepository.current
    val unlocked by repository.get(SettingsKeys.DEVELOPER_ENABLED).collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    var developerTaps by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        ColoredAppText()
        Spacer(modifier = Modifier.height(24.dp))
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
                .animatedCreatorCard()
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

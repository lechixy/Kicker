package com.lechixy.kick.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.components.navigationEnter
import com.lechixy.kick.components.navigationExit
import com.lechixy.kick.components.navigationPopEnter
import com.lechixy.kick.components.navigationPopExit
import com.lechixy.kick.components.settings.SettingsContentWidth
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

internal val LocalSettingsSplitPane = staticCompositionLocalOf { false }

private const val OVERVIEW = "settings-overview"
private const val SEARCH = "settings-search"

@Composable
internal fun SettingsHost(
    releaseViewModel: ReleaseViewModel?,
    onExit: () -> Unit,
    initialSection: SettingsDestinations? = null,
    developerContent: @Composable (() -> Unit) -> Unit = { back ->
        SettingsCategoryScreen(SettingsDestinations.DEVELOPER, back)
    },
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val overview = route == null || route == OVERVIEW
    val searching = route == SEARCH
    val selected = SettingsDestinations.entries.firstOrNull { it.name == route }
        ?: SettingsDestinations.ABOUT
    val start = initialSection?.name ?: OVERVIEW
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val unlocked by LocalSettingsRepository.current.get(SettingsKeys.DEVELOPER_ENABLED).collectAsStateWithLifecycle(false)
    val destinations = SettingsDestinations.entries.filter { it != SettingsDestinations.DEVELOPER || BuildConfig.DEBUG || unlocked }
    val back: () -> Unit = {
        focus.clearFocus()
        if (!nav.popBackStack()) onExit()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 640.dp
        val openSection: (SettingsDestinations) -> Unit = { section ->
            focus.clearFocus()
            if (wide && section == SettingsDestinations.ABOUT && start == OVERVIEW) {
                nav.popBackStack(OVERVIEW, inclusive = false)
            } else if (route != section.name) {
                nav.navigate(section.name) {
                    popUpTo(start)
                    launchSingleTop = true
                }
            }
        }
        val updateQuery: (String) -> Unit = { text ->
            if (text.isBlank()) {
                if (searching) nav.popBackStack()
            } else {
                query = text
                if (!searching) nav.navigate(SEARCH) { launchSingleTop = true }
            }
        }
        val paneIsWide by rememberUpdatedState(wide)
        val visibleDestinations by rememberUpdatedState(destinations)
        val selectSection by rememberUpdatedState(openSection)
        val currentBack by rememberUpdatedState(back)
        val developerUnlocked by rememberUpdatedState(unlocked)
        val showDeveloper by rememberUpdatedState(developerContent)
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Surface(Modifier.width(248.dp).fillMaxHeight()) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSearchField(if (searching) query else "", updateQuery) { keyboard?.hide() }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(destinations, key = { it.name }) { section ->
                                NavigationDrawerItem(
                                    label = { Text(stringResource(section.titleRes)) },
                                    selected = !searching && selected == section,
                                    onClick = { openSection(section) },
                                    icon = { Icon(painterResource(section.icon), null) },
                                )
                            }
                        }
                    }
                }
                VerticalDivider()
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!wide && (overview || searching)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = SettingsContentWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SettingsSearchField(if (searching) query else "", updateQuery) { keyboard?.hide() }
                        }
                    }
                }
                CompositionLocalProvider(LocalSettingsSplitPane provides wide) {
                    NavHost(
                        navController = nav,
                        startDestination = start,
                        modifier = Modifier.weight(1f),
                        enterTransition = { navigationEnter(initialState.destination.route, targetState.destination.route) },
                        exitTransition = { navigationExit(initialState.destination.route, targetState.destination.route) },
                        popEnterTransition = { navigationPopEnter(initialState.destination.route, targetState.destination.route) },
                        popExitTransition = { navigationPopExit(initialState.destination.route, targetState.destination.route) },
                    ) {
                        composable(OVERVIEW) {
                            if (paneIsWide) SettingsAboutScreen(releaseViewModel, currentBack)
                            else SettingsOverview(visibleDestinations, selectSection)
                        }
                        composable(SEARCH) {
                            SettingsSearchScreen(query, developerUnlocked, currentBack) { selectSection(SettingsDestinations.ABOUT) }
                        }
                        SettingsDestinations.entries.forEach { section ->
                            composable(section.name) {
                                when (section) {
                                    SettingsDestinations.ABOUT -> SettingsAboutScreen(releaseViewModel, currentBack)
                                    SettingsDestinations.DEVELOPER -> showDeveloper(currentBack)
                                    else -> SettingsCategoryScreen(section, currentBack)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.settings_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    stringResource(R.string.settings_clear_search))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

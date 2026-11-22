package com.snapfree.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.snapfree.app.AppContainer
import com.snapfree.app.R
import com.snapfree.app.SharedLink
import com.snapfree.app.ui.downloads.DownloadsScreen
import com.snapfree.app.ui.downloads.DownloadsViewModel
import com.snapfree.app.ui.home.HomeEvent
import com.snapfree.app.ui.home.HomeScreen
import com.snapfree.app.ui.home.HomeViewModel
import com.snapfree.app.ui.settings.SettingsScreen
import com.snapfree.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.StateFlow

private enum class Destination(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Outlined.Download, Icons.Filled.Download),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun SnapFreeRoot(
    container: AppContainer,
    sharedLink: StateFlow<SharedLink?>,
    onSharedLinkConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(container) }
    val downloadsViewModel: DownloadsViewModel = viewModel { DownloadsViewModel(container) }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(container) }

    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val downloads by downloadsViewModel.state.collectAsStateWithLifecycle()
    val link by sharedLink.collectAsStateWithLifecycle()
    val homeEvent by homeViewModel.events.collectAsStateWithLifecycle()

    fun navigate(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A link shared from another app opens the home tab and starts the analysis right away.
    LaunchedEffect(link) {
        val shared = link ?: return@LaunchedEffect
        navigate(Destination.HOME)
        homeViewModel.analyze(shared.url)
        onSharedLinkConsumed()
    }

    val queuedMessage = stringResource(R.string.snackbar_queued)
    val viewAction = stringResource(R.string.action_view)
    LaunchedEffect(homeEvent) {
        if (homeEvent is HomeEvent.Enqueued) {
            homeViewModel.consumeEvent()
            val result = snackbarHostState.showSnackbar(queuedMessage, viewAction, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) navigate(Destination.DOWNLOADS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Destination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigate(destination) },
                        icon = {
                            val activeCount = downloads.active.size
                            if (destination == Destination.DOWNLOADS && activeCount > 0) {
                                BadgedBox(badge = { Badge { Text(activeCount.toString()) } }) {
                                    Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
                                }
                            } else {
                                Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            enterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f) },
            popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.97f) },
        ) {
            composable(Destination.HOME.route) { HomeScreen(homeViewModel, padding) }
            composable(Destination.DOWNLOADS.route) { DownloadsScreen(downloadsViewModel, padding) }
            composable(Destination.SETTINGS.route) { SettingsScreen(settingsViewModel, padding) }
        }
    }
}

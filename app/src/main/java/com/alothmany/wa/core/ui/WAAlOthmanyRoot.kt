package com.alothmany.wa.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.alothmany.wa.R
import com.alothmany.wa.core.navigation.Destination
import com.alothmany.wa.core.ui.theme.WAAlOthmanyTheme
import com.alothmany.wa.feature.dashboard.DashboardScreen
import com.alothmany.wa.feature.diagnostics.DiagnosticsScreen
import com.alothmany.wa.feature.groups.GroupsScreen
import com.alothmany.wa.feature.logs.LogsScreen
import com.alothmany.wa.feature.placeholder.FeaturePlaceholderScreen
import com.alothmany.wa.feature.results.ResultsScreen
import com.alothmany.wa.feature.settings.SettingsScreen
import com.alothmany.wa.feature.sync.SyncScreen
import com.alothmany.wa.feature.tasks.TasksScreen

private data class BottomItem(val destination: Destination, val label: Int, val icon: ImageVector)

@Composable
fun WAAlOthmanyRoot(viewModel: AppRootViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsState()
    WAAlOthmanyTheme(appTheme = prefs.theme) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val bottomRoutes = setOf("home", "groups", "tasks", "results", "settings")
        val items = listOf(
            BottomItem(Destination.Home, R.string.home, Icons.Rounded.Home),
            BottomItem(Destination.Groups, R.string.groups, Icons.Rounded.Groups),
            BottomItem(Destination.Tasks, R.string.tasks, Icons.Rounded.TaskAlt),
            BottomItem(Destination.Results, R.string.results, Icons.Rounded.QueryStats),
            BottomItem(Destination.Settings, R.string.settings, Icons.Rounded.Settings),
        )

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (route in bottomRoutes) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        items.forEach { item ->
                            val selected = route == item.destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, null) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.Home.route) {
                    DashboardScreen(onNavigate = navController::navigate)
                }
                composable(Destination.Groups.route) { GroupsScreen() }
                composable(Destination.Tasks.route) { TasksScreen() }
                composable(Destination.Results.route) { ResultsScreen() }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        onOpenLogs = { navController.navigate(Destination.Logs.route) },
                        onOpenDiagnostics = { navController.navigate(Destination.Diagnostics.route) },
                    )
                }
                composable(Destination.Logs.route) { LogsScreen(onBack = { navController.popBackStack() }) }
                composable(Destination.Diagnostics.route) { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
                composable(Destination.Sync.route) {
                    SyncScreen(
                        onBack = { navController.popBackStack() },
                        onNavigate = navController::navigate,
                    )
                }
                composable(Destination.Join.route) { FeaturePlaceholderScreen(R.string.join, Icons.Rounded.GroupAdd, onBack = { navController.popBackStack() }) }
                composable(Destination.Check.route) { FeaturePlaceholderScreen(R.string.check, Icons.Rounded.Policy, onBack = { navController.popBackStack() }) }
                composable(Destination.Extract.route) { FeaturePlaceholderScreen(R.string.extract, Icons.Rounded.Link, onBack = { navController.popBackStack() }) }
                composable(Destination.Publish.route) { FeaturePlaceholderScreen(R.string.publish, Icons.Rounded.Campaign, onBack = { navController.popBackStack() }) }
                composable(Destination.Delete.route) { FeaturePlaceholderScreen(R.string.delete, Icons.Rounded.DeleteSweep, onBack = { navController.popBackStack() }) }
            }
        }
    }
}

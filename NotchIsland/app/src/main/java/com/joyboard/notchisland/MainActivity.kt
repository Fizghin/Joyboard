package com.joyboard.notchisland

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.screens.AboutScreen
import com.joyboard.notchisland.ui.screens.AppearanceScreen
import com.joyboard.notchisland.ui.screens.BlockedAppsScreen
import com.joyboard.notchisland.ui.screens.FeaturesScreen
import com.joyboard.notchisland.ui.screens.GesturesScreen
import com.joyboard.notchisland.ui.screens.HomeScreen
import com.joyboard.notchisland.ui.components.UpdateDialog
import com.joyboard.notchisland.ui.theme.NotchIslandTheme
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPostNotifications()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val status by viewModel.status.collectAsStateWithLifecycle()
            NotchIslandTheme(themeMode = settings.themeMode) {
                NotchIslandApp(viewModel, status) { viewModel.clearStatus() }
                UpdateDialog(
                    state = updateState,
                    onInstall = { viewModel.installUpdate() },
                    onDismiss = { viewModel.dismissUpdate() },
                    onSkip = { viewModel.skipUpdate() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.refreshWeatherArea()
        viewModel.ensureServiceRunning()
        viewModel.maybeCheckForUpdates()
    }

    private fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.island, Icons.Outlined.Home),
    Tab("features", R.string.activities, Icons.Outlined.Bolt),
    Tab("appearance", R.string.look, Icons.Outlined.Palette),
    Tab("gestures", R.string.gestures, Icons.Outlined.TouchApp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotchIslandApp(
    viewModel: MainViewModel,
    status: String?,
    onStatusShown: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(status) {
        val message = status ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onStatusShown()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSubScreen = currentRoute == "blocked" || currentRoute == "about"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            "features" -> stringResource(R.string.live_activities)
                            "appearance" -> stringResource(R.string.appearance)
                            "gestures" -> stringResource(R.string.gestures)
                            "blocked" -> stringResource(R.string.per_app_rules)
                            "about" -> stringResource(R.string.about)
                            else -> stringResource(R.string.app_name)
                        }
                    )
                },
                navigationIcon = {
                    if (isSubScreen) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!isSubScreen) {
                        IconButton(onClick = { navController.navigate("about") }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isSubScreen) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                            label = { Text(stringResource(tab.label)) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(viewModel) { navController.navigate("appearance") }
                }
                composable("features") {
                    FeaturesScreen(viewModel) { navController.navigate("blocked") }
                }
                composable("appearance") { AppearanceScreen(viewModel) }
                composable("gestures") { GesturesScreen(viewModel) }
                composable("blocked") { BlockedAppsScreen(viewModel) }
                composable("about") { AboutScreen(viewModel) }
            }
        }
    }
}

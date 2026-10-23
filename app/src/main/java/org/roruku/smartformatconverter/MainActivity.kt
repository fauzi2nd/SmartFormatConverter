package org.roruku.smartformatconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.roruku.smartformatconverter.ui.converter.ConverterV4Screen
import org.roruku.smartformatconverter.ui.downloader.DownloaderScreen
import org.roruku.smartformatconverter.ui.home.HomeScreen
import org.roruku.smartformatconverter.ui.queue.QueueScreen
import org.roruku.smartformatconverter.ui.settings.SettingsScreen
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    object Converter : BottomNavItem("converter", "Converter", Icons.Default.Transform)
    object Downloader : BottomNavItem("downloader", "Download", Icons.Default.Download)
    object Queue : BottomNavItem("queue", "Queue", Icons.AutoMirrored.Filled.ListAlt)
    object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFormatConverterTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var editorVisible by remember { mutableStateOf(false) }

    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Converter,
        BottomNavItem.Downloader,
        BottomNavItem.Queue,
        BottomNavItem.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackBarHostState) },
        bottomBar = {
            if (!editorVisible) NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                navItems.forEach { item ->
                    val isSelected = currentRoute?.substringBefore("?") == item.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            // Always navigate to a canonical destination. In
                            // particular the converter route requires a tab
                            // argument, and restoring a prior back stack can
                            // unexpectedly reopen the converter from Home.
                            val destination = if (item == BottomNavItem.Converter) {
                                "converter?tab=0"
                            } else {
                                item.route
                            }
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                        label = { Text(item.title, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onNavigateToConverterTab = { tabIndex ->
                        navController.navigate("${BottomNavItem.Converter.route}?tab=$tabIndex") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDownloader = {
                        navController.navigate(BottomNavItem.Downloader.route)
                    }
                )
            }

            composable(
                route = "${BottomNavItem.Converter.route}?tab={tab}",
                arguments = listOf(navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                })
            ) { backStackEntry ->
                val tabIndex = backStackEntry.arguments?.getInt("tab") ?: 0
                ConverterV4Screen(
                    snackbarHostState = snackBarHostState,
                    initialTab = tabIndex,
                    onEditorVisibilityChanged = { editorVisible = it }
                )
            }

            composable(BottomNavItem.Downloader.route) {
                DownloaderScreen(
                    snackbarHostState = snackBarHostState
                )
            }

            composable(BottomNavItem.Queue.route) {
                QueueScreen()
            }

            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    snackbarHostState = snackBarHostState,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppScreenPreview() {
    SmartFormatConverterTheme {
        MainAppScreen()
    }
}


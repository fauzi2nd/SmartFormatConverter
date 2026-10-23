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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.roruku.smartformatconverter.ui.converter.ConverterScreen
import org.roruku.smartformatconverter.ui.downloader.DownloaderScreen
import org.roruku.smartformatconverter.ui.home.HomeScreen
import org.roruku.smartformatconverter.ui.queue.QueueScreen
import org.roruku.smartformatconverter.ui.settings.SettingsScreen
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    object Converter : BottomNavItem("converter", "Converter", Icons.Default.Transform)
    object Downloader : BottomNavItem("downloader", "Downloader", Icons.Default.Download)
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Converter,
        BottomNavItem.Downloader,
        BottomNavItem.Queue,
        BottomNavItem.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                navItems.forEach { item ->
                    val isSelected = currentRoute?.startsWith(item.route) == true
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
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
                        navController.navigate("${BottomNavItem.Converter.route}?tab=$tabIndex")
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
                ConverterScreen(
                    snackbarHostState = snackbarHostState,
                    initialTab = tabIndex
                )
            }

            composable(BottomNavItem.Downloader.route) {
                DownloaderScreen(
                    snackbarHostState = snackbarHostState
                )
            }

            composable(BottomNavItem.Queue.route) {
                QueueScreen()
            }

            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

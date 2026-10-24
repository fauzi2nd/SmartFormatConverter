package org.roruku.smartformatconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import org.roruku.smartformatconverter.ui.theme.PixelShiftSplash
import org.roruku.smartformatconverter.ui.components.TopToastHost

sealed class BottomNavItem(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Home : BottomNavItem("home", R.string.nav_home, Icons.Default.Home)
    object Converter : BottomNavItem("converter", R.string.nav_converter, Icons.Default.Transform)
    object Downloader : BottomNavItem("downloader", R.string.nav_downloader, Icons.Default.Download)
    object Queue : BottomNavItem("queue", R.string.nav_queue, Icons.AutoMirrored.Filled.ListAlt)
    object Settings : BottomNavItem("settings", R.string.nav_settings, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFormatConverterTheme {
                MainAppScreen(showBrandedIntro = savedInstanceState == null)
            }
        }
    }
}

@Composable
fun MainAppScreen(showBrandedIntro: Boolean = false) {
    val navController = rememberNavController()
    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var editorVisible by remember { mutableStateOf(false) }
    var brandVisible by rememberSaveable { mutableStateOf(showBrandedIntro) }
    LaunchedEffect(brandVisible) {
        if (brandVisible) {
            delay(1100L)
            brandVisible = false
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!editorVisible) {
                AppBottomNavigationBar(currentRoute = currentRoute) { item ->
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
                ConverterScreen(
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
    TopToastHost(
        hostState = snackBarHostState,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
    )
    AnimatedVisibility(
        visible = brandVisible,
        exit = fadeOut(animationSpec = tween(250)),
        modifier = Modifier.fillMaxSize()
    ) {
        PixelShiftSplash()
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



/** Stateless navigation bar shared between the app and Android Studio Preview. */
@Composable
fun AppBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (BottomNavItem) -> Unit
) {
    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Converter,
        BottomNavItem.Downloader,
        BottomNavItem.Queue,
        BottomNavItem.Settings
    )
    NavigationBar {
        navItems.forEach { item ->
            val title = stringResource(item.titleResId)
            NavigationBarItem(
                selected = currentRoute?.substringBefore("?") == item.route,
                onClick = { onNavigate(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = title) },
                label = { Text(title, maxLines = 1) },
                alwaysShowLabel = true
            )
        }
    }
}

@Preview(name = "Bottom Navigation - Converter", showBackground = true, widthDp = 393)
@Composable
fun BottomNavigationConverterPreview() {
    SmartFormatConverterTheme {
        AppBottomNavigationBar(currentRoute = "converter?tab={tab}", onNavigate = {})
    }
}

@Preview(name = "Bottom Navigation - Home", showBackground = true, widthDp = 393)
@Composable
fun BottomNavigationHomePreview() {
    SmartFormatConverterTheme {
        AppBottomNavigationBar(currentRoute = "home", onNavigate = {})
    }
}

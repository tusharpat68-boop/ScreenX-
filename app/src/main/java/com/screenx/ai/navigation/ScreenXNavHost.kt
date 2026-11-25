package com.screenx.ai.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.screenx.ai.presentation.editor.EditorPickerScreen
import com.screenx.ai.presentation.editor.EditorScreen
import com.screenx.ai.presentation.home.HomeScreen
import com.screenx.ai.presentation.library.RecordingsScreen
import com.screenx.ai.presentation.library.ScreenshotsScreen
import com.screenx.ai.presentation.settings.SettingsScreen

@Composable
fun ScreenXNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(220)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onNavigate = { navController.navigateToTopLevel(it.route) })
        }
        composable(Screen.Recordings.route) {
            RecordingsScreen(onEdit = { navController.navigate(Routes.edit(it.uri)) })
        }
        composable(Screen.Screenshots.route) { ScreenshotsScreen() }
        composable(Screen.Editor.route) {
            EditorPickerScreen(onPick = { navController.navigate(Routes.edit(it.uri)) })
        }
        composable(
            route = Routes.EDIT_PATTERN,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { entry ->
            val raw = entry.arguments?.getString("uri")
            if (raw != null) {
                EditorScreen(uri = Uri.parse(raw), onBack = { navController.popBackStack() })
            }
        }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}

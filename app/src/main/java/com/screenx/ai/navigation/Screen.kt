package com.screenx.ai.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.screenx.ai.R

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Home : Screen("home", R.string.nav_home, Icons.Rounded.Home)
    data object Recordings : Screen("recordings", R.string.nav_recordings, Icons.Rounded.VideoLibrary)
    data object Screenshots : Screen("screenshots", R.string.nav_screenshots, Icons.Rounded.Image)
    data object Editor : Screen("editor", R.string.nav_editor, Icons.Rounded.ContentCut)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Rounded.Settings)
}

/** Order shown in the bottom bar. Kept top-level to avoid sealed-class init-order issues. */
val bottomNavScreens: List<Screen> = listOf(
    Screen.Home, Screen.Recordings, Screen.Screenshots, Screen.Editor, Screen.Settings
)

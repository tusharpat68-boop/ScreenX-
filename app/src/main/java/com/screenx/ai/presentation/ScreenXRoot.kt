package com.screenx.ai.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.screenx.ai.navigation.Screen
import com.screenx.ai.navigation.ScreenXNavHost
import com.screenx.ai.navigation.navigateToTopLevel
import com.screenx.ai.presentation.components.ScreenXBottomBar
import com.screenx.ai.utils.QuickStart

/** Root composable: scaffold + bottom navigation + nav host. */
@Composable
fun ScreenXRoot() {
    val navController = rememberNavController()

    // A "start recording" request (quick tile or reminder) always lands on Home.
    val quickStart by QuickStart.request.collectAsStateWithLifecycle()
    LaunchedEffect(quickStart) {
        if (quickStart) navController.navigateToTopLevel(Screen.Home.route)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { ScreenXBottomBar(navController) }
    ) { innerPadding ->
        ScreenXNavHost(navController, Modifier.padding(innerPadding))
    }
}

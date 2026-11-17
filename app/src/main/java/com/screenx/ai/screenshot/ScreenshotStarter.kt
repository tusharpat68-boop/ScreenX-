package com.screenx.ai.screenshot

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.screenx.ai.permissions.PermissionHelper
import com.screenx.ai.utils.findActivity

/**
 * Returns a function that asks for screen-capture consent and starts a screenshot.
 * With a delay, the app moves to the background so you can open what you want to capture.
 */
@Composable
fun rememberScreenshotStarter(delaySec: Int): () -> Unit {
    val context = LocalContext.current
    val currentDelay by rememberUpdatedState(delaySec)

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenshotService.start(context, result.resultCode, data, currentDelay)
            if (currentDelay > 0) context.findActivity()?.moveTaskToBack(true)
        }
    }

    fun launchConsent() {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> launchConsent() }

    return {
        if (PermissionHelper.needsNotificationPermission(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchConsent()
        }
    }
}

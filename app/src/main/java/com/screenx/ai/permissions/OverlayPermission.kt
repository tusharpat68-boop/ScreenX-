package com.screenx.ai.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.screenx.ai.R

/**
 * Returns a function that makes sure the "display over other apps" permission is granted
 * (opening Android's settings page if not) and then calls [onGranted].
 * The permission is only requested when the user turns floating controls on.
 */
@Composable
fun rememberOverlayEnabler(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            currentOnGranted()
        } else {
            Toast.makeText(context, R.string.perm_overlay_denied, Toast.LENGTH_LONG).show()
        }
    }

    return {
        if (Settings.canDrawOverlays(context)) {
            currentOnGranted()
        } else {
            launcher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }
}

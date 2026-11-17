package com.screenx.ai.permissions

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.screenx.ai.R
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.service.RecordingService
import com.screenx.ai.utils.findActivity

/**
 * Returns a function that runs the full start flow:
 * 1) notification permission (Android 13+), 2) microphone permission if audio is selected,
 * 3) camera permission if the face camera is on, 4) system screen-capture consent, 5) start.
 * The consent dialog is always shown by Android and cannot be skipped. If the user denies an
 * optional permission, the recording still starts without that feature.
 */
@Composable
fun rememberRecordingStarter(
    audioSource: AudioSource,
    facecamEnabled: Boolean,
    moveToBackground: Boolean = false
): () -> Unit {
    val context = LocalContext.current
    val requestedAudio by rememberUpdatedState(audioSource)
    val requestedFacecam by rememberUpdatedState(facecamEnabled)
    val moveBack by rememberUpdatedState(moveToBackground)
    var effectiveAudio by remember { mutableStateOf(AudioSource.NONE) }
    var effectiveFacecam by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            RecordingService.start(context, result.resultCode, data, effectiveAudio, effectiveFacecam)
            // With a countdown, step aside so the user can open what they want to record.
            if (moveBack) context.findActivity()?.moveTaskToBack(true)
        }
    }

    fun launchConsent() {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            effectiveFacecam = false
            Toast.makeText(context, R.string.perm_camera_denied, Toast.LENGTH_LONG).show()
        }
        launchConsent()
    }

    fun continueAfterAudio() {
        if (effectiveFacecam && !PermissionHelper.hasCameraPermission(context)) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            launchConsent()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            effectiveAudio = AudioSource.NONE
            Toast.makeText(context, R.string.perm_audio_denied, Toast.LENGTH_LONG).show()
        }
        continueAfterAudio()
    }

    fun continueAfterNotifications() {
        effectiveAudio = requestedAudio
        effectiveFacecam = requestedFacecam && Settings.canDrawOverlays(context)
        if (requestedFacecam && !effectiveFacecam) {
            Toast.makeText(context, R.string.warn_facecam_overlay, Toast.LENGTH_LONG).show()
        }
        if (effectiveAudio != AudioSource.NONE && !PermissionHelper.hasAudioPermission(context)) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            continueAfterAudio()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, R.string.perm_notification_denied, Toast.LENGTH_LONG).show()
        }
        // Capture still works without the notification permission, so continue either way.
        continueAfterNotifications()
    }

    return {
        if (PermissionHelper.needsNotificationPermission(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueAfterNotifications()
        }
    }
}

package com.screenx.ai.presentation.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenx.ai.R
import com.screenx.ai.camera.CameraCapabilities
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.ThemeMode
import com.screenx.ai.domain.VideoOrientation
import com.screenx.ai.editor.ExportQuality
import com.screenx.ai.permissions.PermissionHelper
import com.screenx.ai.permissions.rememberOverlayEnabler
import com.screenx.ai.presentation.theme.AccentPresets
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.storage.StorageConfig
import com.screenx.ai.utils.LocaleHelper
import com.screenx.ai.utils.findActivity
import java.text.DateFormat
import java.util.Date

private enum class SettingsDialog { NONE, WATERMARK, FOLDER, PRIVACY, ABOUT }

private data class PermissionSnapshot(
    val microphone: Boolean,
    val camera: Boolean,
    val overlay: Boolean,
    val notifications: Boolean
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    val config by viewModel.config.collectAsStateWithLifecycle()
    val floating by viewModel.floating.collectAsStateWithLifecycle()
    val facecam by viewModel.facecam.collectAsStateWithLifecycle()
    val drawing by viewModel.drawingEnabled.collectAsStateWithLifecycle()
    val general by viewModel.general.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val cameras = remember { CameraCapabilities.available(context) }

    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }
    var language by remember { mutableStateOf(LocaleHelper.get(context)) }

    fun readPermissions() = PermissionSnapshot(
        microphone = PermissionHelper.hasAudioPermission(context),
        camera = PermissionHelper.hasCameraPermission(context),
        overlay = Settings.canDrawOverlays(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    )
    var perms by remember { mutableStateOf(readPermissions()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = readPermissions() }

    val enableFloating = rememberOverlayEnabler { viewModel.setFloatingEnabled(true) }
    val enableFacecam = rememberOverlayEnabler { viewModel.setFacecamEnabled(true) }
    val enableDrawing = rememberOverlayEnabler { viewModel.setDrawingEnabled(true) }
    val enableWatermark = rememberOverlayEnabler { viewModel.setWatermarkEnabled(true) }

    var pendingMinutes by remember { mutableStateOf<Int?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingMinutes?.let { viewModel.scheduleReminder(it) }
        pendingMinutes = null
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp)
        )

        // ---------------- Recording ----------------
        SettingsCard(stringResource(R.string.sec_recording)) {
            ChipRow(
                title = stringResource(R.string.home_resolution),
                items = options.resolutions,
                selected = config.resolutionP,
                labelOf = { stringResource(R.string.chip_resolution, it) },
                onSelect = viewModel::setResolution
            )
            ChipRow(
                title = stringResource(R.string.home_fps),
                items = options.fps,
                selected = config.fps,
                labelOf = { stringResource(R.string.chip_fps, it) },
                onSelect = viewModel::setFps
            )
            ChipRow(
                title = stringResource(R.string.set_bitrate),
                items = RecordingConfig.BITRATES_KBPS,
                selected = config.bitrateKbps,
                labelOf = {
                    if (it == 0) stringResource(R.string.bitrate_auto)
                    else stringResource(R.string.bitrate_mbps, it / 1000)
                },
                onSelect = viewModel::setBitrate
            )
            ChipRow(
                title = stringResource(R.string.set_orientation),
                items = VideoOrientation.values().toList(),
                selected = config.orientation,
                labelOf = {
                    stringResource(
                        when (it) {
                            VideoOrientation.AUTO -> R.string.orient_auto
                            VideoOrientation.PORTRAIT -> R.string.orient_portrait
                            VideoOrientation.LANDSCAPE -> R.string.orient_landscape
                        }
                    )
                },
                onSelect = viewModel::setOrientation
            )
            ChipRow(
                title = stringResource(R.string.home_audio),
                items = options.audioSources,
                selected = config.audioSource,
                labelOf = {
                    stringResource(
                        when (it) {
                            AudioSource.NONE -> R.string.audio_off
                            AudioSource.MICROPHONE -> R.string.audio_mic
                            AudioSource.INTERNAL -> R.string.audio_internal
                            AudioSource.BOTH -> R.string.audio_both
                        }
                    )
                },
                onSelect = viewModel::setAudioSource
            )
            if (config.audioSource != AudioSource.NONE) {
                ChipRow(
                    title = stringResource(R.string.home_audio_quality),
                    items = RecordingConfig.AUDIO_BITRATES,
                    selected = config.audioBitrateKbps,
                    labelOf = { stringResource(qualityLabel(it)) },
                    onSelect = viewModel::setAudioBitrate
                )
            }
            ChipRow(
                title = stringResource(R.string.set_countdown),
                items = RecordingConfig.COUNTDOWNS,
                selected = config.countdownSec,
                labelOf = {
                    if (it == 0) stringResource(R.string.audio_off) else stringResource(R.string.delay_seconds, it)
                },
                onSelect = viewModel::setCountdown
            )
            if (config.countdownSec > 0) Note(stringResource(R.string.set_countdown_note))
            ChipRow(
                title = stringResource(R.string.set_autostop),
                items = RecordingConfig.AUTO_STOPS,
                selected = config.autoStopMin,
                labelOf = {
                    if (it == 0) stringResource(R.string.audio_off) else stringResource(R.string.minutes_short, it)
                },
                onSelect = viewModel::setAutoStop
            )
        }

        // ---------------- Floating controls ----------------
        val floatingOn = floating.enabled && perms.overlay
        SettingsCard(stringResource(R.string.home_floating)) {
            SwitchRow(
                title = stringResource(R.string.floating_enable),
                checked = floatingOn,
                onChange = { on -> if (on) enableFloating() else viewModel.setFloatingEnabled(false) }
            )
            if (floatingOn) {
                ChipRow(
                    title = stringResource(R.string.floating_size),
                    items = FloatingConfig.SIZES,
                    selected = floating.sizeLevel,
                    labelOf = { stringResource(sizeLabel(it)) },
                    onSelect = viewModel::setFloatingSize
                )
                ChipRow(
                    title = stringResource(R.string.floating_opacity),
                    items = FloatingConfig.OPACITIES,
                    selected = floating.opacityPercent,
                    labelOf = { stringResource(R.string.chip_percent, it) },
                    onSelect = viewModel::setFloatingOpacity
                )
                SwitchRow(
                    title = stringResource(R.string.set_float_start_hidden),
                    subtitle = stringResource(R.string.set_float_start_hidden_sub),
                    checked = floating.startHidden,
                    onChange = viewModel::setFloatingStartHidden
                )
                OutlinedButton(
                    onClick = viewModel::resetFloatingPosition,
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text(stringResource(R.string.set_reset_position)) }
            }
        }

        // ---------------- Face camera ----------------
        val facecamOn = facecam.enabled && perms.overlay && cameras.isNotEmpty()
        SettingsCard(stringResource(R.string.home_facecam)) {
            SwitchRow(
                title = stringResource(
                    if (cameras.isEmpty()) R.string.facecam_no_camera else R.string.facecam_enable
                ),
                checked = facecamOn,
                enabled = cameras.isNotEmpty(),
                onChange = { on -> if (on) enableFacecam() else viewModel.setFacecamEnabled(false) }
            )
            if (facecamOn) {
                ChipRow(
                    title = stringResource(R.string.facecam_camera),
                    items = cameras,
                    selected = facecam.lens,
                    labelOf = {
                        stringResource(if (it == CameraLens.FRONT) R.string.lens_front else R.string.lens_back)
                    },
                    onSelect = viewModel::setFacecamLens
                )
                ChipRow(
                    title = stringResource(R.string.facecam_shape),
                    items = FacecamShape.values().toList(),
                    selected = facecam.shape,
                    labelOf = {
                        stringResource(
                            if (it == FacecamShape.CIRCLE) R.string.shape_circle else R.string.shape_rectangle
                        )
                    },
                    onSelect = viewModel::setFacecamShape
                )
                ChipRow(
                    title = stringResource(R.string.floating_size),
                    items = FacecamConfig.SIZES,
                    selected = facecam.sizeLevel,
                    labelOf = { stringResource(sizeLabel(it)) },
                    onSelect = viewModel::setFacecamSize
                )
                OutlinedButton(
                    onClick = viewModel::resetFacecamPosition,
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text(stringResource(R.string.set_reset_position)) }
            }
        }

        // ---------------- Drawing ----------------
        SettingsCard(stringResource(R.string.home_drawing)) {
            SwitchRow(
                title = stringResource(R.string.drawing_enable),
                checked = drawing && perms.overlay,
                onChange = { on -> if (on) enableDrawing() else viewModel.setDrawingEnabled(false) }
            )
        }

        // ---------------- Video and saving ----------------
        SettingsCard(stringResource(R.string.sec_video)) {
            ChipRow(
                title = stringResource(R.string.set_export_quality),
                items = ExportQuality.values().toList(),
                selected = runCatching { ExportQuality.valueOf(general.exportQuality) }
                    .getOrDefault(ExportQuality.HIGH),
                labelOf = {
                    stringResource(
                        when (it) {
                            ExportQuality.LOW -> R.string.quality_low
                            ExportQuality.MEDIUM -> R.string.quality_medium
                            ExportQuality.HIGH -> R.string.quality_high
                            ExportQuality.ORIGINAL -> R.string.quality_original
                        }
                    )
                },
                onSelect = { viewModel.setExportQuality(it.name) }
            )
            Text(
                text = stringResource(R.string.set_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.set_folder_value, general.saveFolder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScreenXAccent,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { dialog = SettingsDialog.FOLDER }) {
                    Text(stringResource(R.string.set_change))
                }
            }
            Note(stringResource(R.string.set_folder_note))

            SwitchRow(
                title = stringResource(R.string.set_watermark),
                subtitle = stringResource(R.string.set_watermark_sub),
                checked = general.watermarkEnabled && perms.overlay,
                onChange = { on -> if (on) enableWatermark() else viewModel.setWatermarkEnabled(false) }
            )
            if (general.watermarkEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = general.watermarkText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ScreenXAccent,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { dialog = SettingsDialog.WATERMARK }) {
                        Text(stringResource(R.string.set_change))
                    }
                }
                ChipRow(
                    title = stringResource(R.string.set_watermark_pos),
                    items = listOf(0, 1, 2, 3),
                    selected = general.watermarkCorner,
                    labelOf = {
                        stringResource(
                            when (it) {
                                0 -> R.string.corner_tl
                                1 -> R.string.corner_tr
                                2 -> R.string.corner_bl
                                else -> R.string.corner_br
                            }
                        )
                    },
                    onSelect = viewModel::setWatermarkCorner
                )
                Note(stringResource(R.string.set_watermark_note))
            }
        }

        // ---------------- Appearance ----------------
        SettingsCard(stringResource(R.string.sec_appearance)) {
            ChipRow(
                title = stringResource(R.string.set_theme),
                items = ThemeMode.values().toList(),
                selected = appearance.themeMode,
                labelOf = {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.DARK -> R.string.theme_dark
                            ThemeMode.LIGHT -> R.string.theme_light
                        }
                    )
                },
                onSelect = viewModel::setThemeMode
            )
            Text(
                text = stringResource(R.string.set_accent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccentPresets.forEachIndexed { index, preset ->
                    val selected = appearance.accentIndex == index
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(preset.primary, preset.secondary)))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                            .clickable { viewModel.setAccent(index) }
                    )
                }
            }
        }

        // ---------------- Language ----------------
        SettingsCard(stringResource(R.string.sec_language)) {
            ChipRow(
                title = stringResource(R.string.sec_language),
                items = listOf("system", "en", "bn"),
                selected = language,
                labelOf = {
                    when (it) {
                        "en" -> stringResource(R.string.lang_en)
                        "bn" -> stringResource(R.string.lang_bn)
                        else -> stringResource(R.string.lang_system)
                    }
                },
                onSelect = { lang ->
                    language = lang
                    LocaleHelper.set(context, lang)
                    context.findActivity()?.recreate()
                }
            )
        }

        // ---------------- Feel and sound ----------------
        SettingsCard(stringResource(R.string.sec_feel)) {
            SwitchRow(
                title = stringResource(R.string.set_haptics),
                subtitle = stringResource(R.string.set_haptics_sub),
                checked = general.hapticsEnabled,
                onChange = viewModel::setHaptics
            )
            SwitchRow(
                title = stringResource(R.string.set_sound),
                subtitle = stringResource(R.string.set_sound_sub),
                checked = general.soundEnabled,
                onChange = viewModel::setSound
            )
            if (general.soundEnabled) {
                ChipRow(
                    title = stringResource(R.string.set_sound_volume),
                    items = listOf(40, 70, 100),
                    selected = general.soundVolume,
                    labelOf = { stringResource(qualityLabel(if (it == 40) 64 else if (it == 70) 128 else 192)) },
                    onSelect = viewModel::setSoundVolume
                )
            }
            SwitchRow(
                title = stringResource(R.string.set_notif_pause),
                checked = general.notifPauseAction,
                onChange = viewModel::setNotifPause
            )
            Note(stringResource(R.string.set_tile_note))
        }

        // ---------------- Reminder ----------------
        SettingsCard(stringResource(R.string.sec_schedule)) {
            ChipRow(
                title = stringResource(R.string.set_schedule_in),
                items = listOf(5, 15, 30, 60),
                selected = -1,
                labelOf = { stringResource(R.string.minutes_short, it) },
                onSelect = { minutes ->
                    if (PermissionHelper.needsNotificationPermission(context)) {
                        pendingMinutes = minutes
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.scheduleReminder(minutes)
                    }
                }
            )
            val at = general.scheduledAtMs
            if (at > System.currentTimeMillis()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.set_schedule_set,
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ScreenXAccent,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = viewModel::cancelReminder) {
                        Text(stringResource(R.string.set_schedule_cancel))
                    }
                }
            }
            Note(stringResource(R.string.set_schedule_note))
        }

        // ---------------- Privacy dashboard ----------------
        SettingsCard(stringResource(R.string.sec_privacy)) {
            Note(stringResource(R.string.privacy_no_internet))
            PermissionRow(stringResource(R.string.priv_mic), perms.microphone) { openAppSettings() }
            PermissionRow(stringResource(R.string.priv_camera), perms.camera) { openAppSettings() }
            PermissionRow(stringResource(R.string.priv_overlay), perms.overlay) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            }
            PermissionRow(stringResource(R.string.priv_notifications), perms.notifications) { openAppSettings() }
            Note(stringResource(R.string.privacy_capture_note))
        }

        // ---------------- Device capabilities ----------------
        SettingsCard(stringResource(R.string.sec_device)) {
            capabilities.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ---------------- Other ----------------
        SettingsCard(stringResource(R.string.sec_other)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.set_version),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(version, style = MaterialTheme.typography.bodyLarge, color = ScreenXAccent)
            }
            Row(
                modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { dialog = SettingsDialog.PRIVACY }) {
                    Text(stringResource(R.string.set_privacy_policy))
                }
                OutlinedButton(onClick = { dialog = SettingsDialog.ABOUT }) {
                    Text(stringResource(R.string.set_about))
                }
                Button(onClick = {
                    val body = "ScreenX AI $version\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "${Build.MANUFACTURER} ${Build.MODEL}\n\n"
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
                        .putExtra(Intent.EXTRA_TEXT, body)
                    try {
                        context.startActivity(
                            Intent.createChooser(send, context.getString(R.string.feedback_chooser))
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.err_operation_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.set_feedback)) }
            }
        }
        Box(Modifier.height(16.dp))
    }

    // ---------------- dialogs ----------------
    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.WATERMARK -> TextInputDialog(
            title = stringResource(R.string.set_watermark_text),
            label = stringResource(R.string.set_watermark_text),
            initial = general.watermarkText,
            maxLength = 40,
            onConfirm = {
                viewModel.setWatermarkText(it)
                dialog = SettingsDialog.NONE
            },
            onDismiss = { dialog = SettingsDialog.NONE }
        )
        SettingsDialog.FOLDER -> TextInputDialog(
            title = stringResource(R.string.set_folder),
            label = stringResource(R.string.set_folder_hint),
            initial = general.saveFolder,
            maxLength = 30,
            onConfirm = {
                viewModel.setSaveFolder(StorageConfig.sanitize(it))
                dialog = SettingsDialog.NONE
            },
            onDismiss = { dialog = SettingsDialog.NONE }
        )
        SettingsDialog.PRIVACY -> InfoDialog(
            title = stringResource(R.string.set_privacy_policy),
            text = stringResource(R.string.privacy_policy_text),
            onClose = { dialog = SettingsDialog.NONE }
        )
        SettingsDialog.ABOUT -> InfoDialog(
            title = stringResource(R.string.set_about),
            text = stringResource(R.string.about_text, version),
            onClose = { dialog = SettingsDialog.NONE }
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onManage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(if (granted) R.string.priv_allowed else R.string.priv_off),
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) ScreenXAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onManage) { Text(stringResource(R.string.priv_manage)) }
    }
}

private fun qualityLabel(kbps: Int): Int = when (kbps) {
    64 -> R.string.audio_q_low
    128 -> R.string.audio_q_medium
    else -> R.string.audio_q_high
}

private fun sizeLabel(level: Int): Int = when (level) {
    0 -> R.string.size_small
    2 -> R.string.size_large
    3 -> R.string.size_xlarge
    else -> R.string.size_medium
}

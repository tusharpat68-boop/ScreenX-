package com.screenx.ai.presentation.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.screenx.ai.R
import com.screenx.ai.camera.CameraCapabilities
import com.screenx.ai.storage.RecordingStorage
import com.screenx.ai.utils.QuickStart
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.QualityOptions
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.RecordingState
import com.screenx.ai.domain.RecordingStatus
import com.screenx.ai.navigation.Screen
import com.screenx.ai.permissions.rememberOverlayEnabler
import com.screenx.ai.permissions.rememberRecordingStarter
import com.screenx.ai.presentation.components.GlassCard
import com.screenx.ai.presentation.components.ScreenXLogo
import com.screenx.ai.presentation.components.SectionHeader
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.presentation.theme.ScreenXGradients
import com.screenx.ai.presentation.theme.ScreenXPrimary
import com.screenx.ai.service.RecordingService
import com.screenx.ai.utils.AppConstants
import com.screenx.ai.utils.TimeFormat

@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val floating by viewModel.floating.collectAsStateWithLifecycle()
    val facecam by viewModel.facecam.collectAsStateWithLifecycle()
    val drawingEnabled by viewModel.drawingEnabled.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameras = remember { CameraCapabilities.available(context) }

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val enableFloating = rememberOverlayEnabler { viewModel.setFloatingEnabled(true) }
    val enableFacecam = rememberOverlayEnabler { viewModel.setFacecamEnabled(true) }
    val enableDrawing = rememberOverlayEnabler { viewModel.setDrawingEnabled(true) }
    val startRecording = rememberRecordingStarter(
        audioSource = config.audioSource,
        facecamEnabled = facecam.enabled && overlayGranted && cameras.isNotEmpty(),
        moveToBackground = config.countdownSec > 0
    )

    // "Record" from the quick settings tile or a reminder notification.
    val quickStart by QuickStart.request.collectAsStateWithLifecycle()
    LaunchedEffect(quickStart) {
        if (quickStart) {
            QuickStart.consume()
            if (!recording.isActive) startRecording()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        overlayGranted = Settings.canDrawOverlays(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeHeader()
        HeroCard(
            state = recording,
            onStart = startRecording,
            onPause = { RecordingService.send(context, RecordingService.ACTION_PAUSE) },
            onResume = { RecordingService.send(context, RecordingService.ACTION_RESUME) },
            onStop = { RecordingService.send(context, RecordingService.ACTION_STOP) }
        )
        if (recording.lastSavedName != null && recording.status == RecordingStatus.IDLE) {
            SavedCard(
                state = recording,
                onPlay = {
                    val uri = recording.lastSavedUri
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse(uri), "video/mp4")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.error_no_player, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = viewModel::dismissSaved
            )
        }
        QualityCard(
            config = config,
            options = options,
            enabled = !recording.isActive,
            onResolution = viewModel::setResolution,
            onFps = viewModel::setFps,
            onAudioSource = viewModel::setAudioSource,
            onAudioBitrate = viewModel::setAudioBitrate
        )
        FloatingCard(
            config = floating,
            overlayGranted = overlayGranted,
            enabled = !recording.isActive,
            onToggle = { on -> if (on) enableFloating() else viewModel.setFloatingEnabled(false) },
            onSize = viewModel::setFloatingSize,
            onOpacity = viewModel::setFloatingOpacity
        )
        FacecamCard(
            config = facecam,
            cameras = cameras,
            overlayGranted = overlayGranted,
            enabled = !recording.isActive,
            onToggle = { on -> if (on) enableFacecam() else viewModel.setFacecamEnabled(false) },
            onLens = viewModel::setFacecamLens,
            onShape = viewModel::setFacecamShape,
            onSize = viewModel::setFacecamSize
        )
        DrawingCard(
            enabledSetting = drawingEnabled,
            overlayGranted = overlayGranted,
            enabled = !recording.isActive,
            onToggle = { on -> if (on) enableDrawing() else viewModel.setDrawingEnabled(false) }
        )
        StorageCard(state)
        SectionHeader(stringResource(R.string.home_quick_access))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(Screen.Recordings, { onNavigate(Screen.Recordings) }, Modifier.weight(1f))
            QuickActionCard(Screen.Screenshots, { onNavigate(Screen.Screenshots) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(Screen.Editor, { onNavigate(Screen.Editor) }, Modifier.weight(1f))
            QuickActionCard(Screen.Settings, { onNavigate(Screen.Settings) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScreenXLogo(logoSize = 44.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = AppConstants.APP_NAME,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${AppConstants.EDITION} Edition",
                style = MaterialTheme.typography.labelLarge,
                color = ScreenXAccent
            )
        }
    }
}

@Composable
private fun HeroCard(
    state: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val idle = state.status == RecordingStatus.IDLE
    val running = state.status == RecordingStatus.RECORDING
    val paused = state.status == RecordingStatus.PAUSED
    val active = running || paused

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 0.08f,
                animationSpec = infiniteRepeatable(
                    tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha = if (paused) 0f else pulseAlpha
                        }
                        .background(if (running) Color(0xFFEF4444) else ScreenXPrimary, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable(enabled = idle, onClick = onStart)
                        .background(ScreenXGradients.brand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = stringResource(R.string.home_start),
                        tint = if (running) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                text = stringResource(
                    when (state.status) {
                        RecordingStatus.IDLE -> R.string.home_ready
                        RecordingStatus.STARTING -> R.string.home_starting
                        RecordingStatus.RECORDING -> R.string.home_recording
                        RecordingStatus.PAUSED -> R.string.home_paused
                        RecordingStatus.STOPPING -> R.string.home_stopping
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (state.countdownLeft > 0) {
                Text(
                    text = state.countdownLeft.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = ScreenXAccent,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedButton(onClick = onStop, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.action_stop))
                }
            }

            if (active) {
                Text(
                    text = TimeFormat.elapsed(state.elapsedMs),
                    style = MaterialTheme.typography.headlineLarge,
                    color = ScreenXAccent,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Button(onClick = if (paused) onResume else onPause) {
                        Text(stringResource(if (paused) R.string.action_resume else R.string.action_pause))
                    }
                    OutlinedButton(onClick = onStop) {
                        Text(stringResource(R.string.action_stop))
                    }
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.notice != null) {
                Text(
                    text = state.notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScreenXAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (idle) {
                Text(
                    text = stringResource(R.string.home_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedCard(state: RecordingState, onPlay: () -> Unit, onDismiss: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.saved_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = state.lastSavedName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = ScreenXAccent,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stringResource(R.string.saved_location, RecordingStorage.FOLDER)
            } else {
                stringResource(R.string.saved_location_legacy)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Button(onClick = onPlay, enabled = state.lastSavedUri != null) {
                Text(stringResource(R.string.action_play))
            }
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun QualityCard(
    config: RecordingConfig,
    options: QualityOptions,
    enabled: Boolean,
    onResolution: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onAudioSource: (AudioSource) -> Unit,
    onAudioBitrate: (Int) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_quality),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        ChipRow(
            title = stringResource(R.string.home_resolution),
            items = options.resolutions,
            selected = config.resolutionP,
            enabled = enabled,
            labelOf = { stringResource(R.string.chip_resolution, it) },
            onSelect = onResolution
        )
        ChipRow(
            title = stringResource(R.string.home_fps),
            items = options.fps,
            selected = config.fps,
            enabled = enabled,
            labelOf = { stringResource(R.string.chip_fps, it) },
            onSelect = onFps
        )
        ChipRow(
            title = stringResource(R.string.home_audio),
            items = options.audioSources,
            selected = config.audioSource,
            enabled = enabled,
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
            onSelect = onAudioSource
        )
        if (config.audioSource != AudioSource.NONE) {
            ChipRow(
                title = stringResource(R.string.home_audio_quality),
                items = RecordingConfig.AUDIO_BITRATES,
                selected = config.audioBitrateKbps,
                enabled = enabled,
                labelOf = {
                    stringResource(
                        when (it) {
                            64 -> R.string.audio_q_low
                            128 -> R.string.audio_q_medium
                            else -> R.string.audio_q_high
                        }
                    )
                },
                onSelect = onAudioBitrate
            )
        }
        if (config.audioSource.usesInternal) {
            Text(
                text = stringResource(R.string.audio_internal_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun FloatingCard(
    config: FloatingConfig,
    overlayGranted: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSize: (Int) -> Unit,
    onOpacity: (Int) -> Unit
) {
    val on = config.enabled && overlayGranted
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_floating),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.floating_enable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = on, onCheckedChange = onToggle, enabled = enabled)
        }
        if (on) {
            ChipRow(
                title = stringResource(R.string.floating_size),
                items = FloatingConfig.SIZES,
                selected = config.sizeLevel,
                enabled = enabled,
                labelOf = {
                    stringResource(
                        when (it) {
                            0 -> R.string.size_small
                            2 -> R.string.size_large
                            else -> R.string.size_medium
                        }
                    )
                },
                onSelect = onSize
            )
            ChipRow(
                title = stringResource(R.string.floating_opacity),
                items = FloatingConfig.OPACITIES,
                selected = config.opacityPercent,
                enabled = enabled,
                labelOf = { stringResource(R.string.chip_percent, it) },
                onSelect = onOpacity
            )
            Text(
                text = stringResource(R.string.floating_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun FacecamCard(
    config: FacecamConfig,
    cameras: List<CameraLens>,
    overlayGranted: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onLens: (CameraLens) -> Unit,
    onShape: (FacecamShape) -> Unit,
    onSize: (Int) -> Unit
) {
    val hasCamera = cameras.isNotEmpty()
    val on = config.enabled && overlayGranted && hasCamera
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_facecam),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (hasCamera) R.string.facecam_enable else R.string.facecam_no_camera
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = on, onCheckedChange = onToggle, enabled = enabled && hasCamera)
        }
        if (on) {
            ChipRow(
                title = stringResource(R.string.facecam_camera),
                items = cameras,
                selected = config.lens,
                enabled = enabled,
                labelOf = {
                    stringResource(if (it == CameraLens.FRONT) R.string.lens_front else R.string.lens_back)
                },
                onSelect = onLens
            )
            ChipRow(
                title = stringResource(R.string.facecam_shape),
                items = FacecamShape.values().toList(),
                selected = config.shape,
                enabled = enabled,
                labelOf = {
                    stringResource(
                        if (it == FacecamShape.CIRCLE) R.string.shape_circle else R.string.shape_rectangle
                    )
                },
                onSelect = onShape
            )
            ChipRow(
                title = stringResource(R.string.floating_size),
                items = FacecamConfig.SIZES,
                selected = config.sizeLevel,
                enabled = enabled,
                labelOf = {
                    stringResource(
                        when (it) {
                            0 -> R.string.size_small
                            2 -> R.string.size_large
                            3 -> R.string.size_xlarge
                            else -> R.string.size_medium
                        }
                    )
                },
                onSelect = onSize
            )
            Text(
                text = stringResource(R.string.facecam_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DrawingCard(
    enabledSetting: Boolean,
    overlayGranted: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val on = enabledSetting && overlayGranted
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_drawing),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.drawing_enable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = on, onCheckedChange = onToggle, enabled = enabled)
        }
        if (on) {
            Text(
                text = stringResource(R.string.drawing_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun <T> ChipRow(
    title: String,
    items: List<T>,
    selected: T,
    enabled: Boolean,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                enabled = enabled,
                label = { Text(labelOf(item)) }
            )
        }
    }
}

@Composable
private fun StorageCard(state: HomeUiState) {
    val context = LocalContext.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Storage, contentDescription = null, tint = ScreenXAccent)
            Text(
                text = stringResource(R.string.home_storage),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LinearProgressIndicator(
            progress = { state.usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(50)),
            color = ScreenXPrimary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        Text(
            text = stringResource(
                R.string.home_storage_free,
                Formatter.formatShortFileSize(context, state.freeBytes),
                Formatter.formatShortFileSize(context, state.totalBytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun QuickActionCard(screen: Screen, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, onClick = onClick) {
        QuickActionIcon(screen.icon)
        Text(
            text = stringResource(
                if (screen == Screen.Editor) R.string.title_editor else screen.labelRes
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun QuickActionIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ScreenXGradients.brand),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

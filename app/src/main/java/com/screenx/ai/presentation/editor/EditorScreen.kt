package com.screenx.ai.presentation.editor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.screenx.ai.R
import com.screenx.ai.editor.CropPreset
import com.screenx.ai.editor.EditEffects
import com.screenx.ai.editor.ExportQuality
import com.screenx.ai.editor.ExportState
import com.screenx.ai.presentation.components.GlassCard
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.utils.TimeFormat
import kotlinx.coroutines.delay
import kotlin.math.max

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(uri) { viewModel.load(uri) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val busy = ui.export is ExportState.Running

    // ---- preview player ----
    val exo = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(exo) { onDispose { exo.release() } }
    LaunchedEffect(uri) {
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
    }
    val previewEffects = remember(ui.width, ui.height, ui.rotationCw, ui.crop) {
        EditEffects.preview(ui.width, ui.height, ui.rotationCw, ui.crop)
    }
    LaunchedEffect(previewEffects) { exo.setVideoEffects(previewEffects) }

    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val startMs by rememberUpdatedState(ui.trimStartMs)
    val endMs by rememberUpdatedState(ui.trimEndMs)
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition
            playing = exo.isPlaying
            // Only play the part you kept.
            if (exo.isPlaying && endMs > 0 && position >= endMs) {
                exo.pause()
                exo.seekTo(startMs)
            }
            delay(100)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.editor_back))
            }
            Text(
                text = stringResource(R.string.title_editor),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when {
            ui.loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Text(
                text = stringResource(ui.error ?: R.string.editor_error_load),
                color = MaterialTheme.colorScheme.error
            )
            else -> {
                // Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exo
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Transport + position
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (playing) {
                            exo.pause()
                        } else {
                            if (position >= endMs || position < startMs) exo.seekTo(startMs)
                            exo.play()
                        }
                    }) {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playing) R.string.action_pause else R.string.editor_play
                            )
                        )
                    }
                    Text(
                        text = "${TimeFormat.elapsed(position)} / ${TimeFormat.elapsed(ui.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Slider(
                    value = position.coerceIn(0L, ui.durationMs).toFloat(),
                    onValueChange = {
                        exo.seekTo(it.toLong())
                        position = it.toLong()
                    },
                    valueRange = 0f..max(1f, ui.durationMs.toFloat())
                )

                // Trim
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.editor_trim),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    RangeSlider(
                        value = ui.trimStartMs.toFloat()..ui.trimEndMs.toFloat(),
                        onValueChange = { range ->
                            viewModel.setTrim(range.start.toLong(), range.endInclusive.toLong())
                        },
                        onValueChangeFinished = { exo.seekTo(ui.trimStartMs) },
                        valueRange = 0f..max(1f, ui.durationMs.toFloat()),
                        enabled = !busy
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.editor_start) + " " + TimeFormat.elapsed(ui.trimStartMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.editor_end) + " " + TimeFormat.elapsed(ui.trimEndMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.editor_length, TimeFormat.elapsed(ui.trimEndMs - ui.trimStartMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScreenXAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setTrim(position, ui.trimEndMs) },
                            enabled = !busy
                        ) { Text(stringResource(R.string.editor_set_start)) }
                        OutlinedButton(
                            onClick = { viewModel.setTrim(ui.trimStartMs, position) },
                            enabled = !busy
                        ) { Text(stringResource(R.string.editor_set_end)) }
                    }
                }

                // Rotate + crop
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.editor_rotate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedButton(onClick = { viewModel.rotate(-90) }, enabled = !busy) {
                            Icon(Icons.Rounded.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.editor_rotate_left), modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { viewModel.rotate(90) }, enabled = !busy) {
                            Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.editor_rotate_right), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    OptionRow(
                        title = stringResource(R.string.editor_crop),
                        items = CropPreset.values().toList(),
                        selected = ui.crop,
                        enabled = !busy,
                        labelOf = {
                            stringResource(
                                when (it) {
                                    CropPreset.ORIGINAL -> R.string.crop_original
                                    CropPreset.SQUARE -> R.string.crop_square
                                    CropPreset.WIDE -> R.string.crop_wide
                                    CropPreset.TALL -> R.string.crop_tall
                                    CropPreset.CLASSIC -> R.string.crop_classic
                                    CropPreset.PORTRAIT -> R.string.crop_portrait
                                }
                            )
                        },
                        onSelect = viewModel::setCrop
                    )
                }

                // Quality + export
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    OptionRow(
                        title = stringResource(R.string.editor_quality),
                        items = ExportQuality.values().toList(),
                        selected = ui.quality,
                        enabled = !busy,
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
                        onSelect = viewModel::setQuality
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Button(onClick = viewModel::exportVideo, enabled = !busy) {
                            Text(stringResource(R.string.editor_export))
                        }
                        OutlinedButton(onClick = viewModel::extractAudio, enabled = !busy && ui.hasAudio) {
                            Text(stringResource(R.string.editor_extract_audio))
                        }
                    }
                    if (!ui.hasAudio) {
                        Text(
                            text = stringResource(R.string.editor_no_audio),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.editor_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Export status
                when (val export = ui.export) {
                    ExportState.Idle -> Unit
                    is ExportState.Running -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(
                                if (export.audioOnly) R.string.editor_extracting else R.string.editor_exporting,
                                export.progress
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { export.progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedButton(
                            onClick = viewModel::cancelExport,
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(stringResource(R.string.dialog_cancel)) }
                    }
                    is ExportState.Done -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(
                                if (export.audioOnly) R.string.editor_saved_audio else R.string.editor_saved_video,
                                export.name
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            if (export.uri != null) {
                                Button(onClick = {
                                    val mime = if (export.audioOnly) "audio/mp4" else "video/mp4"
                                    val intent = Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(Uri.parse(export.uri), mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, R.string.error_no_player, Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text(stringResource(R.string.action_play)) }
                            }
                            OutlinedButton(onClick = viewModel::dismissResult) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        }
                    }
                    is ExportState.Failed -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.editor_failed, export.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = viewModel::dismissResult,
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(stringResource(R.string.action_dismiss)) }
                    }
                }
            }
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun <T> OptionRow(
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

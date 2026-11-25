package com.screenx.ai.presentation.editor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.screenx.ai.R
import com.screenx.ai.editor.Adjust
import com.screenx.ai.editor.CropPreset
import com.screenx.ai.editor.EditEffects
import com.screenx.ai.editor.EditTimeline
import com.screenx.ai.editor.EditorUiState
import com.screenx.ai.editor.ExportQuality
import com.screenx.ai.editor.ExportState
import com.screenx.ai.editor.FilterPreset
import com.screenx.ai.editor.LookSettings
import com.screenx.ai.editor.Segment
import com.screenx.ai.editor.TextItem
import com.screenx.ai.utils.TimeFormat
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

// A dark, CapCut-like look that does not depend on the app theme.
private val CcBg = Color(0xFF0A0A0C)
private val CcPanel = Color(0xFF161618)
private val CcChip = Color(0xFF2A2A2E)
private val CcAccent = Color(0xFF25F4EE)
private val CcMuted = Color(0xFF9A9AA2)
private val CcLane = Color(0xFF131316)
private val CcTextClip = Color(0xFF7C4DFF)
private val CcAudioClip = Color(0xFF1FA39E)

/** How wide one second of the timeline is. */
private const val DP_PER_SEC = 44f

private enum class Tool { NONE, SPEED, VOLUME, FILTERS, ADJUST, TEXT, CANVAS }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFE600, 0xFFFF3B30, 0xFF25F4EE, 0xFFFF2D95, 0xFF34C759, 0xFFFF9500
).map { it.toInt() }

private fun speedLabel(v: Float): String =
    if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel()
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CcAccent,
            onPrimary = Color.Black,
            background = CcBg,
            onBackground = Color.White,
            surface = CcPanel,
            onSurface = Color.White,
            surfaceVariant = CcChip,
            onSurfaceVariant = CcMuted,
            secondaryContainer = Color(0xFF3A3A40),
            onSecondaryContainer = Color.White,
            outline = Color(0xFF4A4A52),
            error = Color(0xFFFF6B6B)
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        EditorContent(uri, onBack, modifier, viewModel)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EditorContent(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    LaunchedEffect(uri) { viewModel.load(uri) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val thumbs by viewModel.thumbs.collectAsStateWithLifecycle()
    val busy = ui.export is ExportState.Running

    // ---- preview player ----
    val exo = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(exo) { onDispose { exo.release() } }
    LaunchedEffect(uri) {
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
    }
    val previewEffects = remember(ui.width, ui.height, ui.rotationCw, ui.crop, ui.filter, ui.adjust, ui.texts) {
        EditEffects.preview(
            ui.width,
            ui.height,
            LookSettings(ui.rotationCw, ui.crop, ui.filter, ui.adjust, ui.texts)
        )
    }
    LaunchedEffect(previewEffects) { exo.setVideoEffects(previewEffects) }

    // Position in the SOURCE video. The timeline position is worked out from it.
    var positionMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val segs by rememberUpdatedState(ui.segments)

    LaunchedEffect(exo) {
        while (true) {
            val list = segs
            if (list.isNotEmpty()) {
                val pos = exo.currentPosition
                var idx = EditTimeline.indexAtSource(list, pos)
                if (exo.isPlaying && idx == -1) {
                    // In a part that was cut out: jump to the next clip, or stop at the end.
                    val next = list.indexOfFirst { it.startMs > pos }
                    if (next == -1) {
                        exo.pause()
                        exo.seekTo(list.last().endMs)
                    } else {
                        exo.seekTo(list[next].startMs)
                        idx = next
                    }
                }
                if (idx != -1) {
                    val s = list[idx]
                    if (exo.playbackParameters.speed != s.speed) exo.setPlaybackSpeed(s.speed)
                    val v = min(1f, s.volume)
                    if (exo.volume != v) exo.volume = v
                }
                positionMs = exo.currentPosition
            }
            playing = exo.isPlaying
            delay(40)
        }
    }

    val totalMs = EditTimeline.total(ui.segments)
    val timelineMs = EditTimeline.timelineOfSource(ui.segments, positionMs)
    val headIdx = EditTimeline.locate(ui.segments, timelineMs)?.index
    val target: Segment? = ui.segments.firstOrNull { it.id == ui.selectedId }
        ?: headIdx?.let { ui.segments.getOrNull(it) }

    fun seekTimeline(tl: Long) {
        val loc = EditTimeline.locate(segs, tl) ?: return
        if (exo.isPlaying) exo.pause()
        exo.seekTo(loc.sourceMs)
        positionMs = loc.sourceMs
    }

    fun togglePlay() {
        if (exo.isPlaying) {
            exo.pause()
            return
        }
        val list = segs
        if (list.isEmpty()) return
        val pos = exo.currentPosition
        val tl = EditTimeline.timelineOfSource(list, pos)
        if (tl >= EditTimeline.total(list) - 60L) {
            exo.seekTo(list.first().startMs)
        } else if (EditTimeline.indexAtSource(list, pos) == -1) {
            val next = list.firstOrNull { it.startMs > pos } ?: list.first()
            exo.seekTo(next.startMs)
        }
        exo.play()
    }

    var tool by remember { mutableStateOf(Tool.NONE) }
    var showExport by remember { mutableStateOf(false) }
    fun closeTool() {
        viewModel.endGesture()
        tool = Tool.NONE
    }

    val splitFailText = stringResource(R.string.ed_split_fail)
    val deleteFailText = stringResource(R.string.ed_delete_fail)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CcBg)
            .imePadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back),
                    tint = Color.White
                )
            }
            Text(
                text = ui.title,
                color = CcMuted,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            Button(
                onClick = {
                    exo.pause()
                    showExport = true
                },
                enabled = !busy && !ui.loading && ui.error == null,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CcAccent, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.ed_export), fontWeight = FontWeight.Bold)
            }
        }

        when {
            ui.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CcAccent)
            }
            ui.error != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(ui.error ?: R.string.editor_error_load),
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                // Preview (tap to play / pause)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .clickable { togglePlay() }
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
                    if (!playing) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.editor_play),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                                .background(Color(0x66000000), CircleShape)
                                .padding(8.dp)
                        )
                    }
                }

                // Time, transport, undo / redo
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${TimeFormat.elapsed(timelineMs)} / ${TimeFormat.elapsed(totalMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { seekTimeline(0L) }) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = { togglePlay() }) {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playing) R.string.action_pause else R.string.editor_play
                            ),
                            tint = Color.Black,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White, CircleShape)
                                .padding(6.dp)
                        )
                    }
                    IconButton(onClick = { seekTimeline(totalMs) }) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { viewModel.undo() }, enabled = ui.canUndo && !busy) {
                            Icon(
                                Icons.Rounded.Undo,
                                contentDescription = stringResource(R.string.ed_undo),
                                tint = if (ui.canUndo) Color.White else CcChip
                            )
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = ui.canRedo && !busy) {
                            Icon(
                                Icons.Rounded.Redo,
                                contentDescription = stringResource(R.string.ed_redo),
                                tint = if (ui.canRedo) Color.White else CcChip
                            )
                        }
                    }
                }

                // Timeline
                Timeline(
                    segments = ui.segments,
                    texts = ui.texts,
                    hasAudio = ui.hasAudio,
                    thumbs = thumbs,
                    sourceDurationMs = ui.durationMs,
                    selectedId = ui.selectedId,
                    timelineMs = timelineMs,
                    onSeek = { seekTimeline(it) },
                    onSelect = { id -> viewModel.select(if (ui.selectedId == id) null else id) },
                    onTrim = { id, start, end -> viewModel.trimSegment(id, start, end) },
                    onTrimPreview = { src ->
                        if (exo.isPlaying) exo.pause()
                        exo.seekTo(src)
                        positionMs = src
                    },
                    onTrimEnd = { viewModel.endGesture() },
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Toolbar or the panel of the open tool
                if (tool == Tool.NONE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CcPanel)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ToolButton(Icons.Rounded.ContentCut, stringResource(R.string.tool_split), !busy) {
                            if (!viewModel.split(timelineMs)) {
                                Toast.makeText(context, splitFailText, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ToolButton(Icons.Rounded.Delete, stringResource(R.string.tool_delete), !busy) {
                            val id = target?.id
                            if (id == null || !viewModel.deleteSegment(id)) {
                                Toast.makeText(context, deleteFailText, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ToolButton(Icons.Rounded.Speed, stringResource(R.string.tool_speed), !busy) { tool = Tool.SPEED }
                        ToolButton(Icons.Rounded.VolumeUp, stringResource(R.string.tool_volume), !busy) { tool = Tool.VOLUME }
                        ToolButton(Icons.Rounded.AutoAwesome, stringResource(R.string.tool_filters), !busy) { tool = Tool.FILTERS }
                        ToolButton(Icons.Rounded.Tune, stringResource(R.string.tool_adjust), !busy) { tool = Tool.ADJUST }
                        ToolButton(Icons.Rounded.TextFields, stringResource(R.string.tool_text), !busy) { tool = Tool.TEXT }
                        ToolButton(Icons.Rounded.Crop, stringResource(R.string.tool_canvas), !busy) { tool = Tool.CANVAS }
                    }
                } else {
                    ToolPanel(
                        title = stringResource(
                            when (tool) {
                                Tool.SPEED -> R.string.tool_speed
                                Tool.VOLUME -> R.string.tool_volume
                                Tool.FILTERS -> R.string.tool_filters
                                Tool.ADJUST -> R.string.tool_adjust
                                Tool.TEXT -> R.string.tool_text
                                else -> R.string.tool_canvas
                            }
                        ),
                        onDone = { closeTool() }
                    ) {
                        when (tool) {
                            Tool.SPEED -> SpeedPanel(target) { id, v -> viewModel.setSpeed(id, v) }
                            Tool.VOLUME -> VolumePanel(target, ui.hasAudio, viewModel)
                            Tool.FILTERS -> FiltersPanel(ui.filter) { viewModel.setFilter(it) }
                            Tool.ADJUST -> AdjustPanel(ui.adjust, viewModel)
                            Tool.TEXT -> TextPanel(ui, viewModel)
                            Tool.CANVAS -> CanvasPanel(ui, viewModel)
                            Tool.NONE -> Unit
                        }
                    }
                }
            }
        }
    }

    // ---- export dialogs ----
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text(stringResource(R.string.ed_export)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.editor_quality),
                        color = CcMuted,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportQuality.values().forEach { q ->
                            FilterChip(
                                selected = q == ui.quality,
                                onClick = { viewModel.setQuality(q) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (q) {
                                                ExportQuality.LOW -> R.string.quality_low
                                                ExportQuality.MEDIUM -> R.string.quality_medium
                                                ExportQuality.HIGH -> R.string.quality_high
                                                ExportQuality.ORIGINAL -> R.string.quality_original
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showExport = false
                            viewModel.extractAudio()
                        },
                        enabled = ui.hasAudio,
                        modifier = Modifier.padding(top = 14.dp)
                    ) { Text(stringResource(R.string.editor_extract_audio)) }
                    if (!ui.hasAudio) {
                        Text(
                            text = stringResource(R.string.editor_no_audio),
                            color = CcMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.editor_note),
                        color = CcMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExport = false
                        viewModel.exportVideo()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CcAccent, contentColor = Color.Black)
                ) { Text(stringResource(R.string.editor_export)) }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    when (val export = ui.export) {
        ExportState.Idle -> Unit
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    stringResource(
                        if (export.audioOnly) R.string.editor_extracting else R.string.editor_exporting,
                        export.progress
                    )
                )
            },
            text = {
                LinearProgressIndicator(
                    progress = { export.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = CcAccent
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelExport) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
        is ExportState.Done -> AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            text = {
                Text(
                    stringResource(
                        if (export.audioOnly) R.string.editor_saved_audio else R.string.editor_saved_video,
                        export.name
                    )
                )
            },
            confirmButton = {
                if (export.uri != null) {
                    Button(
                        onClick = {
                            val mime = if (export.audioOnly) "audio/mp4" else "video/mp4"
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(Uri.parse(export.uri), mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.error_no_player, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CcAccent, contentColor = Color.Black)
                    ) { Text(stringResource(R.string.action_play)) }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.action_dismiss)) }
            }
        )
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            text = {
                Text(
                    text = stringResource(R.string.editor_failed, export.message),
                    color = MaterialTheme.colorScheme.error
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.action_dismiss)) }
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------------------------

private val RULER_H = 20.dp
private val MAIN_H = 52.dp
private val LANE_H = 24.dp
private val LANE_GAP = 4.dp

@Composable
private fun Timeline(
    segments: List<Segment>,
    texts: List<TextItem>,
    hasAudio: Boolean,
    thumbs: List<Bitmap?>,
    sourceDurationMs: Long,
    selectedId: Long?,
    timelineMs: Long,
    onSeek: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onTrim: (Long, Long, Long) -> Unit,
    onTrimPreview: (Long) -> Unit,
    onTrimEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { DP_PER_SEC.dp.toPx() } / 1000f
    val total = EditTimeline.total(segments)
    val scroll = rememberScrollState()
    val seekNow by rememberUpdatedState(onSeek)
    val totalNow by rememberUpdatedState(total)
    val images = remember(thumbs) { thumbs.map { it?.asImageBitmap() } }
    // True while the strip is being moved by the player (not by a finger).
    val followFlag = remember { BooleanArray(1) }

    // Keep the strip under the playhead while playing or after a seek.
    LaunchedEffect(timelineMs, total) {
        if (!scroll.isScrollInProgress) {
            followFlag[0] = true
            try {
                scroll.scrollTo((timelineMs * pxPerMs).roundToInt())
            } finally {
                followFlag[0] = false
            }
        }
    }
    // A finger moving the strip moves the video.
    LaunchedEffect(scroll, pxPerMs) {
        snapshotFlow { scroll.value }.collect { v ->
            if (!followFlag[0] && scroll.isScrollInProgress) {
                seekNow((v / pxPerMs).toLong().coerceIn(0L, totalNow))
            }
        }
    }

    val contentHeight = RULER_H + MAIN_H + LANE_H * 2 + LANE_GAP * 3
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(contentHeight)) {
        val half = maxWidth / 2
        val contentWidth = (total / 1000f * DP_PER_SEC).dp
        Row(
            modifier = Modifier.fillMaxHeight().horizontalScroll(scroll)
        ) {
            Spacer(Modifier.width(half))
            Column(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
                TimeRuler(total, Modifier.fillMaxWidth().height(RULER_H))
                Spacer(Modifier.height(LANE_GAP))
                // Main video track
                Row(modifier = Modifier.height(MAIN_H)) {
                    segments.forEach { seg ->
                        key(seg.id) {
                            SegmentBlock(
                                seg = seg,
                                images = images,
                                sourceDurationMs = sourceDurationMs,
                                selected = seg.id == selectedId,
                                pxPerMs = pxPerMs,
                                onSelect = { onSelect(seg.id) },
                                onTrim = { start, end -> onTrim(seg.id, start, end) },
                                onTrimPreview = onTrimPreview,
                                onTrimEnd = onTrimEnd
                            )
                        }
                    }
                }
                Spacer(Modifier.height(LANE_GAP))
                TextLane(texts, Modifier.fillMaxWidth().height(LANE_H))
                Spacer(Modifier.height(LANE_GAP))
                AudioLane(segments, hasAudio, Modifier.fillMaxWidth().height(LANE_H))
            }
            Spacer(Modifier.width(half))
        }

        // Track icons stay at the left edge while the clips slide underneath.
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Spacer(Modifier.height(RULER_H + LANE_GAP))
            TrackIcon(Icons.Rounded.Videocam, MAIN_H)
            Spacer(Modifier.height(LANE_GAP))
            TrackIcon(Icons.Rounded.TextFields, LANE_H)
            Spacer(Modifier.height(LANE_GAP))
            TrackIcon(Icons.Rounded.MusicNote, LANE_H)
        }

        // The playhead stays in the middle, the clips slide under it.
        Box(
            Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(10.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
private fun TrackIcon(icon: ImageVector, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(height)
            .background(CcBg.copy(alpha = 0.85f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = CcMuted, modifier = Modifier.size(16.dp))
    }
}

/** Time marks: a small tick every half second, a label every two seconds. */
@Composable
private fun TimeRuler(totalMs: Long, modifier: Modifier = Modifier) {
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A9AA2.toInt() }
    }
    Canvas(modifier = modifier) {
        val pxPerSec = DP_PER_SEC.dp.toPx()
        paint.textSize = 9.sp.toPx()
        val halfSteps = (totalMs / 500L).toInt()
        for (i in 0..halfSteps) {
            val x = i * 0.5f * pxPerSec
            val tick = if (i % 2 == 0) 7.dp.toPx() else 4.dp.toPx()
            drawLine(
                color = CcMuted,
                start = Offset(x, size.height - tick),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        val seconds = (totalMs / 1000L).toInt()
        for (s in 0..seconds step 2) {
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    TimeFormat.elapsed(s * 1000L),
                    s * pxPerSec + 3.dp.toPx(),
                    10.dp.toPx(),
                    paint
                )
            }
        }
    }
}

/** Text overlays. They currently last for the whole video, so the bar spans the whole timeline. */
@Composable
private fun TextLane(texts: List<TextItem>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.clip(shape).background(CcLane), contentAlignment = Alignment.CenterStart) {
        if (texts.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(CcTextClip.copy(alpha = 0.85f), shape),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = texts.joinToString("  ·  ") { it.text.ifBlank { "…" } },
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 32.dp, end = 8.dp)
                )
            }
        }
    }
}

/** The audio of the video clips. It follows the clips; a muted clip is grey. */
@Composable
private fun AudioLane(segments: List<Segment>, hasAudio: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Row(modifier = modifier.clip(shape).background(CcLane)) {
        if (hasAudio) {
            segments.forEach { seg ->
                key(seg.id) {
                    val muted = seg.volume == 0f
                    Box(
                        modifier = Modifier
                            .width((seg.timelineMs / 1000f * DP_PER_SEC).dp)
                            .fillMaxHeight()
                            .padding(end = 2.dp)
                            .background(
                                if (muted) CcChip else CcAudioClip.copy(alpha = 0.85f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentBlock(
    seg: Segment,
    images: List<ImageBitmap?>,
    sourceDurationMs: Long,
    selected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrim: (Long, Long) -> Unit,
    onTrimPreview: (Long) -> Unit,
    onTrimEnd: () -> Unit
) {
    val segNow by rememberUpdatedState(seg)
    val ppmNow by rememberUpdatedState(pxPerMs)
    val widthDp = (seg.timelineMs / 1000f * DP_PER_SEC).dp
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(52.dp)
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(CcChip)
                .clickable { onSelect() }
        ) {
            val h = size.height
            val tileW = h * 16f / 9f
            val count = ceil(size.width / tileW).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                val frac = ((i + 0.5f) * tileW / size.width).coerceIn(0f, 1f)
                val srcMs = seg.startMs + (frac * seg.sourceMs).toLong()
                val img = pickImage(images, srcMs, sourceDurationMs) ?: continue
                val tileAspect = tileW / h
                val imgAspect = img.width.toFloat() / img.height.coerceAtLeast(1)
                val srcW: Int
                val srcH: Int
                if (imgAspect > tileAspect) {
                    srcH = img.height
                    srcW = (img.height * tileAspect).roundToInt().coerceAtLeast(1)
                } else {
                    srcW = img.width
                    srcH = (img.width / tileAspect).roundToInt().coerceAtLeast(1)
                }
                drawImage(
                    image = img,
                    srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset((i * tileW).roundToInt(), 0),
                    dstSize = IntSize(tileW.roundToInt() + 1, h.roundToInt())
                )
            }
            // A thin gap marks where this clip ends.
            val gap = 2.dp.toPx()
            drawRect(CcBg, topLeft = Offset(size.width - gap, 0f), size = Size(gap, h))
        }

        if (seg.speed != 1f || seg.volume == 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 3.dp)
            ) {
                if (seg.speed != 1f) {
                    Text(
                        text = speedLabel(seg.speed),
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp)
                    )
                }
                if (seg.volume == 0f) {
                    Icon(
                        Icons.Rounded.VolumeOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .size(14.dp)
                    )
                }
            }
        }

        if (selected) {
            TrimHandle(
                modifier = Modifier.align(Alignment.CenterStart),
                onDrag = { dx ->
                    val s = segNow
                    val delta = (dx / ppmNow * s.speed).toLong()
                    val newStart = (s.startMs + delta).coerceIn(0L, s.endMs - EditorViewModel.MIN_CLIP_MS)
                    onTrim(newStart, s.endMs)
                    onTrimPreview(newStart)
                },
                onEnd = onTrimEnd
            )
            TrimHandle(
                modifier = Modifier.align(Alignment.CenterEnd),
                onDrag = { dx ->
                    val s = segNow
                    val delta = (dx / ppmNow * s.speed).toLong()
                    val newEnd = (s.endMs + delta).coerceIn(s.startMs + EditorViewModel.MIN_CLIP_MS, sourceDurationMs)
                    onTrim(s.startMs, newEnd)
                    onTrimPreview(newEnd)
                },
                onEnd = onTrimEnd
            )
        }
    }
}

/** The thumbnail closest to source time [srcMs] (some may still be loading). */
private fun pickImage(images: List<ImageBitmap?>, srcMs: Long, durationMs: Long): ImageBitmap? {
    if (images.isEmpty() || durationMs <= 0L) return null
    val idx = ((srcMs.toFloat() / durationMs) * images.size).toInt().coerceIn(0, images.size - 1)
    images[idx]?.let { return it }
    for (d in 1..6) {
        images.getOrNull(idx - d)?.let { return it }
        images.getOrNull(idx + d)?.let { return it }
    }
    return null
}

@Composable
private fun TrimHandle(modifier: Modifier, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val dragNow by rememberUpdatedState(onDrag)
    val endNow by rememberUpdatedState(onEnd)
    Box(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .background(Color.White, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { endNow() },
                    onDragCancel = { endNow() }
                ) { change, dragAmount ->
                    change.consume()
                    dragNow(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(18.dp)
                .background(Color(0xFF555555))
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Toolbar and tool panels
// ---------------------------------------------------------------------------------------------

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = if (enabled) Color.White else CcMuted.copy(alpha = 0.5f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ToolPanel(title: String, onDone: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CcPanel)
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDone) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ed_done), tint = CcAccent)
            }
        }
        Column(
            modifier = Modifier
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(text = text, color = CcMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SpeedPanel(target: Segment?, onSpeed: (Long, Float) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SPEEDS.forEach { v ->
            FilterChip(
                selected = target != null && target.speed == v,
                onClick = { if (target != null) onSpeed(target.id, v) },
                enabled = target != null,
                label = { Text(speedLabel(v)) }
            )
        }
    }
    HintText(stringResource(R.string.ed_hint_clip))
}

@Composable
private fun VolumePanel(target: Segment?, hasAudio: Boolean, vm: EditorViewModel) {
    val volume = target?.volume ?: 1f
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                if (target != null) {
                    vm.setVolume(target.id, if (volume == 0f) 1f else 0f)
                    vm.endGesture()
                }
            },
            enabled = hasAudio && target != null
        ) {
            Icon(
                imageVector = if (volume == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.ed_mute),
                tint = if (hasAudio) Color.White else CcMuted
            )
        }
        Slider(
            value = volume,
            onValueChange = { if (target != null) vm.setVolume(target.id, it) },
            onValueChangeFinished = { vm.endGesture() },
            valueRange = 0f..2f,
            enabled = hasAudio && target != null,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.ed_volume, (volume * 100).roundToInt()),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(88.dp).padding(start = 8.dp)
        )
    }
    if (!hasAudio) HintText(stringResource(R.string.editor_no_audio))
    else HintText(stringResource(R.string.ed_hint_clip))
}

@Composable
private fun FiltersPanel(selected: FilterPreset, onSelect: (FilterPreset) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterPreset.values().forEach { f ->
            val on = f == selected
            Column(
                modifier = Modifier.clickable { onSelect(f) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(filterColors(f)))
                        .then(if (on) Modifier.border(2.dp, CcAccent, RoundedCornerShape(10.dp)) else Modifier)
                )
                Text(
                    text = stringResource(filterName(f)),
                    color = if (on) CcAccent else Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun filterName(f: FilterPreset): Int = when (f) {
    FilterPreset.NONE -> R.string.filter_none
    FilterPreset.VIVID -> R.string.filter_vivid
    FilterPreset.WARM -> R.string.filter_warm
    FilterPreset.COOL -> R.string.filter_cool
    FilterPreset.VINTAGE -> R.string.filter_vintage
    FilterPreset.FADE -> R.string.filter_fade
    FilterPreset.BW -> R.string.filter_bw
    FilterPreset.NEGATIVE -> R.string.filter_negative
}

private fun filterColors(f: FilterPreset): List<Color> = when (f) {
    FilterPreset.NONE -> listOf(Color(0xFF6B7280), Color(0xFF9CA3AF))
    FilterPreset.VIVID -> listOf(Color(0xFFFF3D71), Color(0xFF3A86FF))
    FilterPreset.WARM -> listOf(Color(0xFFFF9F43), Color(0xFFFF6B6B))
    FilterPreset.COOL -> listOf(Color(0xFF48DBFB), Color(0xFF5F27CD))
    FilterPreset.VINTAGE -> listOf(Color(0xFFC8A97E), Color(0xFF7D5A50))
    FilterPreset.FADE -> listOf(Color(0xFFD1D5DB), Color(0xFFF3F4F6))
    FilterPreset.BW -> listOf(Color(0xFF111111), Color(0xFFEEEEEE))
    FilterPreset.NEGATIVE -> listOf(Color(0xFF00E5FF), Color(0xFFFF6D00))
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(92.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AdjustPanel(adjust: Adjust, vm: EditorViewModel) {
    LabeledSlider(stringResource(R.string.adj_brightness), adjust.brightness, -1f..1f,
        { vm.setAdjust(adjust.copy(brightness = it)) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.adj_contrast), adjust.contrast, -1f..1f,
        { vm.setAdjust(adjust.copy(contrast = it)) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.adj_saturation), adjust.saturation, -1f..1f,
        { vm.setAdjust(adjust.copy(saturation = it)) }, { vm.endGesture() })
    TextButton(
        onClick = {
            vm.setAdjust(Adjust())
            vm.endGesture()
        },
        enabled = !adjust.isDefault
    ) { Text(stringResource(R.string.adj_reset)) }
}

@Composable
private fun TextPanel(ui: EditorUiState, vm: EditorViewModel) {
    val defaultText = stringResource(R.string.text_default)
    val selected = ui.texts.firstOrNull { it.id == ui.selectedTextId }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ui.texts.forEach { t ->
            FilterChip(
                selected = t.id == ui.selectedTextId,
                onClick = { vm.selectText(t.id) },
                label = { Text(t.text.ifBlank { "…" }.take(12)) }
            )
        }
        FilterChip(
            selected = false,
            onClick = { vm.addText(defaultText) },
            label = { Text(stringResource(R.string.text_add)) },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
    }
    if (selected == null) {
        HintText(stringResource(R.string.text_empty_hint))
        return
    }
    OutlinedTextField(
        value = selected.text,
        onValueChange = { v -> vm.updateText(selected.id) { it.copy(text = v) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    Row(
        modifier = Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TEXT_COLORS.forEach { c ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (c == selected.color) 3.dp else 1.dp,
                        color = if (c == selected.color) CcAccent else CcMuted,
                        shape = CircleShape
                    )
                    .clickable {
                        vm.updateText(selected.id) { it.copy(color = c) }
                        vm.endGesture()
                    }
            )
        }
    }
    LabeledSlider(stringResource(R.string.text_size), selected.size, 0.03f..0.2f,
        { v -> vm.updateText(selected.id) { it.copy(size = v) } }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.text_pos_x), selected.x, -0.9f..0.9f,
        { v -> vm.updateText(selected.id) { it.copy(x = v) } }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.text_pos_y), selected.y, -0.9f..0.9f,
        { v -> vm.updateText(selected.id) { it.copy(y = v) } }, { vm.endGesture() })
    TextButton(onClick = { vm.removeText(selected.id) }) { Text(stringResource(R.string.text_remove)) }
}

@Composable
private fun CanvasPanel(ui: EditorUiState, vm: EditorViewModel) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CropPreset.values().forEach { c ->
            FilterChip(
                selected = c == ui.crop,
                onClick = { vm.setCrop(c) },
                label = {
                    Text(
                        stringResource(
                            when (c) {
                                CropPreset.ORIGINAL -> R.string.crop_original
                                CropPreset.SQUARE -> R.string.crop_square
                                CropPreset.WIDE -> R.string.crop_wide
                                CropPreset.TALL -> R.string.crop_tall
                                CropPreset.CLASSIC -> R.string.crop_classic
                                CropPreset.PORTRAIT -> R.string.crop_portrait
                            }
                        )
                    )
                }
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        OutlinedButton(onClick = { vm.rotate(-90) }) {
            Icon(Icons.Rounded.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.editor_rotate_left), modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { vm.rotate(90) }) {
            Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.editor_rotate_right), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

package com.screenx.ai.presentation.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.NavigateBefore
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.Image as ImageIcon
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
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.screenx.ai.R
import com.screenx.ai.editor.Adjust
import com.screenx.ai.editor.CropPreset
import com.screenx.ai.editor.EditEffects
import com.screenx.ai.editor.EditTimeline
import com.screenx.ai.editor.EditorUiState
import com.screenx.ai.editor.ExportQuality
import com.screenx.ai.editor.ExportState
import com.screenx.ai.editor.FilterMath
import com.screenx.ai.editor.FilterPreset
import com.screenx.ai.editor.LookSettings
import com.screenx.ai.editor.ImportKind
import com.screenx.ai.editor.ImportedMedia
import com.screenx.ai.editor.MediaImporter
import com.screenx.ai.editor.ProjectMediaPool
import com.screenx.ai.editor.OverlayLayout
import com.screenx.ai.editor.Segment
import com.screenx.ai.editor.TimelineClip
import com.screenx.ai.editor.TimelineOps
import com.screenx.ai.editor.Track
import com.screenx.ai.editor.TrackKind
import com.screenx.ai.editor.Transitions
import com.screenx.ai.editor.TextItem
import com.screenx.ai.editor.VoiceRecorder
import com.screenx.ai.editor.WaveformExtractor
import com.screenx.ai.editor.WaveformMath
import com.screenx.ai.utils.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
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

/** Timeline zoom steps: how wide one second is, in dp. */
private val ZOOM_STEPS = listOf(12f, 22f, 44f, 88f, 176f)
private const val DEFAULT_ZOOM_INDEX = 2

private enum class Tool { NONE, ADD, OVERLAY, SPEED, VOLUME, AUDIO, VOICE, TRANSITION, FILTERS, ADJUST, TEXT, CANVAS }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFE600, 0xFFFF3B30, 0xFF25F4EE, 0xFFFF2D95, 0xFF34C759, 0xFFFF9500
).map { it.toInt() }

/** What the preview player needs to know about one clip. Speed and volume are applied while playing. */
private data class PlayItem(val id: Long, val uri: String, val startMs: Long, val endMs: Long) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()
}

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
    val waves by viewModel.waves.collectAsStateWithLifecycle()
    val busy = ui.export is ExportState.Running

    // ---- preview player: a playlist with one entry per clip ----
    // Exact seeking: scrubbing and frame stepping land on the real frame, not the nearest keyframe.
    val exo = remember { ExoPlayer.Builder(context).build().apply { setSeekParameters(SeekParameters.EXACT) } }
    DisposableEffect(exo) { onDispose { exo.release() } }

    var loop by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

    // Clips of another size are fitted into the picture size of the first video.
    val mixedSizes = ui.segments.any { it.width != ui.width || it.height != ui.height }
    val previewEffects = remember(
        ui.width, ui.height, ui.rotationCw, ui.flipH, ui.flipV, ui.crop, ui.filter, ui.adjust, ui.texts, mixedSizes
    ) {
        EditEffects.preview(
            ui.width,
            ui.height,
            LookSettings(ui.rotationCw, ui.crop, ui.filter, ui.adjust, ui.texts, ui.flipH, ui.flipV),
            mixedSizes
        )
    }
    LaunchedEffect(previewEffects) { exo.setVideoEffects(previewEffects) }

    // The preview frame has the shape of the result, so overlays line up with what will be exported.
    val frameAspect = remember(ui.width, ui.height, ui.rotationCw, ui.crop) {
        if (ui.width > 0 && ui.height > 0) {
            val (w, h) = EditEffects.outputSize(
                ui.width,
                ui.height,
                LookSettings(ui.rotationCw, ui.crop, FilterPreset.NONE, Adjust(), emptyList())
            )
            w.toFloat() / h.coerceAtLeast(1)
        } else {
            16f / 9f
        }
    }

    // Position on the EDITED timeline (clips back to back).
    var timelineMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val segs by rememberUpdatedState(ui.segments)
    val timelineNow by rememberUpdatedState(timelineMs)
    // Set while a trim handle is dragged, so the preview shows the edge being moved: (clip id, is start edge).
    var pendingEdge by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }

    // Rebuild the playlist whenever the order, the source or the cut points of the clips change.
    val playlist = ui.segments.map { PlayItem(it.id, it.uri, it.startMs, it.endMs) }
    LaunchedEffect(playlist) {
        if (playlist.isEmpty()) return@LaunchedEffect
        val list = segs
        var tl = timelineNow
        val edge = pendingEdge
        if (edge != null) {
            val i = list.indexOfFirst { it.id == edge.first }
            if (i >= 0) {
                tl = if (edge.second) {
                    EditTimeline.startOf(list, i)
                } else {
                    (EditTimeline.startOf(list, i + 1) - 1L).coerceAtLeast(0L)
                }
            }
            pendingEdge = null
        }
        val loc = EditTimeline.locate(list, tl)
        val index = loc?.index ?: 0
        val offset = if (loc != null) loc.sourceMs - list[index].startMs else 0L
        exo.setMediaItems(playlist.map { it.toMediaItem() }, index, offset)
        exo.prepare()
        timelineMs = tl
    }

    LaunchedEffect(exo) {
        while (true) {
            val list = segs
            val idx = exo.currentMediaItemIndex
            val s = list.getOrNull(idx)
            if (s != null && exo.mediaItemCount == list.size && exo.currentMediaItem?.mediaId == s.id.toString()) {
                // Speed and volume belong to each clip, so they follow the clip being played.
                if (exo.playbackParameters.speed != s.speed) exo.setPlaybackSpeed(s.speed)
                val v = min(1f, s.volume)
                if (exo.volume != v) exo.volume = v
                timelineMs = EditTimeline.timelineOf(list, idx, exo.currentPosition)
            }
            playing = exo.isPlaying
            if (loop && list.isNotEmpty() && exo.playbackState == Player.STATE_ENDED) {
                exo.seekTo(0, 0L)
                exo.play()
            }
            delay(40)
        }
    }

    val totalMs = EditTimeline.total(ui.segments)
    val headIdx = EditTimeline.locate(ui.segments, timelineMs)?.index
    val target: Segment? = ui.segments.firstOrNull { it.id == ui.selectedId }
        ?: headIdx?.let { ui.segments.getOrNull(it) }

    fun seekTimeline(tl: Long) {
        val list = segs
        val loc = EditTimeline.locate(list, tl) ?: return
        if (exo.isPlaying) exo.pause()
        if (exo.mediaItemCount == list.size) exo.seekTo(loc.index, loc.sourceMs - list[loc.index].startMs)
        timelineMs = tl.coerceIn(0L, EditTimeline.total(list))
    }

    fun togglePlay() {
        if (exo.isPlaying) {
            exo.pause()
            return
        }
        val list = segs
        if (list.isEmpty() || exo.mediaItemCount != list.size) return
        if (timelineMs >= EditTimeline.total(list) - 60L || exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0, 0L)
        }
        exo.play()
    }

    fun stepFrame(direction: Int) {
        val fps = exo.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
        val step = (1000f / fps).toLong().coerceAtLeast(1L)
        seekTimeline((timelineMs + direction * step).coerceIn(0L, EditTimeline.total(segs)))
    }

    var tool by remember { mutableStateOf(Tool.NONE) }
    var zoomIdx by remember { mutableIntStateOf(DEFAULT_ZOOM_INDEX) }
    val pool by ProjectMediaPool.items.collectAsStateWithLifecycle()
    var showExport by remember { mutableStateOf(false) }
    fun closeTool() {
        viewModel.endGesture()
        tool = Tool.NONE
    }

    val splitFailText = stringResource(R.string.ed_split_fail)
    val deleteFailText = stringResource(R.string.ed_delete_fail)
    val mergeFailText = stringResource(R.string.ed_merge_fail)

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
                // Preview (tap to play / pause). The frame has the shape of the result.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .clickable { togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(modifier = Modifier.aspectRatio(frameAspect)) {
                        val frameW = constraints.maxWidth.toFloat()
                        val frameH = constraints.maxHeight.toFloat()
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exo
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Fade through black at a cut that has a transition.
                        val dark = Transitions.darkness(ui.segments, timelineMs)
                        if (dark > 0f) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dark)))
                        }
                        // Music, voiceover and sound effects: they only make sound.
                        TimelineOps.audibleAt(ui.tracks, timelineMs).forEach { clip ->
                            key(clip.id) { AudioPreview(clip, timelineMs, playing) }
                        }
                        // Pictures from the extra tracks, bottom layer first.
                        var videos = 0
                        TimelineOps.visualAt(ui.tracks, timelineMs).forEach { clip ->
                            if (clip.kind == ImportKind.VIDEO) {
                                videos++
                                if (videos > MAX_OVERLAY_VIDEOS) return@forEach
                            }
                            key(clip.id) {
                                PreviewOverlay(
                                    clip = clip,
                                    frameW = frameW,
                                    frameH = frameH,
                                    timelineMs = timelineMs,
                                    playing = playing,
                                    selected = clip.id == ui.selectedClipId,
                                    onSelect = { viewModel.selectClip(clip.id) },
                                    onMove = { x, y -> viewModel.setClipTransform(clip.id, posX = x, posY = y) },
                                    onMoveEnd = { viewModel.endGesture() }
                                )
                            }
                        }
                    }
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
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color(0x66000000), RoundedCornerShape(20.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewButton(Icons.Rounded.Repeat, active = loop) { loop = !loop }
                        PreviewButton(Icons.Rounded.NavigateBefore) { stepFrame(-1) }
                        PreviewButton(Icons.Rounded.NavigateNext) { stepFrame(1) }
                        PreviewButton(if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen) {
                            full = !full
                        }
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

                // Timeline and tools (hidden in the full preview)
                if (!full) {
                Timeline(
                    segments = ui.segments,
                    texts = ui.texts,
                    hasAudio = ui.hasAudio,
                    tracks = ui.tracks,
                    waves = waves,
                    pool = pool,
                    selectedClipId = ui.selectedClipId,
                    zoomDpPerSec = ZOOM_STEPS[zoomIdx],
                    onZoom = { d -> zoomIdx = (zoomIdx + d).coerceIn(0, ZOOM_STEPS.lastIndex) },
                    onSelectClip = { id -> viewModel.selectClip(if (ui.selectedClipId == id) null else id) },
                    onMoveClip = { id, start -> viewModel.moveClip(id, start) },
                    onTrimClipStart = { id, start -> viewModel.trimClipStart(id, start) },
                    onTrimClipEnd = { id, end -> viewModel.trimClipEnd(id, end) },
                    thumbs = thumbs,
                    selectedId = ui.selectedId,
                    timelineMs = timelineMs,
                    onSeek = { seekTimeline(it) },
                    onSelect = { id -> viewModel.select(if (ui.selectedId == id) null else id) },
                    onTrim = { id, start, end -> viewModel.trimSegment(id, start, end) },
                    onTrimPreview = { id, isStart ->
                        if (exo.isPlaying) exo.pause()
                        pendingEdge = id to isStart
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
                        ToolButton(Icons.Rounded.Add, stringResource(R.string.tool_add), !busy) { tool = Tool.ADD }
                        ToolButton(Icons.Rounded.ContentCut, stringResource(R.string.tool_split), !busy) {
                            val clipId = ui.selectedClipId
                            val ok = if (clipId != null) {
                                viewModel.splitClip(clipId, timelineMs)
                            } else {
                                viewModel.split(timelineMs)
                            }
                            if (!ok) Toast.makeText(context, splitFailText, Toast.LENGTH_SHORT).show()
                        }
                        val selectedClip = ui.selectedClipId
                        val selectedSeg = ui.selectedId
                        val segIndex = ui.segments.indexOfFirst { it.id == selectedSeg }
                        if (selectedClip != null) {
                            ToolButton(Icons.Rounded.ContentCopy, stringResource(R.string.tool_duplicate), !busy) {
                                viewModel.duplicateClip(selectedClip)
                            }
                        } else if (selectedSeg != null && segIndex >= 0) {
                            ToolButton(Icons.Rounded.ContentCopy, stringResource(R.string.tool_duplicate), !busy) {
                                viewModel.duplicateSegment(selectedSeg)
                            }
                            ToolButton(
                                Icons.Rounded.KeyboardArrowLeft,
                                stringResource(R.string.tool_move_left),
                                !busy && segIndex > 0
                            ) { viewModel.moveSegment(selectedSeg, -1) }
                            ToolButton(
                                Icons.Rounded.KeyboardArrowRight,
                                stringResource(R.string.tool_move_right),
                                !busy && segIndex < ui.segments.lastIndex
                            ) { viewModel.moveSegment(selectedSeg, 1) }
                            ToolButton(
                                Icons.Rounded.CallMerge,
                                stringResource(R.string.tool_merge),
                                !busy && segIndex < ui.segments.lastIndex
                            ) {
                                if (!viewModel.mergeSegment(selectedSeg)) {
                                    Toast.makeText(context, mergeFailText, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        val panelClip = selectedClip?.let { TimelineOps.find(ui.tracks, it) }
                        if (panelClip != null && panelClip.kind != ImportKind.AUDIO) {
                            ToolButton(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.tool_overlay), !busy) {
                                tool = Tool.OVERLAY
                            }
                        }
                        ToolButton(Icons.Rounded.Delete, stringResource(R.string.tool_delete), !busy) {
                            if (selectedClip != null) {
                                viewModel.deleteClip(selectedClip)
                            } else {
                                val id = target?.id
                                if (id == null || !viewModel.deleteSegment(id)) {
                                    Toast.makeText(context, deleteFailText, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        ToolButton(Icons.Rounded.Speed, stringResource(R.string.tool_speed), !busy) { tool = Tool.SPEED }
                        ToolButton(Icons.Rounded.Animation, stringResource(R.string.tool_transition), !busy) { tool = Tool.TRANSITION }
                        ToolButton(Icons.Rounded.VolumeUp, stringResource(R.string.tool_volume), !busy) { tool = Tool.VOLUME }
                        ToolButton(Icons.Rounded.MusicNote, stringResource(R.string.tool_audio), !busy) { tool = Tool.AUDIO }
                        ToolButton(Icons.Rounded.Mic, stringResource(R.string.tool_voice), !busy) { tool = Tool.VOICE }
                        ToolButton(Icons.Rounded.AutoAwesome, stringResource(R.string.tool_filters), !busy) { tool = Tool.FILTERS }
                        ToolButton(Icons.Rounded.Tune, stringResource(R.string.tool_adjust), !busy) { tool = Tool.ADJUST }
                        ToolButton(Icons.Rounded.TextFields, stringResource(R.string.tool_text), !busy) { tool = Tool.TEXT }
                        ToolButton(Icons.Rounded.Crop, stringResource(R.string.tool_canvas), !busy) { tool = Tool.CANVAS }
                    }
                } else {
                    ToolPanel(
                        title = stringResource(
                            when (tool) {
                                Tool.ADD -> R.string.tool_add
                                Tool.OVERLAY -> R.string.tool_overlay
                                Tool.SPEED -> R.string.tool_speed
                                Tool.VOLUME -> R.string.tool_volume
                                Tool.AUDIO -> R.string.tool_audio
                                Tool.TRANSITION -> R.string.tool_transition
                                Tool.VOICE -> R.string.tool_voice
                                Tool.FILTERS -> R.string.tool_filters
                                Tool.ADJUST -> R.string.tool_adjust
                                Tool.TEXT -> R.string.tool_text
                                else -> R.string.tool_canvas
                            }
                        ),
                        onDone = { closeTool() }
                    ) {
                        when (tool) {
                            Tool.ADD -> AddPanel(pool, timelineMs, headIdx ?: -1, viewModel)
                            Tool.OVERLAY -> OverlayPanel(
                                ui.selectedClipId?.let { TimelineOps.find(ui.tracks, it) },
                                viewModel
                            )
                            Tool.SPEED -> SpeedPanel(target) { id, v -> viewModel.setSpeed(id, v) }
                            Tool.VOLUME -> VolumePanel(target, ui.hasAudio, viewModel)
                            Tool.TRANSITION -> TransitionPanel(ui.segments, target, viewModel)
                            Tool.AUDIO -> AudioPanel(
                                ui.selectedClipId?.let { TimelineOps.find(ui.tracks, it) },
                                viewModel
                            )
                            Tool.VOICE -> VoicePanel(
                                startMs = timelineMs,
                                vm = viewModel,
                                onStartPlayback = { if (!exo.isPlaying) togglePlay() },
                                onStopPlayback = { if (exo.isPlaying) exo.pause() }
                            )
                            Tool.FILTERS -> FiltersPanel(
                                ui.filter,
                                ui.adjust.filterStrength,
                                thumbs[ui.segments.firstOrNull()?.uri].orEmpty().firstOrNull { it != null }?.asImageBitmap(),
                                { viewModel.setFilter(it) },
                                { viewModel.setAdjust(ui.adjust.copy(filterStrength = it)) },
                                { viewModel.endGesture() }
                            )
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
private val CLIP_LANE_H = 32.dp
private val LANE_GAP = 4.dp
private val LANES_VIEW_H = 148.dp

/** The widest the timeline may be, in pixels (a layout cannot be much wider than 260 000 px). */
private const val MAX_CONTENT_PX = 240_000f

@Composable
private fun Timeline(
    segments: List<Segment>,
    texts: List<TextItem>,
    hasAudio: Boolean,
    tracks: List<Track>,
    waves: Map<String, FloatArray>,
    pool: List<ImportedMedia>,
    selectedClipId: Long?,
    zoomDpPerSec: Float,
    onZoom: (Int) -> Unit,
    onSelectClip: (Long) -> Unit,
    onMoveClip: (Long, Long) -> Unit,
    onTrimClipStart: (Long, Long) -> Unit,
    onTrimClipEnd: (Long, Long) -> Unit,
    thumbs: Map<String, List<Bitmap?>>,
    selectedId: Long?,
    timelineMs: Long,
    onSeek: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onTrim: (Long, Long, Long) -> Unit,
    onTrimPreview: (Long, Boolean) -> Unit,
    onTrimEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val total = EditTimeline.total(segments)
    // The timeline is as long as the main video or the last extra clip, whichever is later.
    val endMs = maxOf(total, TimelineOps.end(tracks))
    val maxDpPerSec = (MAX_CONTENT_PX / density.density) / (endMs / 1000f).coerceAtLeast(1f)
    val dpPerSec = minOf(zoomDpPerSec, maxDpPerSec)
    val pxPerMs = with(density) { dpPerSec.dp.toPx() } / 1000f
    val scroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val seekNow by rememberUpdatedState(onSeek)
    val totalNow by rememberUpdatedState(total)
    val images = remember(thumbs) { thumbs.mapValues { (_, list) -> list.map { it?.asImageBitmap() } } }
    // True while the strip is being moved by the player (not by a finger).
    val followFlag = remember { BooleanArray(1) }

    // Keep the strip under the playhead while playing, after a seek or after zooming.
    LaunchedEffect(timelineMs, total, pxPerMs) {
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

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(RULER_H + LANE_GAP + LANES_VIEW_H)) {
        val half = maxWidth / 2
        val contentWidth = (endMs / 1000f * dpPerSec).dp
        Row(modifier = Modifier.fillMaxHeight().horizontalScroll(scroll)) {
            Spacer(Modifier.width(half))
            Column(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
                TimeRuler(endMs, dpPerSec, Modifier.fillMaxWidth().height(RULER_H))
                Spacer(Modifier.height(LANE_GAP))
                // The lanes scroll up and down when there are more than fit.
                Column(modifier = Modifier.fillMaxWidth().height(LANES_VIEW_H).verticalScroll(vScroll)) {
                    // Main video track
                    Row(modifier = Modifier.height(MAIN_H)) {
                        segments.forEach { seg ->
                            key(seg.id) {
                                Box {
                                    SegmentBlock(
                                        seg = seg,
                                        dpPerSec = dpPerSec,
                                        images = images[seg.uri].orEmpty(),
                                        selected = seg.id == selectedId,
                                        pxPerMs = pxPerMs,
                                        onSelect = { onSelect(seg.id) },
                                        onTrim = { start, end -> onTrim(seg.id, start, end) },
                                        onTrimPreview = onTrimPreview,
                                        onTrimEnd = onTrimEnd
                                    )
                                    // A dot at the end of a clip that fades into the next one.
                                    if (Transitions.effective(segments, segments.indexOf(seg)) > 0L) {
                                        Box(
                                            Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 2.dp)
                                                .size(16.dp)
                                                .background(CcBg, CircleShape)
                                                .padding(4.dp)
                                                .background(CcAccent, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(LANE_GAP))
                    TextLane(texts, Modifier.fillMaxWidth().height(LANE_H))
                    Spacer(Modifier.height(LANE_GAP))
                    AudioLane(segments, hasAudio, waves, dpPerSec, Modifier.fillMaxWidth().height(LANE_H))
                    tracks.forEach { track ->
                        key(track.id) {
                            Spacer(Modifier.height(LANE_GAP))
                            ClipLane(
                                track = track,
                                selectedClipId = selectedClipId,
                                waves = waves,
                                pool = pool,
                                dpPerSec = dpPerSec,
                                pxPerMs = pxPerMs,
                                onSelect = onSelectClip,
                                onMove = onMoveClip,
                                onTrimStart = onTrimClipStart,
                                onTrimEnd = onTrimClipEnd,
                                onGestureEnd = onTrimEnd,
                                modifier = Modifier.fillMaxWidth().height(CLIP_LANE_H)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(half))
        }

        // Track icons stay at the left edge (and scroll up and down together with the lanes).
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = RULER_H + LANE_GAP)
                .height(LANES_VIEW_H)
                .verticalScroll(vScroll)
        ) {
            TrackIcon(Icons.Rounded.Videocam, MAIN_H)
            Spacer(Modifier.height(LANE_GAP))
            TrackIcon(Icons.Rounded.TextFields, LANE_H)
            Spacer(Modifier.height(LANE_GAP))
            TrackIcon(Icons.Rounded.MusicNote, LANE_H)
            tracks.forEach { track ->
                key(track.id) {
                    Spacer(Modifier.height(LANE_GAP))
                    TrackIcon(trackIcon(track.kind), CLIP_LANE_H)
                }
            }
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

        // Zoom
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(28.dp)
                .background(CcBg.copy(alpha = 0.9f), RoundedCornerShape(bottomStart = 8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ZoomOut,
                contentDescription = stringResource(R.string.zoom_out),
                tint = Color.White,
                modifier = Modifier.size(width = 36.dp, height = 28.dp).clickable { onZoom(-1) }.padding(6.dp)
            )
            Icon(
                Icons.Rounded.ZoomIn,
                contentDescription = stringResource(R.string.zoom_in),
                tint = Color.White,
                modifier = Modifier.size(width = 36.dp, height = 28.dp).clickable { onZoom(1) }.padding(6.dp)
            )
        }
    }
}

private fun trackColor(kind: TrackKind): Color = when (kind) {
    TrackKind.VIDEO -> Color(0xFF3D7BFF)
    TrackKind.OVERLAY -> Color(0xFFFF8A3D)
    TrackKind.IMAGE -> Color(0xFF34C759)
    TrackKind.AUDIO -> Color(0xFF1FA39E)
    TrackKind.VOICEOVER -> Color(0xFFFF2D95)
    TrackKind.SFX -> Color(0xFFE5B800)
}

private fun trackIcon(kind: TrackKind): ImageVector = when (kind) {
    TrackKind.VIDEO -> Icons.Rounded.Videocam
    TrackKind.OVERLAY -> Icons.Rounded.Layers
    TrackKind.IMAGE -> Icons.Rounded.ImageIcon
    TrackKind.AUDIO -> Icons.Rounded.MusicNote
    TrackKind.VOICEOVER -> Icons.Rounded.Mic
    TrackKind.SFX -> Icons.Rounded.GraphicEq
}

private fun trackName(kind: TrackKind): Int = when (kind) {
    TrackKind.VIDEO -> R.string.track_video
    TrackKind.OVERLAY -> R.string.track_overlay
    TrackKind.IMAGE -> R.string.track_image
    TrackKind.AUDIO -> R.string.track_audio
    TrackKind.VOICEOVER -> R.string.track_voiceover
    TrackKind.SFX -> R.string.track_sfx
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

/** How many seconds apart the time labels are, so they never overlap at the current zoom. */
private fun labelStepSeconds(dpPerSec: Float): Int {
    val need = 52f / dpPerSec
    return listOf(1, 2, 5, 10, 30, 60).firstOrNull { it >= need } ?: 60
}

/** Time marks: small ticks and a label every few seconds, depending on the zoom. */
@Composable
private fun TimeRuler(totalMs: Long, dpPerSec: Float, modifier: Modifier = Modifier) {
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A9AA2.toInt() }
    }
    Canvas(modifier = modifier) {
        val pxPerSec = dpPerSec.dp.toPx()
        paint.textSize = 9.sp.toPx()
        val minorMs = if (dpPerSec >= 30f) 500L else 1000L
        val minorCount = (totalMs / minorMs).toInt()
        for (i in 0..minorCount) {
            val x = i * (minorMs / 1000f) * pxPerSec
            val isSecond = (i * minorMs) % 1000L == 0L
            val tick = if (isSecond) 7.dp.toPx() else 4.dp.toPx()
            drawLine(
                color = CcMuted,
                start = Offset(x, size.height - tick),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        val seconds = (totalMs / 1000L).toInt()
        for (s in 0..seconds step labelStepSeconds(dpPerSec)) {
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
private fun AudioLane(
    segments: List<Segment>,
    hasAudio: Boolean,
    waves: Map<String, FloatArray>,
    dpPerSec: Float,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Row(modifier = modifier.clip(shape).background(CcLane)) {
        if (hasAudio) {
            segments.forEach { seg ->
                key(seg.id) {
                    val muted = seg.volume == 0f
                    Box(
                        modifier = Modifier
                            .width((seg.timelineMs / 1000f * dpPerSec).dp)
                            .fillMaxHeight()
                            .padding(end = 2.dp)
                            .background(
                                if (muted) CcChip else CcAudioClip.copy(alpha = 0.85f),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        WaveformBars(
                            peaks = waves[seg.uri],
                            fromMs = seg.startMs,
                            toMs = seg.endMs,
                            gain = seg.volume,
                            color = if (muted) CcMuted else Color.White,
                            modifier = Modifier.fillMaxSize().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** One extra track: its clips sit at their own time on the timeline. */
@Composable
private fun ClipLane(
    track: Track,
    selectedClipId: Long?,
    waves: Map<String, FloatArray>,
    pool: List<ImportedMedia>,
    dpPerSec: Float,
    pxPerMs: Float,
    onSelect: (Long) -> Unit,
    onMove: (Long, Long) -> Unit,
    onTrimStart: (Long, Long) -> Unit,
    onTrimEnd: (Long, Long) -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.clip(shape).background(CcLane)) {
        track.clips.forEach { clip ->
            key(clip.id) {
                val thumb = remember(clip.uri, pool) {
                    pool.firstOrNull { it.uri.toString() == clip.uri }?.thumb?.asImageBitmap()
                }
                ClipBlock(
                    clip = clip,
                    trackKind = track.kind,
                    selected = clip.id == selectedClipId,
                    thumb = thumb,
                    peaks = waves[clip.uri],
                    dpPerSec = dpPerSec,
                    pxPerMs = pxPerMs,
                    onSelect = { onSelect(clip.id) },
                    onMove = { start -> onMove(clip.id, start) },
                    onTrimStart = { start -> onTrimStart(clip.id, start) },
                    onTrimEnd = { end -> onTrimEnd(clip.id, end) },
                    onGestureEnd = onGestureEnd
                )
            }
        }
    }
}

@Composable
private fun ClipBlock(
    clip: TimelineClip,
    trackKind: TrackKind,
    selected: Boolean,
    thumb: ImageBitmap?,
    peaks: FloatArray?,
    dpPerSec: Float,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onMove: (Long) -> Unit,
    onTrimStart: (Long) -> Unit,
    onTrimEnd: (Long) -> Unit,
    onGestureEnd: () -> Unit
) {
    val clipNow by rememberUpdatedState(clip)
    val ppmNow by rememberUpdatedState(pxPerMs)
    val moveNow by rememberUpdatedState(onMove)
    val gestureEndNow by rememberUpdatedState(onGestureEnd)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .offset(x = (clip.startMs / 1000f * dpPerSec).dp)
            .width((clip.lengthMs / 1000f * dpPerSec).dp)
            .fillMaxHeight()
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clip(shape)
            .background(trackColor(trackKind).copy(alpha = 0.85f))
            .clickable { onSelect() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { gestureEndNow() },
                    onDragCancel = { gestureEndNow() }
                ) { change, dragAmount ->
                    change.consume()
                    moveNow(clipNow.startMs + (dragAmount / ppmNow).toLong())
                }
            }
    ) {
        if (trackKind in TimelineOps.AUDIBLE_KINDS) {
            WaveformBars(
                peaks = peaks,
                fromMs = clip.inMs,
                toMs = clip.inMs + clip.lengthMs,
                gain = clip.volume,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxSize().padding(vertical = 3.dp)
            )
            FadeMarks(clip, pxPerMs, Modifier.fillMaxSize())
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(start = if (selected) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().width(CLIP_LANE_H)
                )
            }
            Text(
                text = clip.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (selected) {
            TrimHandle(
                modifier = Modifier.align(Alignment.CenterStart),
                onDrag = { dx -> onTrimStart(clipNow.startMs + (dx / ppmNow).toLong()) },
                onEnd = onGestureEnd
            )
            TrimHandle(
                modifier = Modifier.align(Alignment.CenterEnd),
                onDrag = { dx -> onTrimEnd(clipNow.endMs + (dx / ppmNow).toLong()) },
                onEnd = onGestureEnd
            )
        }
    }
}

/** The loudness picture of a sound. Bars grow with the volume; a flat line means it is still being measured. */
@Composable
private fun WaveformBars(
    peaks: FloatArray?,
    fromMs: Long,
    toMs: Long,
    gain: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val minBar = 1.5.dp.toPx()
        val step = 3.dp.toPx()
        val bars = minOf((size.width / step).toInt(), 1500).coerceAtLeast(1)
        val slot = size.width / bars
        val values = if (peaks != null) {
            WaveformMath.slice(peaks, WaveformExtractor.BUCKET_MS, fromMs, toMs, bars)
        } else {
            null
        }
        val barW = (slot * 0.6f).coerceAtLeast(1f)
        val mid = size.height / 2f
        for (i in 0 until bars) {
            val v = ((values?.get(i) ?: 0f) * gain.coerceIn(0f, 1.5f)).coerceIn(0f, 1f)
            val h = maxOf(minBar, v * size.height)
            drawRect(
                color = color,
                topLeft = Offset(i * slot, mid - h / 2f),
                size = Size(barW, h)
            )
        }
    }
}

/** Little slopes at the ends of a sound clip that show the fade in and fade out. */
@Composable
private fun FadeMarks(clip: TimelineClip, pxPerMs: Float, modifier: Modifier = Modifier) {
    if (clip.fadeInMs <= 0L && clip.fadeOutMs <= 0L) return
    Canvas(modifier = modifier) {
        val line = Color.White
        val w = 1.5.dp.toPx()
        if (clip.fadeInMs > 0L) {
            val x = (clip.fadeInMs * pxPerMs).coerceAtMost(size.width)
            drawLine(line, Offset(0f, size.height), Offset(x, 0f), w)
        }
        if (clip.fadeOutMs > 0L) {
            val x = (clip.fadeOutMs * pxPerMs).coerceAtMost(size.width)
            drawLine(line, Offset(size.width - x, 0f), Offset(size.width, size.height), w)
        }
    }
}

@Composable
private fun SegmentBlock(
    seg: Segment,
    dpPerSec: Float,
    images: List<ImageBitmap?>,
    selected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrim: (Long, Long) -> Unit,
    onTrimPreview: (Long, Boolean) -> Unit,
    onTrimEnd: () -> Unit
) {
    val segNow by rememberUpdatedState(seg)
    val ppmNow by rememberUpdatedState(pxPerMs)
    val widthDp = (seg.timelineMs / 1000f * dpPerSec).dp
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
                val img = pickImage(images, srcMs, seg.mediaDurationMs) ?: continue
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
                    onTrimPreview(s.id, true)
                },
                onEnd = onTrimEnd
            )
            TrimHandle(
                modifier = Modifier.align(Alignment.CenterEnd),
                onDrag = { dx ->
                    val s = segNow
                    val delta = (dx / ppmNow * s.speed).toLong()
                    val limit = if (s.mediaDurationMs > 0L) s.mediaDurationMs else s.endMs
                    val newEnd = (s.endMs + delta).coerceIn(s.startMs + EditorViewModel.MIN_CLIP_MS, limit)
                    onTrim(s.startMs, newEnd)
                    onTrimPreview(s.id, false)
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

/** Puts imported media on extra tracks at the playhead, and lets you import more. */
@Composable
private fun AddPanel(pool: List<ImportedMedia>, startMs: Long, headIndex: Int, vm: EditorViewModel) {
    val context = LocalContext.current
    val mismatchText = stringResource(R.string.add_main_mismatch)
    val importVm: ImportViewModel = viewModel()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        importVm.import(it)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        importVm.import(it)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
        ) { Text(stringResource(R.string.import_gallery)) }
        OutlinedButton(onClick = { files.launch(MediaImporter.FILE_MIME_TYPES) }) {
            Text(stringResource(R.string.import_files))
        }
    }
    if (pool.isEmpty()) HintText(stringResource(R.string.add_empty))
    pool.forEach { m ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CcChip),
                contentAlignment = Alignment.Center
            ) {
                val bmp = m.thumb
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (m.kind == ImportKind.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
                        contentDescription = null,
                        tint = CcMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(text = m.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
                if (m.durationMs > 0L) {
                    Text(text = TimeFormat.elapsed(m.durationMs), color = CcMuted, fontSize = 11.sp)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (m.kind == ImportKind.VIDEO) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (vm.addToMain(m, headIndex) == MainAddResult.AUDIO_MISMATCH) {
                                    Toast.makeText(context, mismatchText, Toast.LENGTH_LONG).show()
                                }
                            },
                            label = { Text(stringResource(R.string.track_main)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    TimelineOps.tracksFor(m.kind).forEach { kind ->
                        FilterChip(
                            selected = false,
                            onClick = { vm.addClip(m, kind, startMs) },
                            label = { Text(stringResource(trackName(kind))) },
                            leadingIcon = {
                                Icon(trackIcon(kind), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }
    }
    HintText(stringResource(R.string.add_note))
}

/** How many video overlays may play at once (each one needs its own video decoder). */
private const val MAX_OVERLAY_VIDEOS = 3

/** One picture clip drawn over the main video. Drag it to move it; tap to select it. */
@Composable
private fun PreviewOverlay(
    clip: TimelineClip,
    frameW: Float,
    frameH: Float,
    timelineMs: Long,
    playing: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit
) {
    val density = LocalDensity.current
    val r = OverlayLayout.rect(frameW, frameH, clip.posX, clip.posY, clip.scale, clip.aspect)
    val clipNow by rememberUpdatedState(clip)
    val moveNow by rememberUpdatedState(onMove)
    val endNow by rememberUpdatedState(onMoveEnd)
    val frameWNow by rememberUpdatedState(frameW)
    val frameHNow by rememberUpdatedState(frameH)
    Box(
        modifier = Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
            .then(if (selected) Modifier.border(2.dp, CcAccent) else Modifier)
            .alpha(clip.opacity)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { endNow() },
                    onDragCancel = { endNow() }
                ) { change, drag ->
                    change.consume()
                    val c = clipNow
                    moveNow(
                        c.posX + drag.x / frameWNow.coerceAtLeast(1f) * 2f,
                        c.posY - drag.y / frameHNow.coerceAtLeast(1f) * 2f
                    )
                }
            }
            .clickable { onSelect() }
    ) {
        when (clip.kind) {
            ImportKind.VIDEO -> OverlayVideo(clip, timelineMs, playing)
            ImportKind.IMAGE -> OverlayImage(clip.uri)
            ImportKind.AUDIO -> Unit
        }
    }
}

/** A video overlay has its own player and follows the playhead. Its sound is set in the Audio panel (silent at first). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun OverlayVideo(clip: TimelineClip, timelineMs: Long, playing: Boolean) {
    val context = LocalContext.current
    val player = remember(clip.id) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val vol = clipVolumeNow(clip, timelineMs)
    LaunchedEffect(player, vol) { player.volume = vol }
    // Reload when the clip is trimmed (a different part of the file).
    LaunchedEffect(player, clip.uri, clip.inMs, clip.lengthMs) {
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.inMs)
                        .setEndPositionMs(clip.inMs + clip.lengthMs)
                        .build()
                )
                .build()
        )
        player.prepare()
    }
    val want = (timelineMs - clip.startMs).coerceIn(0L, clip.lengthMs)
    LaunchedEffect(player, want, playing) {
        if (player.playbackState == Player.STATE_IDLE) return@LaunchedEffect
        val drift = abs(player.currentPosition - want)
        if (playing) {
            if (drift > 300L) player.seekTo(want)
            player.play()
        } else {
            if (drift > 30L) player.seekTo(want)
            player.pause()
        }
    }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.editor_overlay_player, null) as PlayerView).also {
                it.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** Volume of a clip right now: its own volume times the fade (steps of 2%, so it is not set too often). */
private fun clipVolumeNow(clip: TimelineClip, timelineMs: Long): Float {
    val pos = (timelineMs - clip.startMs).coerceIn(0L, clip.lengthMs)
    val v = clip.volume.coerceIn(0f, 1f) * TimelineOps.fadeFactor(pos, clip.lengthMs, clip.fadeInMs, clip.fadeOutMs)
    return (v * 50f).roundToInt() / 50f
}

/** Plays one sound clip (music, voiceover, effect) in step with the playhead. It has no picture. */
@Composable
private fun AudioPreview(clip: TimelineClip, timelineMs: Long, playing: Boolean) {
    val context = LocalContext.current
    val player = remember(clip.id) {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val vol = clipVolumeNow(clip, timelineMs)
    LaunchedEffect(player, vol) { player.volume = vol }
    LaunchedEffect(player, clip.uri, clip.inMs, clip.lengthMs) {
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.inMs)
                        .setEndPositionMs(clip.inMs + clip.lengthMs)
                        .build()
                )
                .build()
        )
        player.prepare()
    }
    val want = (timelineMs - clip.startMs).coerceIn(0L, clip.lengthMs)
    LaunchedEffect(player, want, playing) {
        if (player.playbackState == Player.STATE_IDLE) return@LaunchedEffect
        val drift = abs(player.currentPosition - want)
        if (playing) {
            if (drift > 300L) player.seekTo(want)
            player.play()
        } else {
            player.pause()
        }
    }
}

@Composable
private fun OverlayImage(uri: String) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            MediaImporter.decodeSampled(context, Uri.parse(uri), 1080)?.asImageBitmap()
        }
    }
    bmp?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PreviewButton(icon: ImageVector, active: Boolean = false, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (active) CcAccent else Color.White,
        modifier = Modifier.size(36.dp).clickable(onClick = onClick).padding(8.dp)
    )
}

/** Size, opacity and position of the selected picture clip. */
@Composable
private fun OverlayPanel(clip: TimelineClip?, vm: EditorViewModel) {
    if (clip == null || clip.kind == ImportKind.AUDIO) {
        HintText(stringResource(R.string.ov_select_hint))
        return
    }
    LabeledSlider(stringResource(R.string.ov_size), clip.scale, TimelineOps.MIN_SCALE..1f,
        { vm.setClipTransform(clip.id, scale = it) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.ov_opacity), clip.opacity, 0f..1f,
        { vm.setClipTransform(clip.id, opacity = it) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.text_pos_x), clip.posX, -1f..1f,
        { vm.setClipTransform(clip.id, posX = it) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.text_pos_y), clip.posY, -1f..1f,
        { vm.setClipTransform(clip.id, posY = it) }, { vm.endGesture() })
    TextButton(
        onClick = {
            vm.setClipTransform(clip.id, posX = 0.5f, posY = -0.5f, scale = 0.35f, opacity = 1f)
            vm.endGesture()
        }
    ) { Text(stringResource(R.string.adj_reset)) }
    HintText(stringResource(R.string.ov_hint))
}

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
    val context = LocalContext.current
    val extractFail = stringResource(R.string.ed_extract_fail)
    TextButton(
        onClick = {
            if (target != null && !vm.extractAudioToTrack(target.id)) {
                Toast.makeText(context, extractFail, Toast.LENGTH_SHORT).show()
            }
        },
        enabled = hasAudio && target != null
    ) { Text(stringResource(R.string.ed_extract_track)) }
    if (!hasAudio) HintText(stringResource(R.string.editor_no_audio))
    else HintText(stringResource(R.string.ed_hint_clip))
}

/** Volume and fades of the selected extra clip (music, voiceover, or the sound of an added video). */
/** Fade through black between the selected clip and the next one. */
@Composable
private fun TransitionPanel(segments: List<Segment>, target: Segment?, vm: EditorViewModel) {
    val index = segments.indexOfFirst { it.id == target?.id }
    if (target == null || index < 0) {
        HintText(stringResource(R.string.trn_select_hint))
        return
    }
    val cap = Transitions.maxMs(segments, index)
    if (index == segments.lastIndex) {
        HintText(stringResource(R.string.trn_last_hint))
        return
    }
    if (cap < Transitions.MIN_MS) {
        HintText(stringResource(R.string.trn_short_hint))
        return
    }
    val current = Transitions.effective(segments, index)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == 0L,
            onClick = { vm.setTransition(target.id, 0L); vm.endGesture() },
            label = { Text(stringResource(R.string.trn_none)) }
        )
        FilterChip(
            selected = current > 0L,
            onClick = {
                vm.setTransition(target.id, minOf(600L, cap).coerceAtLeast(Transitions.MIN_MS))
                vm.endGesture()
            },
            label = { Text(stringResource(R.string.trn_fade)) }
        )
    }
    if (current > 0L) {
        LabeledSlider(
            stringResource(R.string.trn_duration, current / 1000f),
            current.toFloat(), Transitions.MIN_MS.toFloat()..cap.toFloat(),
            { vm.setTransition(target.id, it.toLong()) }, { vm.endGesture() }
        )
    }
    HintText(stringResource(R.string.trn_hint))
}

@Composable
private fun AudioPanel(clip: TimelineClip?, vm: EditorViewModel) {
    val canSound = clip != null && (clip.kind == ImportKind.AUDIO || clip.kind == ImportKind.VIDEO)
    if (clip == null || !canSound) {
        HintText(stringResource(R.string.aud_select_hint))
        return
    }
    val maxFade = (clip.lengthMs / 2L).coerceAtLeast(0L).toFloat()
    LabeledSlider(
        stringResource(R.string.aud_volume, (clip.volume * 100).roundToInt()),
        clip.volume, 0f..1f,
        { vm.setClipAudio(clip.id, volume = it) }, { vm.endGesture() }
    )
    if (maxFade > 0f) {
        LabeledSlider(
            stringResource(R.string.aud_fade_in, clip.fadeInMs / 1000f),
            clip.fadeInMs.toFloat().coerceAtMost(maxFade), 0f..maxFade,
            { vm.setClipAudio(clip.id, fadeInMs = it.toLong()) }, { vm.endGesture() }
        )
        LabeledSlider(
            stringResource(R.string.aud_fade_out, clip.fadeOutMs / 1000f),
            clip.fadeOutMs.toFloat().coerceAtMost(maxFade), 0f..maxFade,
            { vm.setClipAudio(clip.id, fadeOutMs = it.toLong()) }, { vm.endGesture() }
        )
    }
    HintText(stringResource(R.string.aud_hint))
}

/** Record your voice. The video plays while you record, so you can speak along with it. */
@Composable
private fun VoicePanel(
    startMs: Long,
    vm: EditorViewModel,
    onStartPlayback: () -> Unit,
    onStopPlayback: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val startAt = remember { mutableLongStateOf(0L) }
    val voiceName = stringResource(R.string.track_voiceover)
    val failText = stringResource(R.string.voice_fail)
    val deniedText = stringResource(R.string.voice_perm_denied)

    fun finish() {
        if (!recorder.isRecording) return
        recording = false
        onStopPlayback()
        val file = recorder.stop()
        if (file == null) {
            Toast.makeText(context, failText, Toast.LENGTH_SHORT).show()
        } else {
            vm.addVoiceover(file, startAt.longValue, voiceName) { ok ->
                if (!ok) Toast.makeText(context, failText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun begin() {
        if (recorder.start()) {
            startAt.longValue = startMs
            elapsedMs = 0L
            recording = true
            onStartPlayback()
        } else {
            Toast.makeText(context, failText, Toast.LENGTH_SHORT).show()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else Toast.makeText(context, deniedText, Toast.LENGTH_LONG).show()
    }

    // Leaving the panel while recording keeps what was recorded.
    DisposableEffect(Unit) { onDispose { finish() } }
    LaunchedEffect(recording) {
        while (recording) {
            delay(200)
            elapsedMs += 200
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                if (recording) {
                    finish()
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    begin()
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.size(56.dp).background(if (recording) Color(0xFFE53935) else CcChip, CircleShape)
        ) {
            Icon(
                imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = stringResource(if (recording) R.string.voice_stop else R.string.voice_record),
                tint = Color.White
            )
        }
        Text(
            text = if (recording) {
                stringResource(R.string.voice_recording, TimeFormat.elapsed(elapsedMs))
            } else {
                stringResource(R.string.voice_record)
            },
            color = Color.White,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    HintText(stringResource(R.string.voice_hint))
}

@Composable
private fun FiltersPanel(
    selected: FilterPreset,
    strength: Float,
    frame: ImageBitmap?,
    onSelect: (FilterPreset) -> Unit,
    onStrength: (Float) -> Unit,
    onStrengthDone: () -> Unit
) {
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
                val boxShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(boxShape)
                        .then(if (frame == null) Modifier.background(Brush.linearGradient(filterColors(f))) else Modifier)
                        .then(if (on) Modifier.border(2.dp, CcAccent, boxShape) else Modifier)
                ) {
                    if (frame != null) {
                        // A frame of your video with this look applied.
                        val matrix = remember(f) {
                            ColorFilter.colorMatrix(ColorMatrix(FilterMath.colorMatrix(FilterMath.params(f, 1f))))
                        }
                        Image(
                            bitmap = frame,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = matrix,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    text = stringResource(filterName(f)),
                    color = if (on) CcAccent else Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
    LabeledSlider(
        stringResource(R.string.filter_strength, (strength * 100).roundToInt()),
        strength, 0f..1f, onStrength, onStrengthDone
    )
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
    FilterPreset.CINEMATIC -> R.string.filter_cinematic
    FilterPreset.SUNSET -> R.string.filter_sunset
    FilterPreset.MINT -> R.string.filter_mint
    FilterPreset.NOIR -> R.string.filter_noir
    FilterPreset.DRAMA -> R.string.filter_drama
    FilterPreset.SOFT -> R.string.filter_soft
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
    FilterPreset.CINEMATIC -> listOf(Color(0xFF1E3A5F), Color(0xFFD98E4A))
    FilterPreset.SUNSET -> listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B))
    FilterPreset.MINT -> listOf(Color(0xFF38EF7D), Color(0xFF11998E))
    FilterPreset.NOIR -> listOf(Color(0xFF000000), Color(0xFF8A8A8A))
    FilterPreset.DRAMA -> listOf(Color(0xFF232526), Color(0xFFE5383B))
    FilterPreset.SOFT -> listOf(Color(0xFFFBD3E9), Color(0xFFBB377D))
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
    LabeledSlider(stringResource(R.string.adj_warmth), adjust.warmth, -1f..1f,
        { vm.setAdjust(adjust.copy(warmth = it)) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.adj_tint), adjust.tint, -1f..1f,
        { vm.setAdjust(adjust.copy(tint = it)) }, { vm.endGesture() })
    LabeledSlider(stringResource(R.string.adj_hue), adjust.hue, -1f..1f,
        { vm.setAdjust(adjust.copy(hue = it)) }, { vm.endGesture() })
    TextButton(
        onClick = {
            vm.setAdjust(Adjust(filterStrength = adjust.filterStrength))
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        FilterChip(
            selected = ui.flipH,
            onClick = { vm.toggleFlipH() },
            label = { Text(stringResource(R.string.editor_flip_h)) },
            leadingIcon = { Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = ui.flipV,
            onClick = { vm.toggleFlipV() },
            label = { Text(stringResource(R.string.editor_flip_v)) },
            leadingIcon = { Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
    }
}

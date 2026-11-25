package com.screenx.ai.presentation.editor

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
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
import com.screenx.ai.storage.ExportStorage
import com.screenx.ai.storage.RecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class VideoMeta(
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val bitrate: Int
)

/** What undo and redo bring back. */
private class Snapshot(
    val segments: List<Segment>,
    val filter: FilterPreset,
    val adjust: Adjust,
    val texts: List<TextItem>,
    val rotationCw: Int,
    val crop: CropPreset
)

@androidx.annotation.OptIn(UnstableApi::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Small preview frames for the timeline. A null entry has not been loaded yet. */
    private val _thumbs = MutableStateFlow<List<Bitmap?>>(emptyList())
    val thumbs: StateFlow<List<Bitmap?>> = _thumbs.asStateFlow()

    private var loadedUri: Uri? = null
    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var thumbJob: Job? = null
    private var tempFile: File? = null
    private var exportBaseName = ""
    private var nextId = 1L

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var gestureOpen = false

    fun load(uri: Uri) {
        if (loadedUri == uri) return
        loadedUri = uri
        _state.value = EditorUiState(uri = uri, loading = true)
        undoStack.clear()
        redoStack.clear()
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { readMeta(uri) }
            val defaultQuality = runCatching {
                ExportQuality.valueOf((app as ScreenXApp).settingsRepository.generalSettings.first().exportQuality)
            }.getOrDefault(ExportQuality.HIGH)
            if (meta == null || meta.durationMs <= 0L) {
                _state.update { it.copy(loading = false, error = R.string.editor_error_load) }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        title = meta.title,
                        durationMs = meta.durationMs,
                        width = meta.width,
                        height = meta.height,
                        hasAudio = meta.hasAudio,
                        sourceBitrate = meta.bitrate,
                        segments = listOf(Segment(nextId++, 0L, meta.durationMs)),
                        quality = defaultQuality
                    )
                }
                loadThumbnails(uri, meta.durationMs)
            }
        }
    }

    private fun readMeta(uri: Uri): VideoMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)
            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val t = w
                w = h
                h = t
            }
            val hasAudio = meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            VideoMeta(displayName(uri), duration, w, h, hasAudio, bitrate)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "video"
        return name.substringBeforeLast('.')
    }

    /** Grabs evenly spaced frames of the whole source for the timeline strip. */
    private fun loadThumbnails(uri: Uri, durationMs: Long) {
        thumbJob?.cancel()
        val count = (durationMs / 1500L).toInt().coerceIn(8, 60)
        _thumbs.value = List(count) { null }
        thumbJob = viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(app, uri)
                val frames = arrayOfNulls<Bitmap>(count)
                for (i in 0 until count) {
                    if (!isActive) break
                    val timeUs = durationMs * (2L * i + 1L) / (2L * count) * 1000L
                    val frame = runCatching {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }.getOrNull()
                    if (frame != null) {
                        val targetH = 96
                        val targetW = (frame.width * targetH / frame.height.coerceAtLeast(1)).coerceAtLeast(1)
                        frames[i] = if (frame.height > targetH) {
                            Bitmap.createScaledBitmap(frame, targetW, targetH, true).also {
                                if (it !== frame) frame.recycle()
                            }
                        } else {
                            frame
                        }
                    }
                    if (i % 3 == 2 || i == count - 1) _thumbs.value = frames.toList()
                }
            } catch (e: Exception) {
                // Thumbnails are cosmetic. The timeline still works without them.
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    // ---- undo / redo ----

    private fun snapshot(s: EditorUiState) = Snapshot(s.segments, s.filter, s.adjust, s.texts, s.rotationCw, s.crop)

    /**
     * Applies an edit and remembers the previous state for undo.
     * A [continuous] edit (slider drag, typing) only remembers the state before the first change,
     * until [endGesture] is called.
     */
    private fun mutate(continuous: Boolean = false, block: (EditorUiState) -> EditorUiState) {
        val cur = _state.value
        if (!continuous || !gestureOpen) {
            undoStack.addLast(snapshot(cur))
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
        }
        gestureOpen = continuous
        _state.value = block(cur).copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
    }

    fun endGesture() {
        gestureOpen = false
    }

    private fun restore(cur: EditorUiState, snap: Snapshot): EditorUiState = cur.copy(
        segments = snap.segments,
        filter = snap.filter,
        adjust = snap.adjust,
        texts = snap.texts,
        rotationCw = snap.rotationCw,
        crop = snap.crop,
        selectedId = cur.selectedId?.takeIf { id -> snap.segments.any { it.id == id } },
        selectedTextId = cur.selectedTextId?.takeIf { id -> snap.texts.any { it.id == id } }
    )

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        gestureOpen = false
        val cur = _state.value
        redoStack.addLast(snapshot(cur))
        _state.value = restore(cur, prev).copy(canUndo = undoStack.isNotEmpty(), canRedo = true)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        gestureOpen = false
        val cur = _state.value
        undoStack.addLast(snapshot(cur))
        _state.value = restore(cur, next).copy(canUndo = true, canRedo = redoStack.isNotEmpty())
    }

    // ---- clips ----

    fun select(id: Long?) {
        _state.update { it.copy(selectedId = id) }
    }

    /** Cuts the clip under timeline time [timelineMs] in two. Returns false when a piece would be too short. */
    fun split(timelineMs: Long): Boolean {
        val st = _state.value
        val loc = EditTimeline.locate(st.segments, timelineMs) ?: return false
        val seg = st.segments[loc.index]
        if (loc.sourceMs - seg.startMs < MIN_CLIP_MS || seg.endMs - loc.sourceMs < MIN_CLIP_MS) return false
        val first = seg.copy(endMs = loc.sourceMs)
        val second = seg.copy(id = nextId++, startMs = loc.sourceMs)
        mutate { s ->
            val list = s.segments.toMutableList()
            list.removeAt(loc.index)
            list.add(loc.index, first)
            list.add(loc.index + 1, second)
            s.copy(segments = list, selectedId = second.id)
        }
        return true
    }

    /** Removes a clip. The last remaining clip cannot be removed. */
    fun deleteSegment(id: Long): Boolean {
        val st = _state.value
        if (st.segments.size <= 1 || st.segments.none { it.id == id }) return false
        mutate { s -> s.copy(segments = s.segments.filter { it.id != id }, selectedId = null) }
        return true
    }

    fun setSpeed(id: Long, speed: Float) {
        mutate { s -> s.copy(segments = s.segments.map { if (it.id == id) it.copy(speed = speed) else it }) }
    }

    fun setVolume(id: Long, volume: Float) {
        mutate(continuous = true) { s ->
            s.copy(segments = s.segments.map { if (it.id == id) it.copy(volume = volume.coerceIn(0f, 2f)) else it })
        }
    }

    /** Moves the edges of a clip, in source time. */
    fun trimSegment(id: Long, startMs: Long, endMs: Long) {
        mutate(continuous = true) { s ->
            s.copy(
                segments = s.segments.map { seg ->
                    if (seg.id != id) {
                        seg
                    } else {
                        val a = startMs.coerceIn(0L, s.durationMs)
                        val b = endMs.coerceIn(0L, s.durationMs)
                        if (b - a < MIN_CLIP_MS) seg else seg.copy(startMs = a, endMs = b)
                    }
                }
            )
        }
    }

    // ---- look ----

    fun setFilter(filter: FilterPreset) { mutate { it.copy(filter = filter) } }

    fun setAdjust(adjust: Adjust) { mutate(continuous = true) { it.copy(adjust = adjust) } }

    /** [deltaCw] is +90 for clockwise, -90 for counterclockwise. */
    fun rotate(deltaCw: Int) {
        mutate { it.copy(rotationCw = ((it.rotationCw + deltaCw) % 360 + 360) % 360) }
    }

    fun setCrop(crop: CropPreset) { mutate { it.copy(crop = crop) } }

    fun setQuality(quality: ExportQuality) { _state.update { it.copy(quality = quality) } }

    // ---- text ----

    fun addText(defaultText: String) {
        val item = TextItem(id = nextId++, text = defaultText)
        mutate { it.copy(texts = it.texts + item, selectedTextId = item.id) }
    }

    fun selectText(id: Long?) {
        _state.update { it.copy(selectedTextId = id) }
    }

    fun updateText(id: Long, change: (TextItem) -> TextItem) {
        mutate(continuous = true) { s ->
            s.copy(texts = s.texts.map { if (it.id == id) change(it) else it })
        }
    }

    fun removeText(id: Long) {
        mutate { s ->
            s.copy(
                texts = s.texts.filter { it.id != id },
                selectedTextId = if (s.selectedTextId == id) null else s.selectedTextId
            )
        }
    }

    fun dismissResult() { _state.update { it.copy(export = ExportState.Idle) } }

    // ---- export ----

    fun exportVideo() = startExport(audioOnly = false)
    fun extractAudio() = startExport(audioOnly = true)

    private fun look(s: EditorUiState) = LookSettings(s.rotationCw, s.crop, s.filter, s.adjust, s.texts)

    private fun buildItem(
        uri: Uri,
        seg: Segment,
        audioOnly: Boolean,
        videoEffects: List<Effect>
    ): EditedMediaItem {
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(seg.startMs)
            .setEndPositionMs(seg.endMs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()

        val audio = ArrayList<AudioProcessor>()
        if (seg.volume != 1f) {
            val mixer = ChannelMixingAudioProcessor()
            mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(seg.volume))
            mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(seg.volume))
            audio.add(mixer)
        }
        if (seg.speed != 1f) {
            val sonic = SonicAudioProcessor()
            sonic.setSpeed(seg.speed)
            sonic.setPitch(1f)
            audio.add(sonic)
        }

        val video = ArrayList<Effect>()
        if (!audioOnly) {
            if (seg.speed != 1f) video.add(SpeedChangeEffect(seg.speed))
            video.addAll(videoEffects)
        }
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(audioOnly)
            .setEffects(Effects(audio, video))
            .build()
    }

    private fun startExport(audioOnly: Boolean) {
        val s = _state.value
        val uri = s.uri ?: return
        if (s.export is ExportState.Running) return
        if (s.segments.isEmpty()) return
        if (audioOnly && !s.hasAudio) {
            _state.update { it.copy(export = ExportState.Failed(app.getString(R.string.editor_no_audio))) }
            return
        }

        val builder = Transformer.Builder(app).addListener(listener)
        val plan = EditEffects.exportPlan(s.width, s.height, look(s), s.quality)
        if (!audioOnly) {
            val encoder = DefaultEncoderFactory.Builder(app)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(chooseBitrate(s)).build())
                .build()
            builder.setEncoderFactory(encoder).setVideoMimeType(MimeTypes.VIDEO_H264)
        }

        val items = s.segments.map { buildItem(uri, it, audioOnly, plan.effects) }
        val composition = Composition.Builder(EditedMediaItemSequence(items)).build()

        val temp = File(app.cacheDir, "${RecordingStorage.TEMP_PREFIX}export_${System.currentTimeMillis()}.mp4")
        tempFile = temp
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        exportBaseName = if (audioOnly) "${s.title}_audio_$stamp.m4a" else "${s.title}_edited_$stamp.mp4"

        val t = builder.build()
        transformer = t
        _state.update { it.copy(export = ExportState.Running(0, audioOnly)) }
        try {
            t.start(composition, temp.absolutePath)
        } catch (e: Exception) {
            onExportFailed(e.message ?: e.javaClass.simpleName)
            return
        }
        progressJob = viewModelScope.launch {
            val holder = ProgressHolder()
            while (isActive) {
                val st = t.getProgress(holder)
                if (st == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _state.update {
                        val running = it.export as? ExportState.Running
                        if (running != null) it.copy(export = running.copy(progress = holder.progress)) else it
                    }
                }
                delay(250)
            }
        }
    }

    private fun chooseBitrate(s: EditorUiState): Int {
        val source = s.sourceBitrate
        return if (s.quality == ExportQuality.ORIGINAL) {
            if (source > 0) source.coerceIn(2_000_000, 40_000_000) else 8_000_000
        } else {
            val preset = s.quality.bitrate
            (if (source > 0) minOf(preset, source) else preset).coerceAtLeast(1_000_000)
        }
    }

    private val listener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            onExportCompleted()
        }

        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            onExportFailed(exportException.errorCodeName)
        }
    }

    private fun onExportCompleted() {
        progressJob?.cancel()
        val temp = tempFile
        val audioOnly = (_state.value.export as? ExportState.Running)?.audioOnly ?: false
        val name = exportBaseName
        viewModelScope.launch {
            val saved = if (temp != null) {
                withContext(Dispatchers.IO) { ExportStorage.save(app, temp, name, audioOnly) }
            } else {
                null
            }
            temp?.delete()
            tempFile = null
            _state.update {
                it.copy(
                    export = if (saved != null) {
                        ExportState.Done(saved.name, saved.uri?.toString(), audioOnly)
                    } else {
                        ExportState.Failed(app.getString(R.string.editor_save_failed))
                    }
                )
            }
        }
    }

    private fun onExportFailed(message: String) {
        progressJob?.cancel()
        tempFile?.delete()
        tempFile = null
        _state.update { it.copy(export = ExportState.Failed(message)) }
    }

    fun cancelExport() {
        runCatching { transformer?.cancel() }
        progressJob?.cancel()
        tempFile?.delete()
        tempFile = null
        _state.update { it.copy(export = ExportState.Idle) }
    }

    override fun onCleared() {
        runCatching { transformer?.cancel() }
        progressJob?.cancel()
        thumbJob?.cancel()
        tempFile?.delete()
        super.onCleared()
    }

    companion object {
        const val MIN_CLIP_MS = 300L
        private const val MAX_UNDO = 50
    }
}

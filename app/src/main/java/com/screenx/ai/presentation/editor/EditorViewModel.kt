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
import androidx.media3.effect.RgbMatrix
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
import com.screenx.ai.editor.ImportKind
import com.screenx.ai.editor.ImportedMedia
import com.screenx.ai.editor.Segment
import com.screenx.ai.editor.SegmentOps
import com.screenx.ai.editor.TimelineClip
import com.screenx.ai.editor.TimelineOps
import com.screenx.ai.editor.Track
import com.screenx.ai.editor.TrackKind
import com.screenx.ai.editor.Transitions
import com.screenx.ai.editor.WaveformExtractor
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** Result of putting a video on the main track. */
enum class MainAddResult { OK, NOT_VIDEO, AUDIO_MISMATCH }

/** What undo and redo bring back. */
private class Snapshot(
    val segments: List<Segment>,
    val filter: FilterPreset,
    val adjust: Adjust,
    val texts: List<TextItem>,
    val rotationCw: Int,
    val flipH: Boolean,
    val flipV: Boolean,
    val crop: CropPreset,
    val tracks: List<Track>
)

@androidx.annotation.OptIn(UnstableApi::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /** Small preview frames for the timeline. A null entry has not been loaded yet. */
    private val _thumbs = MutableStateFlow<Map<String, List<Bitmap?>>>(emptyMap())
    val thumbs: StateFlow<Map<String, List<Bitmap?>>> = _thumbs.asStateFlow()

    private var loadedUri: Uri? = null
    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private val thumbJobs = HashMap<String, Job>()

    /** Loudness pictures of sound files, by file: one value per [WaveformExtractor.BUCKET_MS]. */
    private val _waves = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val waves: StateFlow<Map<String, FloatArray>> = _waves.asStateFlow()
    private val waveStarted = HashSet<String>()
    private val waveGate = Semaphore(1)
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
                        segments = listOf(
                            Segment(
                                id = nextId++,
                                startMs = 0L,
                                endMs = meta.durationMs,
                                uri = uri.toString(),
                                name = meta.title,
                                mediaDurationMs = meta.durationMs,
                                width = meta.width,
                                height = meta.height
                            )
                        ),
                        quality = defaultQuality
                    )
                }
                loadThumbnails(uri, meta.durationMs)
                if (meta.hasAudio) ensureWaveform(uri.toString(), meta.durationMs)
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
        val key = uri.toString()
        if (_thumbs.value.containsKey(key)) return
        val count = (durationMs / 1500L).toInt().coerceIn(8, 60)
        _thumbs.update { it + (key to List<Bitmap?>(count) { null }) }
        thumbJobs[key] = viewModelScope.launch(Dispatchers.IO) {
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
                    if (i % 3 == 2 || i == count - 1) _thumbs.update { it + (key to frames.toList()) }
                }
            } catch (e: Exception) {
                // Thumbnails are cosmetic. The timeline still works without them.
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    // ---- undo / redo ----

    private fun snapshot(s: EditorUiState) =
        Snapshot(s.segments, s.filter, s.adjust, s.texts, s.rotationCw, s.flipH, s.flipV, s.crop, s.tracks)

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
        flipH = snap.flipH,
        flipV = snap.flipV,
        crop = snap.crop,
        tracks = snap.tracks,
        selectedClipId = cur.selectedClipId?.takeIf { id -> TimelineOps.find(snap.tracks, id) != null },
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
        _state.update { it.copy(selectedId = id, selectedClipId = if (id != null) null else it.selectedClipId) }
    }

    // ---- extra tracks (clips placed freely on the timeline) ----

    fun selectClip(id: Long?) {
        _state.update { it.copy(selectedClipId = id, selectedId = if (id != null) null else it.selectedId) }
    }

    /** Places an imported file on a track of [kind] at timeline time [startMs]. Returns false for a wrong kind. */
    fun addClip(media: ImportedMedia, kind: TrackKind, startMs: Long): Boolean {
        if (!TimelineOps.compatible(media.kind, kind)) return false
        val image = media.kind == ImportKind.IMAGE
        val length = if (image) TimelineOps.IMAGE_DEFAULT_MS else media.durationMs
        if (length <= 0L) return false
        val clip = TimelineClip(
            id = nextId++,
            trackId = 0L,
            uri = media.uri.toString(),
            name = media.name,
            kind = media.kind,
            startMs = startMs.coerceAtLeast(0L),
            inMs = 0L,
            lengthMs = length,
            mediaDurationMs = if (image) 0L else media.durationMs,
            aspect = if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 0f,
            // A video on an extra track starts silent; raise it in the Audio panel.
            volume = if (media.kind == ImportKind.VIDEO) 0f else 1f
        )
        if (media.kind == ImportKind.AUDIO) ensureWaveform(media.uri.toString(), media.durationMs)
        val newTrackId = nextId++
        mutate { s ->
            s.copy(
                tracks = TimelineOps.add(s.tracks, kind, clip, newTrackId),
                selectedClipId = clip.id,
                selectedId = null
            )
        }
        return true
    }

    fun deleteClip(id: Long) {
        if (TimelineOps.find(_state.value.tracks, id) == null) return
        mutate { s -> s.copy(tracks = TimelineOps.delete(s.tracks, id), selectedClipId = null) }
    }

    /** Copies a clip right after the original (on a free track of the same kind). */
    fun duplicateClip(id: Long) {
        val st = _state.value
        val clip = TimelineOps.find(st.tracks, id) ?: return
        val kind = st.tracks.firstOrNull { it.id == clip.trackId }?.kind ?: return
        val copy = clip.copy(id = nextId++, startMs = clip.endMs)
        val newTrackId = nextId++
        mutate { s -> s.copy(tracks = TimelineOps.add(s.tracks, kind, copy, newTrackId), selectedClipId = copy.id) }
    }

    /** Cuts a clip at timeline time [atMs]. Returns false when a piece would be too short. */
    fun splitClip(id: Long, atMs: Long): Boolean {
        val newId = nextId
        val result = TimelineOps.split(_state.value.tracks, id, atMs, newId) ?: return false
        nextId++
        mutate { s -> s.copy(tracks = result, selectedClipId = newId) }
        return true
    }

    /** Where, how big and how opaque a picture clip is on the frame. Values left null stay as they are. */
    fun setClipTransform(id: Long, posX: Float? = null, posY: Float? = null, scale: Float? = null, opacity: Float? = null) {
        mutate(continuous = true) { s ->
            s.copy(tracks = TimelineOps.transform(s.tracks, id, posX, posY, scale, opacity))
        }
    }

    /** Volume (0..1) and fades of a clip. Values left null stay as they are. */
    fun setClipAudio(id: Long, volume: Float? = null, fadeInMs: Long? = null, fadeOutMs: Long? = null) {
        mutate(continuous = true) { s ->
            s.copy(tracks = TimelineOps.audio(s.tracks, id, volume, fadeInMs, fadeOutMs))
        }
    }

    /**
     * Copies the sound of a main-track clip to an audio track, at the same time on the timeline.
     * The clip itself keeps its sound. Needs speed 1x and a clip with sound.
     */
    fun extractAudioToTrack(segmentId: Long): Boolean {
        val st = _state.value
        val index = st.segments.indexOfFirst { it.id == segmentId }
        val seg = st.segments.getOrNull(index) ?: return false
        if (!st.hasAudio || seg.speed != 1f) return false
        val clip = TimelineClip(
            id = nextId++,
            trackId = 0L,
            uri = seg.uri,
            name = seg.name,
            kind = ImportKind.AUDIO,
            startMs = EditTimeline.startOf(st.segments, index),
            inMs = seg.startMs,
            lengthMs = seg.sourceMs,
            mediaDurationMs = seg.mediaDurationMs,
            volume = seg.volume.coerceIn(0f, 1f)
        )
        val newTrackId = nextId++
        mutate { s ->
            s.copy(
                tracks = TimelineOps.add(s.tracks, TrackKind.AUDIO, clip, newTrackId),
                selectedClipId = clip.id,
                selectedId = null
            )
        }
        ensureWaveform(seg.uri, seg.mediaDurationMs)
        return true
    }

    /** Puts a finished voice recording on a voiceover track, starting at [startMs]. Returns false when it is unreadable. */
    fun addVoiceover(file: File, startMs: Long, name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                } finally {
                    runCatching { r.release() }
                }
            }
            if (duration < TimelineOps.MIN_CLIP_MS) {
                file.delete()
                onDone(false)
                return@launch
            }
            val uriString = Uri.fromFile(file).toString()
            val clip = TimelineClip(
                id = nextId++,
                trackId = 0L,
                uri = uriString,
                name = name,
                kind = ImportKind.AUDIO,
                startMs = startMs.coerceAtLeast(0L),
                inMs = 0L,
                lengthMs = duration,
                mediaDurationMs = duration
            )
            val newTrackId = nextId++
            mutate { s ->
                s.copy(
                    tracks = TimelineOps.add(s.tracks, TrackKind.VOICEOVER, clip, newTrackId),
                    selectedClipId = clip.id,
                    selectedId = null
                )
            }
            ensureWaveform(uriString, duration)
            onDone(true)
        }
    }

    /** Measures the loudness of a sound file in the background (one file at a time) for its waveform. */
    private fun ensureWaveform(uri: String, durationMs: Long) {
        if (!waveStarted.add(uri)) return
        viewModelScope.launch(Dispatchers.IO) {
            val peaks = waveGate.withPermit {
                WaveformExtractor.extract(app, Uri.parse(uri), durationMs) { isActive }
            }
            if (peaks != null) _waves.update { it + (uri to peaks) }
        }
    }

    fun moveClip(id: Long, startMs: Long) {
        mutate(continuous = true) { s -> s.copy(tracks = TimelineOps.move(s.tracks, id, startMs)) }
    }

    fun trimClipStart(id: Long, startMs: Long) {
        mutate(continuous = true) { s -> s.copy(tracks = TimelineOps.trimStart(s.tracks, id, startMs)) }
    }

    fun trimClipEnd(id: Long, endMs: Long) {
        mutate(continuous = true) { s -> s.copy(tracks = TimelineOps.trimEnd(s.tracks, id, endMs)) }
    }

    /** Cuts the clip under timeline time [timelineMs] in two. Returns false when a piece would be too short. */
    fun split(timelineMs: Long): Boolean {
        val st = _state.value
        val loc = EditTimeline.locate(st.segments, timelineMs) ?: return false
        val seg = st.segments[loc.index]
        if (loc.sourceMs - seg.startMs < MIN_CLIP_MS || seg.endMs - loc.sourceMs < MIN_CLIP_MS) return false
        // The first piece is now followed by the second, so the fade moves to the end of the second.
        val first = seg.copy(endMs = loc.sourceMs, transitionMs = 0L)
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

    /**
     * Adds an imported video to the main track, after clip [afterIndex].
     * It must have sound exactly when the first video has, so the export can join them.
     */
    fun addToMain(media: ImportedMedia, afterIndex: Int): MainAddResult {
        val st = _state.value
        if (media.kind != ImportKind.VIDEO || media.durationMs <= 0L || media.width <= 0 || media.height <= 0) {
            return MainAddResult.NOT_VIDEO
        }
        if (media.hasAudio != st.hasAudio) return MainAddResult.AUDIO_MISMATCH
        val seg = Segment(
            id = nextId++,
            startMs = 0L,
            endMs = media.durationMs,
            uri = media.uri.toString(),
            name = media.name.substringBeforeLast('.'),
            mediaDurationMs = media.durationMs,
            width = media.width,
            height = media.height
        )
        mutate { s ->
            s.copy(segments = SegmentOps.insertAfter(s.segments, afterIndex, seg), selectedId = seg.id, selectedClipId = null)
        }
        loadThumbnails(media.uri, media.durationMs)
        if (media.hasAudio) ensureWaveform(media.uri.toString(), media.durationMs)
        return MainAddResult.OK
    }

    /** Copies a clip right after the original. */
    fun duplicateSegment(id: Long) {
        val newId = nextId
        val list = SegmentOps.duplicate(_state.value.segments, id, newId) ?: return
        nextId++
        mutate { s -> s.copy(segments = list, selectedId = newId) }
    }

    /** Moves a clip one place earlier ([delta] = -1) or later (+1). */
    fun moveSegment(id: Long, delta: Int) {
        val list = SegmentOps.move(_state.value.segments, id, delta) ?: return
        mutate { s -> s.copy(segments = list) }
    }

    /** Joins a clip with the one after it. Returns false when they do not fit together. */
    fun mergeSegment(id: Long): Boolean {
        val list = SegmentOps.mergeWithNext(_state.value.segments, id) ?: return false
        mutate { s -> s.copy(segments = list) }
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

    /** Sets the fade through black after clip [id] (0 = a plain cut). */
    fun setTransition(id: Long, ms: Long) {
        mutate(continuous = true) { s ->
            s.copy(segments = s.segments.map { if (it.id == id) it.copy(transitionMs = ms.coerceAtLeast(0L)) else it })
        }
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
                        val limit = if (seg.mediaDurationMs > 0L) seg.mediaDurationMs else s.durationMs
                        val a = startMs.coerceIn(0L, limit)
                        val b = endMs.coerceIn(0L, limit)
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

    fun toggleFlipH() { mutate { it.copy(flipH = !it.flipH) } }

    fun toggleFlipV() { mutate { it.copy(flipV = !it.flipV) } }

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

    private fun look(s: EditorUiState) =
        LookSettings(s.rotationCw, s.crop, s.filter, s.adjust, s.texts, s.flipH, s.flipV)

    private fun buildItem(
        uri: Uri,
        seg: Segment,
        audioOnly: Boolean,
        videoEffects: List<Effect>,
        fade: Effect? = null
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
            if (fade != null) video.add(fade)
        }
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(audioOnly)
            .setEffects(Effects(audio, video))
            .build()
    }

    private fun startExport(audioOnly: Boolean) {
        val s = _state.value
        if (s.uri == null) return
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

        val items = s.segments.mapIndexed { index, seg ->
            // A clip of another size is fitted into the project's picture size first.
            val fx = if (seg.width != s.width || seg.height != s.height) {
                listOf(EditEffects.fitToCanvas(s.width, s.height)) + plan.effects
            } else {
                plan.effects
            }
            // Fade through black at the cuts that have a transition. The effect sees the time inside this clip.
            val fade: Effect? = if (!audioOnly && Transitions.touches(s.segments, index)) {
                val list = s.segments
                RgbMatrix { presentationTimeUs, _ ->
                    val g = Transitions.gain(list, index, presentationTimeUs / 1000L)
                    floatArrayOf(
                        g, 0f, 0f, 0f,
                        0f, g, 0f, 0f,
                        0f, 0f, g, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            } else {
                null
            }
            buildItem(Uri.parse(seg.uri), seg, audioOnly, fx, fade)
        }
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
        thumbJobs.values.forEach { it.cancel() }
        tempFile?.delete()
        super.onCleared()
    }

    companion object {
        const val MIN_CLIP_MS = 300L
        private const val MAX_UNDO = 50
    }
}

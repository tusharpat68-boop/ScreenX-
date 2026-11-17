package com.screenx.ai.presentation.editor

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
import kotlinx.coroutines.flow.first
import com.screenx.ai.editor.CropPreset
import com.screenx.ai.editor.EditEffects
import com.screenx.ai.editor.EditorUiState
import com.screenx.ai.editor.ExportQuality
import com.screenx.ai.editor.ExportState
import com.screenx.ai.storage.ExportStorage
import com.screenx.ai.storage.RecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@androidx.annotation.OptIn(UnstableApi::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var loadedUri: Uri? = null
    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var tempFile: File? = null
    private var exportBaseName = ""

    fun load(uri: Uri) {
        if (loadedUri == uri) return
        loadedUri = uri
        _state.value = EditorUiState(uri = uri, loading = true)
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
                        trimStartMs = 0L,
                        trimEndMs = meta.durationMs,
                        quality = defaultQuality
                    )
                }
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

    // ---- edit actions ----

    fun setTrim(startMs: Long, endMs: Long) {
        _state.update {
            val s = startMs.coerceIn(0L, it.durationMs)
            val e = endMs.coerceIn(0L, it.durationMs)
            if (e - s < MIN_CLIP_MS) it else it.copy(trimStartMs = s, trimEndMs = e)
        }
    }

    /** [deltaCw] is +90 for clockwise, -90 for counterclockwise. */
    fun rotate(deltaCw: Int) {
        _state.update { it.copy(rotationCw = ((it.rotationCw + deltaCw) % 360 + 360) % 360) }
    }

    fun setCrop(crop: CropPreset) { _state.update { it.copy(crop = crop) } }
    fun setQuality(quality: ExportQuality) { _state.update { it.copy(quality = quality) } }
    fun dismissResult() { _state.update { it.copy(export = ExportState.Idle) } }

    // ---- export ----

    fun exportVideo() = startExport(audioOnly = false)
    fun extractAudio() = startExport(audioOnly = true)

    private fun startExport(audioOnly: Boolean) {
        val s = _state.value
        val uri = s.uri ?: return
        if (s.export is ExportState.Running) return
        if (audioOnly && !s.hasAudio) {
            _state.update { it.copy(export = ExportState.Failed(app.getString(R.string.editor_no_audio))) }
            return
        }

        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(s.trimStartMs)
            .setEndPositionMs(s.trimEndMs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()

        val edited = EditedMediaItem.Builder(mediaItem)
        val builder = Transformer.Builder(app).addListener(listener)
        if (audioOnly) {
            edited.setRemoveVideo(true)
        } else {
            val plan = EditEffects.exportPlan(s.width, s.height, s.rotationCw, s.crop, s.quality)
            edited.setEffects(Effects(emptyList(), plan.effects))
            val bitrate = chooseBitrate(s)
            val encoder = DefaultEncoderFactory.Builder(app)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                .build()
            builder.setEncoderFactory(encoder).setVideoMimeType(MimeTypes.VIDEO_H264)
        }

        val temp = File(app.cacheDir, "${RecordingStorage.TEMP_PREFIX}export_${System.currentTimeMillis()}.mp4")
        tempFile = temp
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        exportBaseName = if (audioOnly) "${s.title}_audio_$stamp.m4a" else "${s.title}_edited_$stamp.mp4"

        val t = builder.build()
        transformer = t
        _state.update { it.copy(export = ExportState.Running(0, audioOnly)) }
        try {
            t.start(edited.build(), temp.absolutePath)
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
        tempFile?.delete()
        super.onCleared()
    }

    companion object {
        const val MIN_CLIP_MS = 500L
    }
}

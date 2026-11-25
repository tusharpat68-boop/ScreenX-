package com.screenx.ai.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.max

enum class ImportKind { VIDEO, IMAGE, AUDIO }

/**
 * A file the user brought into the editor. Only the location and a few facts are kept;
 * the media itself is never loaded into memory ([thumb] is a small preview picture).
 */
data class ImportedMedia(
    val id: Long,
    val uri: Uri,
    val name: String,
    val kind: ImportKind,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val thumb: Bitmap?
)

/**
 * The media of the project being edited. It lives as long as the app process, so it
 * survives leaving and re-entering the editor tab.
 */
object ProjectMediaPool {
    private val _items = MutableStateFlow<List<ImportedMedia>>(emptyList())
    val items: StateFlow<List<ImportedMedia>> = _items.asStateFlow()

    fun add(list: List<ImportedMedia>) {
        if (list.isEmpty()) return
        _items.update { cur ->
            val known = cur.map { it.uri }.toSet()
            cur + list.filter { it.uri !in known }
        }
    }

    fun remove(id: Long) {
        _items.update { cur -> cur.filter { it.id != id } }
    }

    fun clear() {
        _items.value = emptyList()
    }

    fun firstVideo(): ImportedMedia? = _items.value.firstOrNull { it.kind == ImportKind.VIDEO }
}

/** Reads a picked file's facts (type, length, size, picture size) without decoding it fully. */
object MediaImporter {
    val VIDEO_EXT = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "3gpp")
    val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
    val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "3ga")

    /** MIME types offered in the file manager picker. */
    val FILE_MIME_TYPES = arrayOf("video/*", "image/*", "audio/*")
    val AUDIO_MIME_TYPES = arrayOf("audio/*")

    private const val THUMB_PX = 192
    private var nextId = 1L

    /** Returns null when the file is not readable or not a supported kind of media. */
    fun probe(context: Context, uri: Uri): ImportedMedia? {
        keepAccess(context, uri)
        val (name, size) = nameAndSize(context, uri)
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase(Locale.ROOT)
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val kind = when {
            mime?.startsWith("video/") == true || ext in VIDEO_EXT -> ImportKind.VIDEO
            mime?.startsWith("image/") == true || ext in IMAGE_EXT -> ImportKind.IMAGE
            mime?.startsWith("audio/") == true || ext in AUDIO_EXT -> ImportKind.AUDIO
            else -> return null
        }
        val id = synchronized(this) { nextId++ }
        return when (kind) {
            ImportKind.IMAGE -> probeImage(context, uri, id, name, size)
            else -> probeTimed(context, uri, id, name, size, kind)
        }
    }

    /** Asks to keep read access so a saved project can open the file again later. */
    private fun keepAccess(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun nameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                    size = if (it.isNull(1)) 0L else it.getLong(1)
                }
            }
        }
        return (name ?: uri.lastPathSegment ?: "media") to size
    }

    private fun probeTimed(
        context: Context,
        uri: Uri,
        id: Long,
        name: String,
        size: Long,
        kind: ImportKind
    ): ImportedMedia? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (duration <= 0L) return null
            var w = 0
            var h = 0
            var thumb: Bitmap? = null
            if (kind == ImportKind.VIDEO) {
                w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) {
                    val t = w
                    w = h
                    h = t
                }
                if (w <= 0 || h <= 0) return null
                thumb = runCatching {
                    r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaleDown(it) }
                }.getOrNull()
            }
            ImportedMedia(id, uri, name, kind, duration, w, h, size, thumb)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun probeImage(context: Context, uri: Uri, id: Long, name: String, size: Long): ImportedMedia? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val thumb = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ImportedMedia(id, uri, name, ImportKind.IMAGE, 0L, bounds.outWidth, bounds.outHeight, size, thumb)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val scale = THUMB_PX.toFloat() / max(src.width, src.height)
        if (scale >= 1f) return src
        val out = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (out !== src) src.recycle()
        return out
    }
}

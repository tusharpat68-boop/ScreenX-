package com.screenx.ai.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.screenx.ai.domain.MediaItem
import com.screenx.ai.domain.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Loads and caches thumbnails and preview images. */
object ThumbnailLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun key(item: MediaItem, sizePx: Int) = "${item.id}@$sizePx@${item.fileName}"

    fun peek(item: MediaItem, sizePx: Int): Bitmap? = cache.get(key(item, sizePx))

    suspend fun load(context: Context, item: MediaItem, sizePx: Int, isVideo: Boolean): Bitmap? {
        cache.get(key(item, sizePx))?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                if (isVideo) videoThumb(context, item, sizePx) else decodeSampled(context, item, sizePx)
            }.getOrNull()
        }
        if (bmp != null) cache.put(key(item, sizePx), bmp)
        return bmp
    }

    /** A large version for the full-screen preview (not cached, it can be big). */
    suspend fun loadFull(context: Context, item: MediaItem, maxPx: Int): Bitmap? =
        withContext(Dispatchers.IO) { runCatching { decodeSampled(context, item, maxPx) }.getOrNull() }

    private fun videoThumb(context: Context, item: MediaItem, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.file == null) {
            return context.contentResolver.loadThumbnail(item.uri, Size(sizePx, sizePx * 9 / 16), null)
        }
        val r = MediaMetadataRetriever()
        try {
            val f = item.file
            if (f != null) r.setDataSource(f.absolutePath) else r.setDataSource(context, item.uri)
            val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scale = sizePx.toFloat() / max(frame.width, frame.height)
            return if (scale < 1f) {
                Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
            } else {
                frame
            }
        } finally {
            runCatching { r.release() }
        }
    }

    private fun decodeSampled(context: Context, item: MediaItem, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, item)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open(context, item)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun open(context: Context, item: MediaItem) =
        item.file?.inputStream() ?: context.contentResolver.openInputStream(item.uri)

    @Suppress("unused")
    private fun kindOf(item: MediaItem): MediaKind = if (item.durationMs > 0) MediaKind.VIDEO else MediaKind.IMAGE
}

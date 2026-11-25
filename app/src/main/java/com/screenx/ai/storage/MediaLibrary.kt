package com.screenx.ai.storage

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.screenx.ai.domain.MediaItem
import com.screenx.ai.domain.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class FailReason { INVALID_NAME, NAME_EXISTS, OPERATION_FAILED }

sealed interface LibraryResult {
    data class Done(val newFileName: String? = null) : LibraryResult

    /** Android wants the user to approve this change. Run [sender], then retry if [retry]. */
    class NeedsApproval(val sender: IntentSender, val retry: Boolean) : LibraryResult

    data class Failed(val reason: FailReason) : LibraryResult
}

/**
 * Lists, renames, deletes and shares the app's recordings or screenshots.
 * Android 10+: MediaStore (Movies/ScreenX or Pictures/ScreenX), no storage permission needed
 * for files this app created. Android 8-9: the app's own folder, shared through a FileProvider.
 */
class MediaLibrary(context: Context, private val kind: MediaKind) {
    private val ctx = context.applicationContext
    private val resolver: ContentResolver = ctx.contentResolver

    private val baseDir =
        if (kind == MediaKind.VIDEO) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
    private val relativePath: String get() = "$baseDir/${RecordingStorage.FOLDER}"
    private val mime = if (kind == MediaKind.VIDEO) "video/mp4" else "image/png"
    private val extensions =
        if (kind == MediaKind.VIDEO) listOf("mp4") else listOf("png", "jpg", "jpeg")
    private val authority = "${ctx.packageName}.fileprovider"

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (kind == MediaKind.VIDEO) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            if (kind == MediaKind.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    // ---------- list ----------

    suspend fun list(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) queryMediaStore() else listLegacy()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun queryMediaStore(): List<MediaItem> {
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        if (kind == MediaKind.VIDEO) cols.add(MediaStore.MediaColumns.DURATION)

        val result = ArrayList<MediaItem>()
        resolver.query(
            collection,
            cols.toTypedArray(),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$baseDir/%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                result.add(
                    MediaItem(
                        id = id.toString(),
                        uri = ContentUris.withAppendedId(collection, id),
                        fileName = name,
                        title = name.substringBeforeLast('.'),
                        sizeBytes = c.getLong(2),
                        addedMs = c.getLong(3) * 1000L,
                        durationMs = if (kind == MediaKind.VIDEO) c.getLong(6) else 0L,
                        width = c.getInt(4),
                        height = c.getInt(5)
                    )
                )
            }
        }
        return result
    }

    private fun legacyDir(): File? =
        ctx.getExternalFilesDir(baseDir)?.let { File(it, RecordingStorage.FOLDER) }

    private fun listLegacy(): List<MediaItem> {
        val root = ctx.getExternalFilesDir(baseDir) ?: return emptyList()
        val files = root.walkTopDown()
            .filter { f -> f.isFile && f.extension.lowercase(Locale.ROOT) in extensions }
            .toList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            var w = 0
            var h = 0
            var duration = 0L
            if (kind == MediaKind.VIDEO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(f.absolutePath)
                    duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                } catch (e: Exception) {
                    // keep zeros
                } finally {
                    runCatching { r.release() }
                }
            } else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, o)
                w = o.outWidth
                h = o.outHeight
            }
            MediaItem(
                id = f.absolutePath,
                uri = FileProvider.getUriForFile(ctx, authority, f),
                fileName = f.name,
                title = f.nameWithoutExtension,
                sizeBytes = f.length(),
                addedMs = f.lastModified(),
                durationMs = duration,
                width = w,
                height = h,
                file = f
            )
        }
    }

    // ---------- delete / rename ----------

    fun delete(item: MediaItem): LibraryResult {
        item.file?.let { f ->
            val ok = f.delete()
            MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
            return if (ok) LibraryResult.Done() else LibraryResult.Failed(FailReason.OPERATION_FAILED)
        }
        return try {
            val rows = resolver.delete(item.uri, null, null)
            if (rows > 0) LibraryResult.Done() else LibraryResult.Failed(FailReason.OPERATION_FAILED)
        } catch (e: SecurityException) {
            approvalFor(e, item.uri, forDelete = true)
                ?: LibraryResult.Failed(FailReason.OPERATION_FAILED)
        }
    }

    fun rename(item: MediaItem, newTitle: String): LibraryResult {
        val clean = sanitize(newTitle)
        if (clean.isBlank()) return LibraryResult.Failed(FailReason.INVALID_NAME)
        val ext = item.fileName.substringAfterLast('.', "")
        val newName = if (ext.isEmpty()) clean else "$clean.$ext"
        if (newName == item.fileName) return LibraryResult.Done(newName)
        if (nameExists(newName)) return LibraryResult.Failed(FailReason.NAME_EXISTS)

        item.file?.let { f ->
            val target = File(f.parentFile, newName)
            val ok = f.renameTo(target)
            MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath, target.absolutePath), null, null)
            return if (ok) LibraryResult.Done(newName) else LibraryResult.Failed(FailReason.OPERATION_FAILED)
        }
        return try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
            val rows = resolver.update(item.uri, values, null, null)
            if (rows > 0) LibraryResult.Done(newName) else LibraryResult.Failed(FailReason.OPERATION_FAILED)
        } catch (e: SecurityException) {
            approvalFor(e, item.uri, forDelete = false)
                ?: LibraryResult.Failed(FailReason.OPERATION_FAILED)
        }
    }

    private fun approvalFor(e: SecurityException, uri: Uri, forDelete: Boolean): LibraryResult? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = if (forDelete) {
                MediaStore.createDeleteRequest(resolver, listOf(uri)) // the system deletes it itself
            } else {
                MediaStore.createWriteRequest(resolver, listOf(uri))
            }
            return LibraryResult.NeedsApproval(pending.intentSender, retry = !forDelete)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            return LibraryResult.NeedsApproval(e.userAction.actionIntent.intentSender, retry = true)
        }
        return null
    }

    private fun nameExists(name: String): Boolean {
        legacyDir()?.let { dir ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return File(dir, name).exists()
        }
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "$relativePath%"),
            null
        )?.use { return it.count > 0 }
        return false
    }

    private fun sanitize(input: String): String =
        input.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().take(100)

    // ---------- intents ----------

    fun viewIntent(item: MediaItem): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun shareIntent(item: MediaItem): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, item.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // ---------- details ----------

    /** Average frame rate measured over the first seconds of a video, or null if unknown. */
    suspend fun estimateFps(item: MediaItem): Int? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            val f = item.file
            if (f != null) extractor.setDataSource(f.absolutePath) else extractor.setDataSource(ctx, item.uri, null)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val m = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (m != null && m.startsWith("video/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return@withContext null
            extractor.selectTrack(track)
            var count = 0
            var lastUs = 0L
            while (true) {
                val t = extractor.sampleTime
                if (t < 0 || t > 3_000_000L) break
                count++
                lastUs = t
                if (!extractor.advance()) break
            }
            if (count >= 3 && lastUs > 0) ((count - 1) * 1_000_000.0 / lastUs).roundToInt() else null
        } catch (e: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun locationLabel(item: MediaItem): String =
        if (item.file != null) item.file.parentFile?.absolutePath.orEmpty() else relativePath

    // ---------- screenshots ----------

    /** Saves a screenshot (PNG) to Pictures/ScreenX. Returns false on failure. */
    fun saveBitmap(bitmap: Bitmap): Boolean {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "Screenshot_$stamp.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return false
                try {
                    val ok = resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: false
                    if (!ok) throw IOException("write failed")
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                    true
                } catch (e: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    false
                }
            } else {
                val dir = legacyDir() ?: return false
                if (!dir.isDirectory && !dir.mkdirs()) return false
                val file = File(dir, name)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

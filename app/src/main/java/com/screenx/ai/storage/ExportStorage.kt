package com.screenx.ai.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Saves edited videos (Movies/ScreenX) and extracted audio (Music/ScreenX) next to the originals. */
object ExportStorage {
    class Saved(val name: String, val uri: Uri?)

    fun save(context: Context, source: File, name: String, audioOnly: Boolean): Saved? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveMediaStore(context, source, name, audioOnly)
        } else {
            saveLegacy(context, source, name, audioOnly)
        }
    } catch (e: Exception) {
        null
    }

    private fun saveMediaStore(context: Context, source: File, name: String, audioOnly: Boolean): Saved {
        val resolver = context.contentResolver
        val collection = if (audioOnly) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val baseDir = if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (audioOnly) "audio/mp4" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/${RecordingStorage.FOLDER}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("cannot open output")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Saved(name, uri)
    }

    private fun saveLegacy(context: Context, source: File, name: String, audioOnly: Boolean): Saved {
        val baseDir = if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val root = context.getExternalFilesDir(baseDir) ?: throw IOException("storage unavailable")
        val dir = File(root, RecordingStorage.FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create folder")
        val target = File(dir, name)
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        MediaScannerConnection.scanFile(
            context, arrayOf(target.absolutePath), arrayOf(if (audioOnly) "audio/mp4" else "video/mp4"), null
        )
        return Saved(name, null)
    }
}

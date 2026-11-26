package com.screenx.ai.editor

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records the microphone to an .m4a file in the app's private storage (for voiceover clips). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Starts recording. Returns false when the microphone cannot be used (for example, it is busy). */
    fun start(): Boolean {
        if (recorder != null) return false
        val dir = File(context.filesDir, "voiceovers")
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val target = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            target.delete()
            false
        }
    }

    /** Stops recording and returns the file, or null when nothing usable was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        return try {
            r.stop()
            r.release()
            f
        } catch (e: RuntimeException) {
            // stop() throws when almost nothing was recorded.
            runCatching { r.release() }
            f?.delete()
            null
        }
    }

    /** Stops and throws the recording away (when the screen is left). */
    fun cancel() {
        stop()?.delete()
    }
}

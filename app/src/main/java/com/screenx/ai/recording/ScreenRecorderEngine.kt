package com.screenx.ai.recording

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import com.screenx.ai.R
import com.screenx.ai.audio.AudioCaptureEngine
import com.screenx.ai.audio.AudioCaptureException
import com.screenx.ai.audio.AvMuxer
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.storage.RecordingOutput
import com.screenx.ai.storage.RecordingStorage
import java.io.File
import java.io.IOException

class RecordingException(message: String) : Exception(message)

sealed interface StopOutcome {
    data class Saved(val displayName: String, val uri: Uri?) : StopOutcome
    data class Failed(val message: String) : StopOutcome
}

/**
 * Real screen capture: MediaProjection -> VirtualDisplay -> MediaRecorder surface -> MP4.
 *
 * Without audio the video is written straight to the final file.
 * With audio, video goes to a temp file, audio is captured and encoded by [AudioCaptureEngine],
 * and both are combined (no re-encoding) when you stop.
 *
 * The output size is fixed when recording starts. If the phone is rotated mid-recording,
 * Android scales the screen into the same frame (black bars appear).
 */
class ScreenRecorderEngine(
    context: Context,
    private val projection: MediaProjection
) {
    private val context: Context = context.applicationContext

    /** Called if the encoder reports a fatal error while recording. */
    var onError: ((String) -> Unit)? = null

    /** Called for non-fatal problems (for example audio stopped but video continues). */
    var onWarning: ((String) -> Unit)? = null

    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var output: RecordingOutput? = null
    private var audio: AudioCaptureEngine? = null
    private var videoTemp: File? = null
    private var audioTemp: File? = null
    private var audioOffsetUs = 0L

    @Throws(RecordingException::class)
    fun start(config: RecordingConfig) {
        val resolved = try {
            ConfigResolver.resolve(context, config)
        } catch (e: UnsupportedConfigException) {
            throw RecordingException(context.getString(R.string.error_unsupported))
        }
        val withAudio = config.audioSource != AudioSource.NONE
        val out = try {
            RecordingStorage.create(context, readWrite = withAudio)
        } catch (e: IOException) {
            throw RecordingException(context.getString(R.string.error_storage_create))
        }
        output = out

        try {
            if (withAudio) {
                val stamp = System.currentTimeMillis()
                val vTemp = File(context.cacheDir, "${RecordingStorage.TEMP_PREFIX}${stamp}_v.mp4")
                val aTemp = File(context.cacheDir, "${RecordingStorage.TEMP_PREFIX}${stamp}_a.m4a")
                videoTemp = vTemp
                audioTemp = aTemp
                val engine = AudioCaptureEngine(
                    context, projection, config.audioSource, config.audioBitrateKbps, aTemp
                )
                engine.onError = { msg ->
                    onWarning?.invoke(context.getString(R.string.warn_audio_stopped, msg))
                }
                audio = engine
                engine.prepare()
            }

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder = mr

            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setVideoSize(resolved.width, resolved.height)
            mr.setVideoFrameRate(resolved.fps)
            mr.setVideoEncodingBitRate(resolved.bitrate)
            val tempVideo = videoTemp
            val pfd = out.pfd
            when {
                tempVideo != null -> mr.setOutputFile(tempVideo.absolutePath)
                pfd != null -> mr.setOutputFile(pfd.fileDescriptor)
                else -> mr.setOutputFile(out.file!!.absolutePath)
            }
            mr.setOnErrorListener { _, what, extra ->
                onError?.invoke(context.getString(R.string.error_recorder, "code $what/$extra"))
            }
            mr.prepare()

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenXCapture",
                resolved.width, resolved.height, resolved.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mr.surface, null, null
            ) ?: throw RecordingException(context.getString(R.string.error_projection))

            mr.start()
            val videoStartNs = System.nanoTime()

            audio?.let {
                it.begin()
                // Audio starts a few milliseconds after video; shift it so they stay in sync.
                audioOffsetUs = ((it.startedAtNanos - videoStartNs) / 1000L).coerceIn(0L, 3_000_000L)
            }
        } catch (e: Exception) {
            abort()
            throw when (e) {
                is RecordingException -> e
                is AudioCaptureException ->
                    RecordingException(context.getString(R.string.error_audio, e.message ?: ""))
                else -> RecordingException(
                    context.getString(R.string.error_recorder, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    fun pause() {
        recorder?.pause()
        audio?.pause()
    }

    fun resume() {
        recorder?.resume()
        audio?.resume()
    }

    /** Finishes the recording and saves it. */
    fun stop(): StopOutcome {
        val mr = recorder
        val out = output
        if (mr == null || out == null) {
            return StopOutcome.Failed(context.getString(R.string.error_recorder, "not started"))
        }
        val stopped = try {
            mr.stop()
            true
        } catch (e: RuntimeException) {
            false // happens when nothing was captured (stopped almost immediately)
        }
        releaseCapture()
        val audioHasData = audio?.stop() ?: false
        audio = null
        output = null

        if (!stopped) {
            deleteTemps()
            RecordingStorage.discard(context, out)
            return StopOutcome.Failed(context.getString(R.string.error_too_short))
        }

        if (videoTemp != null) {
            val muxed = muxToOutput(out, audioHasData)
            deleteTemps()
            if (!muxed) {
                RecordingStorage.discard(context, out)
                return StopOutcome.Failed(context.getString(R.string.error_mux))
            }
        }

        val uri = RecordingStorage.finalize(context, out) { scanned ->
            RecordingSession.updateSavedUri(out.displayName, scanned?.toString())
        }
        return StopOutcome.Saved(out.displayName, uri)
    }

    /** Stops everything and deletes the unfinished file. Safe to call more than once. */
    fun abort() {
        releaseCapture()
        audio?.abort()
        audio = null
        deleteTemps()
        output?.let { RecordingStorage.discard(context, it) }
        output = null
    }

    private fun muxToOutput(out: RecordingOutput, withAudio: Boolean): Boolean {
        val video = videoTemp ?: return false
        return try {
            val pfd = out.pfd
            val muxer = if (pfd != null) {
                MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                MediaMuxer(out.file!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            AvMuxer.mux(video, if (withAudio) audioTemp else null, audioOffsetUs, muxer)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteTemps() {
        videoTemp?.let { runCatching { it.delete() } }
        audioTemp?.let { runCatching { it.delete() } }
        videoTemp = null
        audioTemp = null
    }

    private fun releaseCapture() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        recorder?.let {
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        recorder = null
    }
}

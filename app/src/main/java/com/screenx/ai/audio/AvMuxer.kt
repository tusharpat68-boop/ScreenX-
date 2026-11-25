package com.screenx.ai.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Combines the recorded video file and the encoded audio file into the final MP4 without
 * re-encoding. Samples are interleaved by time so players can seek smoothly.
 */
object AvMuxer {
    private const val BUFFER_SIZE = 8 * 1024 * 1024

    private class Track(val index: Int, val format: MediaFormat)

    /** [muxer] is created by the caller (it knows the output target). It is released here. */
    @Throws(IOException::class)
    fun mux(videoFile: File, audioFile: File?, audioOffsetUs: Long, muxer: MediaMuxer) {
        val videoEx = MediaExtractor()
        val audioEx = MediaExtractor()
        var started = false
        try {
            videoEx.setDataSource(videoFile.absolutePath)
            val video = findTrack(videoEx, "video/") ?: throw IOException("No video track")
            videoEx.selectTrack(video.index)
            val videoDst = muxer.addTrack(video.format)

            var audioDst = -1
            if (audioFile != null) {
                audioEx.setDataSource(audioFile.absolutePath)
                val audio = findTrack(audioEx, "audio/")
                if (audio != null) {
                    audioEx.selectTrack(audio.index)
                    audioDst = muxer.addTrack(audio.format)
                }
            }

            muxer.start()
            started = true

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = audioDst < 0
            var lastAudioPts = -1L

            while (true) {
                val videoTime = if (videoDone) -1L else videoEx.sampleTime
                if (!videoDone && videoTime < 0) videoDone = true
                val audioTime = if (audioDone) -1L else audioEx.sampleTime
                if (!audioDone && audioTime < 0) audioDone = true
                if (videoDone && audioDone) break

                val takeVideo = !videoDone && (audioDone || videoTime <= audioTime + audioOffsetUs)
                if (takeVideo) {
                    buffer.clear()
                    val size = videoEx.readSampleData(buffer, 0)
                    if (size < 0) {
                        videoDone = true
                        continue
                    }
                    info.set(0, size, videoTime, flagsOf(videoEx.sampleFlags))
                    muxer.writeSampleData(videoDst, buffer, info)
                    videoEx.advance()
                } else {
                    buffer.clear()
                    val size = audioEx.readSampleData(buffer, 0)
                    if (size < 0) {
                        audioDone = true
                        continue
                    }
                    var pts = audioTime + audioOffsetUs
                    if (pts <= lastAudioPts) pts = lastAudioPts + 1 // must strictly increase
                    lastAudioPts = pts
                    info.set(0, size, pts, flagsOf(audioEx.sampleFlags))
                    muxer.writeSampleData(audioDst, buffer, info)
                    audioEx.advance()
                }
            }
            muxer.stop()
            started = false
        } finally {
            runCatching { videoEx.release() }
            runCatching { audioEx.release() }
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Track? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return Track(i, format)
        }
        return null
    }

    private fun flagsOf(sampleFlags: Int): Int =
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
}

package com.screenx.ai.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

/** Copies a video file and an optional audio file into one MP4 without re-encoding. */
object Mp4Merger {

    @Throws(IOException::class)
    fun merge(video: File, audio: File?, audioOffsetUs: Long, out: FileDescriptor) {
        val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEx = MediaExtractor()
        var aEx: MediaExtractor? = null
        var started = false
        try {
            vEx.setDataSource(video.absolutePath)
            val vTrack = findTrack(vEx, "video/") ?: throw IOException("No video track")
            vEx.selectTrack(vTrack)
            val vFormat = vEx.getTrackFormat(vTrack)
            val vOut = muxer.addTrack(vFormat)

            var aOut = -1
            if (audio != null) {
                val ex = MediaExtractor()
                ex.setDataSource(audio.absolutePath)
                val aTrack = findTrack(ex, "audio/")
                if (aTrack != null) {
                    ex.selectTrack(aTrack)
                    aOut = muxer.addTrack(ex.getTrackFormat(aTrack))
                    aEx = ex
                } else {
                    ex.release()
                }
            }

            muxer.start()
            started = true

            var maxSize = 2 * 1024 * 1024
            if (vFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxSize = maxOf(maxSize, vFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            val buffer = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()

            var vDone = false
            var aDone = aEx == null
            while (!(vDone && aDone)) {
                val vTime = if (vDone) -1L else vEx.sampleTime
                val aTime = if (aDone) -1L else aEx!!.sampleTime
                if (!vDone && vTime < 0) vDone = true
                if (!aDone && aTime < 0) aDone = true
                if (vDone && aDone) break

                val pickVideo = !vDone && (aDone || vTime <= aTime + audioOffsetUs)
                val ex = if (pickVideo) vEx else aEx!!
                val track = if (pickVideo) vOut else aOut
                val offset = if (pickVideo) 0L else audioOffsetUs

                buffer.clear()
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) {
                    if (pickVideo) vDone = true else aDone = true
                    continue
                }
                info.set(0, size, ex.sampleTime + offset, ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC)
                muxer.writeSampleData(track, buffer, info)
                ex.advance()
            }
        } finally {
            runCatching { vEx.release() }
            runCatching { aEx?.release() }
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int? {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return null
    }
}

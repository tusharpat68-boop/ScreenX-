package com.screenx.ai.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes the sound of a file a little at a time (never all at once) and measures how loud each
 * [BUCKET_MS] is. The result is what the waveform pictures are drawn from.
 */
object WaveformExtractor {
    const val BUCKET_MS = 100L

    /** Sound longer than this is not analysed (it would take too long on a phone). */
    private const val MAX_DURATION_MS = 40L * 60L * 1000L

    /** Returns one loudness value (0..1) per [BUCKET_MS], or null when the file has no readable sound. */
    fun extract(context: Context, uri: Uri, durationMs: Long, isActive: () -> Boolean): FloatArray? {
        if (durationMs <= 0L || durationMs > MAX_DURATION_MS) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val buckets = ((durationMs + BUCKET_MS - 1) / BUCKET_MS).toInt().coerceAtLeast(1)
            val peaks = FloatArray(buckets)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!isActive()) return null
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000L)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val out = decoder.getOutputBuffer(outIndex)
                        if (out != null) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val pcm = ShortArray(shorts.remaining())
                            shorts.get(pcm)
                            WaveformMath.accumulate(peaks, pcm, channels, sampleRate, info.presentationTimeUs, BUCKET_MS)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            }
            return peaks
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

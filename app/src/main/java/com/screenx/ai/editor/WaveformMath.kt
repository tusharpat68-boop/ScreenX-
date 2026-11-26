package com.screenx.ai.editor

import kotlin.math.abs

/** Pure maths for waveforms: turning decoded sound into one loudness value per time slot, and shrinking it for drawing. */
object WaveformMath {
    /**
     * Adds the loudness of [pcm] (16-bit samples, channels interleaved, starting at [startUs]) to [peaks].
     * [peaks] holds one value per [bucketMs]: the loudest sample in that time, 0..1.
     */
    fun accumulate(peaks: FloatArray, pcm: ShortArray, channels: Int, sampleRate: Int, startUs: Long, bucketMs: Long) {
        val ch = channels.coerceAtLeast(1)
        val frames = pcm.size / ch
        if (frames == 0 || sampleRate <= 0 || bucketMs <= 0L) return
        val bucketUs = bucketMs * 1000L
        var f = 0
        while (f < frames) {
            val tUs = startUs + f * 1_000_000L / sampleRate
            val bucket = (tUs / bucketUs).toInt()
            val bucketEndUs = (bucket + 1) * bucketUs
            val untilBoundary = ((bucketEndUs - startUs) * sampleRate + 999_999L) / 1_000_000L
            val endFrame = minOf(frames.toLong(), untilBoundary).toInt().coerceAtLeast(f + 1)
            var m = 0
            for (i in f until endFrame) {
                for (c in 0 until ch) {
                    val v = abs(pcm[i * ch + c].toInt())
                    if (v > m) m = v
                }
            }
            if (bucket in peaks.indices) {
                val n = minOf(1f, m / 32768f)
                if (n > peaks[bucket]) peaks[bucket] = n
            }
            f = endFrame
        }
    }

    /** [bars] values for the part [fromMs]..[toMs] of the sound: the loudest slot under each bar. */
    fun slice(peaks: FloatArray, bucketMs: Long, fromMs: Long, toMs: Long, bars: Int): FloatArray {
        val out = FloatArray(bars.coerceAtLeast(0))
        if (out.isEmpty() || peaks.isEmpty() || toMs <= fromMs || bucketMs <= 0L) return out
        val span = (toMs - fromMs).toDouble()
        for (i in out.indices) {
            val a = fromMs + (span * i / out.size).toLong()
            val b = fromMs + (span * (i + 1) / out.size).toLong()
            val lo = (a / bucketMs).toInt().coerceIn(0, peaks.lastIndex)
            val hi = (maxOf(a, b - 1) / bucketMs).toInt().coerceIn(lo, peaks.lastIndex)
            var m = 0f
            for (k in lo..hi) if (peaks[k] > m) m = peaks[k]
            out[i] = m
        }
        return out
    }
}

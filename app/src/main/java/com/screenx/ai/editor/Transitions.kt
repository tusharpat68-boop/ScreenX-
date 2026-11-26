package com.screenx.ai.editor

/**
 * Fade through black between two clips of the main track. Half of the time is spent fading the
 * first clip out, half fading the next clip in, so the total length of the video does not change.
 * The preview and the export both use [gain], so they look the same.
 */
object Transitions {
    const val MIN_MS = 200L
    const val MAX_MS = 2000L

    /** The longest fade allowed after clip [index]: each half must stay well inside both clips. */
    fun maxMs(list: List<Segment>, index: Int): Long {
        val a = list.getOrNull(index) ?: return 0L
        val b = list.getOrNull(index + 1) ?: return 0L
        return minOf(MAX_MS, minOf(a.timelineMs, b.timelineMs) / 2L)
    }

    /** The fade that really applies after clip [index] (0 = none): the last clip has none, too-long ones are shortened. */
    fun effective(list: List<Segment>, index: Int): Long {
        val seg = list.getOrNull(index) ?: return 0L
        if (seg.transitionMs <= 0L) return 0L
        val cap = maxMs(list, index)
        return if (cap < MIN_MS) 0L else seg.transitionMs.coerceIn(MIN_MS, cap)
    }

    /** True when clip [index] has a fade at its start or its end. */
    fun touches(list: List<Segment>, index: Int): Boolean =
        effective(list, index) > 0L || (index > 0 && effective(list, index - 1) > 0L)

    /** Brightness 0..1 of clip [index] at [posMs] after its start on the timeline (1 = unchanged, 0 = black). */
    fun gain(list: List<Segment>, index: Int, posMs: Long): Float {
        val seg = list.getOrNull(index) ?: return 1f
        var g = 1f
        val outHalf = effective(list, index) / 2L
        if (outHalf > 0L) {
            val left = seg.timelineMs - posMs
            if (left < outHalf) g = minOf(g, (left.toFloat() / outHalf).coerceIn(0f, 1f))
        }
        val inHalf = if (index > 0) effective(list, index - 1) / 2L else 0L
        if (inHalf > 0L && posMs < inHalf) g = minOf(g, (posMs.toFloat() / inHalf).coerceIn(0f, 1f))
        return g
    }

    /** How black the picture is at timeline time [timelineMs] (0 = normal, 1 = fully black). */
    fun darkness(list: List<Segment>, timelineMs: Long): Float {
        var acc = 0L
        for (i in list.indices) {
            val len = list[i].timelineMs
            if (timelineMs < acc + len || i == list.lastIndex) {
                return 1f - gain(list, i, (timelineMs - acc).coerceIn(0L, len))
            }
            acc += len
        }
        return 0f
    }
}

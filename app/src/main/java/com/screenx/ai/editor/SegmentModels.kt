package com.screenx.ai.editor

import kotlin.math.abs

/**
 * One clip of the main video track: a piece of one source video ([startMs]..[endMs] in source time)
 * with its own speed and volume. Clips play one after another in list order.
 * [width] and [height] are the picture size as it is shown (after the file's own rotation).
 */
data class Segment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val uri: String = "",
    val name: String = "",
    /** Length of the whole source file (the clip can be trimmed up to this). */
    val mediaDurationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    /** Length of the fade through black between this clip and the next one (0 = cut). */
    val transitionMs: Long = 0L
) {
    val sourceMs: Long get() = endMs - startMs

    /** How long the clip lasts on the timeline (faster speed = shorter). */
    val timelineMs: Long get() = maxOf(1L, (sourceMs / speed).toLong())
}

/** Maps between the edited timeline (clips back to back) and the clips' sources. */
object EditTimeline {
    class Located(val index: Int, val sourceMs: Long)

    fun total(segments: List<Segment>): Long {
        var sum = 0L
        for (s in segments) sum += s.timelineMs
        return sum
    }

    /** Timeline time where clip [index] begins. */
    fun startOf(segments: List<Segment>, index: Int): Long {
        var sum = 0L
        for (i in 0 until index.coerceIn(0, segments.size)) sum += segments[i].timelineMs
        return sum
    }

    /** Where timeline time [timelineMs] is: the clip and the time inside its source. Past the end lands on the last frame. */
    fun locate(segments: List<Segment>, timelineMs: Long): Located? {
        if (segments.isEmpty()) return null
        var acc = 0L
        for (i in segments.indices) {
            val s = segments[i]
            val len = s.timelineMs
            if (timelineMs < acc + len || i == segments.lastIndex) {
                val within = (timelineMs - acc).coerceIn(0L, len)
                val src = s.startMs + (within * s.speed).toLong()
                return Located(i, src.coerceIn(s.startMs, s.endMs))
            }
            acc += len
        }
        return null
    }

    /** Timeline time of the moment [withinSourceMs] after the start of clip [index] (as the player reports it). */
    fun timelineOf(segments: List<Segment>, index: Int, withinSourceMs: Long): Long {
        val s = segments.getOrNull(index) ?: return total(segments)
        val within = (withinSourceMs / s.speed).toLong().coerceIn(0L, s.timelineMs)
        return startOf(segments, index) + within
    }
}

/** Pure list operations on the main track. Each returns a new list, or null when it cannot be done. */
object SegmentOps {
    /** Inserts [seg] after position [index] (-1 = at the very start). */
    fun insertAfter(list: List<Segment>, index: Int, seg: Segment): List<Segment> {
        val at = (index + 1).coerceIn(0, list.size)
        return list.toMutableList().apply { add(at, seg) }
    }

    /** A copy of the clip placed right after it. */
    fun duplicate(list: List<Segment>, id: Long, newId: Long): List<Segment>? {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        return insertAfter(list, i, list[i].copy(id = newId))
    }

    /** Swaps the clip with its neighbour: [delta] is -1 (earlier) or +1 (later). */
    fun move(list: List<Segment>, id: Long, delta: Int): List<Segment>? {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        val j = i + delta
        if (j < 0 || j >= list.size || delta == 0) return null
        return list.toMutableList().apply {
            val t = this[i]
            this[i] = this[j]
            this[j] = t
        }
    }

    /** Two pieces can be joined when they were cut from the same video, touch, and play the same way. */
    fun canMerge(a: Segment, b: Segment): Boolean =
        a.uri == b.uri && abs(b.startMs - a.endMs) <= 1L && a.speed == b.speed && a.volume == b.volume

    /** Joins the clip with the one after it (the opposite of a split). */
    fun mergeWithNext(list: List<Segment>, id: Long): List<Segment>? {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0 || i >= list.lastIndex) return null
        val a = list[i]
        val b = list[i + 1]
        if (!canMerge(a, b)) return null
        return list.toMutableList().apply {
            this[i] = a.copy(endMs = b.endMs, transitionMs = b.transitionMs)
            removeAt(i + 1)
        }
    }
}

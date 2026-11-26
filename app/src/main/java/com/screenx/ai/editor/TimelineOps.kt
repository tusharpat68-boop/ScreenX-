package com.screenx.ai.editor

/** The kinds of extra track below the main video. The order is the order they appear on screen. */
enum class TrackKind { VIDEO, OVERLAY, IMAGE, AUDIO, VOICEOVER, SFX }

/**
 * A clip placed freely on an extra track.
 * [startMs] is where it begins on the timeline, [inMs] where it begins inside its media,
 * [lengthMs] how long it lasts. [mediaDurationMs] is 0 for images (they can be any length).
 */
data class TimelineClip(
    val id: Long,
    val trackId: Long,
    val uri: String,
    val name: String,
    val kind: ImportKind,
    val startMs: Long,
    val inMs: Long,
    val lengthMs: Long,
    val mediaDurationMs: Long,
    /** Where the picture sits on the frame: the centre, -1..1 (posY = 1 is the top). */
    val posX: Float = 0.5f,
    val posY: Float = -0.5f,
    /** Width of the picture as a fraction of the frame width. */
    val scale: Float = 0.35f,
    val opacity: Float = 1f,
    /** Width / height of the picture (0 = unknown). */
    val aspect: Float = 0f,
    /** Sound level 0..1. */
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    val endMs: Long get() = startMs + lengthMs
}

data class Track(val id: Long, val kind: TrackKind, val clips: List<TimelineClip>)

/** Pure editing operations on the extra tracks. Every function returns new lists. */
object TimelineOps {
    const val MIN_CLIP_MS = 300L
    const val IMAGE_DEFAULT_MS = 3000L

    fun compatible(media: ImportKind, track: TrackKind): Boolean = when (media) {
        ImportKind.VIDEO -> track == TrackKind.VIDEO || track == TrackKind.OVERLAY
        ImportKind.IMAGE -> track == TrackKind.IMAGE || track == TrackKind.OVERLAY
        ImportKind.AUDIO -> track == TrackKind.AUDIO || track == TrackKind.SFX
    }

    fun tracksFor(media: ImportKind): List<TrackKind> = TrackKind.values().filter { compatible(media, it) }

    fun isFree(clips: List<TimelineClip>, startMs: Long, endMs: Long, ignoreId: Long = -1L): Boolean =
        clips.none { it.id != ignoreId && it.startMs < endMs && startMs < it.endMs }

    fun find(tracks: List<Track>, clipId: Long): TimelineClip? =
        tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }

    /** The end of the last clip on any extra track (0 when there are none). */
    fun end(tracks: List<Track>): Long = tracks.maxOfOrNull { t -> t.clips.maxOfOrNull { it.endMs } ?: 0L } ?: 0L

    /** Puts [clip] on the first track of [kind] that is free at that time, or on a new track [newTrackId]. */
    fun add(tracks: List<Track>, kind: TrackKind, clip: TimelineClip, newTrackId: Long): List<Track> {
        val idx = tracks.indexOfFirst { it.kind == kind && isFree(it.clips, clip.startMs, clip.endMs) }
        if (idx >= 0) {
            return tracks.mapIndexed { i, t ->
                if (i == idx) t.copy(clips = (t.clips + clip.copy(trackId = t.id)).sortedBy { it.startMs }) else t
            }
        }
        val track = Track(newTrackId, kind, listOf(clip.copy(trackId = newTrackId)))
        val at = tracks.indexOfLast { it.kind.ordinal <= kind.ordinal } + 1
        return tracks.toMutableList().apply { add(at, track) }
    }

    /** Tracks whose clips are shown on the picture (the others only make sound). */
    val VISUAL_KINDS = setOf(TrackKind.VIDEO, TrackKind.OVERLAY, TrackKind.IMAGE)

    /** The picture clips visible at timeline time [timeMs], bottom layer first (track order). */
    fun visualAt(tracks: List<Track>, timeMs: Long): List<TimelineClip> =
        tracks.filter { it.kind in VISUAL_KINDS }
            .flatMap { t -> t.clips.filter { timeMs >= it.startMs && timeMs < it.endMs } }

    /** Tracks that only make sound. */
    val AUDIBLE_KINDS = setOf(TrackKind.AUDIO, TrackKind.VOICEOVER, TrackKind.SFX)

    /** The sound clips that play at timeline time [timeMs]. */
    fun audibleAt(tracks: List<Track>, timeMs: Long): List<TimelineClip> =
        tracks.filter { it.kind in AUDIBLE_KINDS }
            .flatMap { t -> t.clips.filter { timeMs >= it.startMs && timeMs < it.endMs } }

    /** Loudness factor 0..1 from the fades: [posMs] is the time inside the clip. */
    fun fadeFactor(posMs: Long, lengthMs: Long, fadeInMs: Long, fadeOutMs: Long): Float {
        val fin = if (fadeInMs > 0L) (posMs.toFloat() / fadeInMs).coerceIn(0f, 1f) else 1f
        val fout = if (fadeOutMs > 0L) ((lengthMs - posMs).toFloat() / fadeOutMs).coerceIn(0f, 1f) else 1f
        return minOf(fin, fout)
    }

    /** Changes the sound of a clip. Values left null stay as they are. Each fade can be at most half the clip. */
    fun audio(
        tracks: List<Track>,
        clipId: Long,
        volume: Float? = null,
        fadeInMs: Long? = null,
        fadeOutMs: Long? = null
    ): List<Track> = tracks.map { t ->
        if (t.clips.none { it.id == clipId }) {
            t
        } else {
            t.copy(
                clips = t.clips.map { c ->
                    if (c.id != clipId) {
                        c
                    } else {
                        c.copy(
                            volume = (volume ?: c.volume).coerceIn(0f, 1f),
                            fadeInMs = (fadeInMs ?: c.fadeInMs).coerceIn(0L, c.lengthMs / 2),
                            fadeOutMs = (fadeOutMs ?: c.fadeOutMs).coerceIn(0L, c.lengthMs / 2)
                        )
                    }
                }
            )
        }
    }

    /** Changes where and how a picture clip is shown. Values left null stay as they are. */
    fun transform(
        tracks: List<Track>,
        clipId: Long,
        posX: Float? = null,
        posY: Float? = null,
        scale: Float? = null,
        opacity: Float? = null
    ): List<Track> = tracks.map { t ->
        if (t.clips.none { it.id == clipId }) {
            t
        } else {
            t.copy(
                clips = t.clips.map { c ->
                    if (c.id != clipId) {
                        c
                    } else {
                        c.copy(
                            posX = (posX ?: c.posX).coerceIn(-1f, 1f),
                            posY = (posY ?: c.posY).coerceIn(-1f, 1f),
                            scale = (scale ?: c.scale).coerceIn(MIN_SCALE, 1f),
                            opacity = (opacity ?: c.opacity).coerceIn(0f, 1f)
                        )
                    }
                }
            )
        }
    }

    const val MIN_SCALE = 0.1f

    fun delete(tracks: List<Track>, clipId: Long): List<Track> =
        tracks.map { t -> t.copy(clips = t.clips.filter { it.id != clipId }) }.filter { it.clips.isNotEmpty() }

    /**
     * Moves a clip so it starts at [desiredStart]. It cannot overlap its neighbours on the track:
     * when it is dragged past the middle of a neighbour the two swap places instead (reordering).
     */
    fun move(tracks: List<Track>, clipId: Long, desiredStart: Long): List<Track> = tracks.map { t ->
        val clip = t.clips.firstOrNull { it.id == clipId } ?: return@map t
        val others = t.clips.filter { it.id != clipId }.sortedBy { it.startMs }
        val before = others.lastOrNull { it.endMs <= clip.startMs }
        val after = others.firstOrNull { it.startMs >= clip.endMs }
        // The swap test uses the raw drag position, so a clip can be dragged back over a neighbour at 0.
        val center = desiredStart + clip.lengthMs / 2
        val result: List<TimelineClip> = when {
            before != null && center < before.startMs + before.lengthMs / 2 ->
                others.filter { it.id != before.id } +
                    before.copy(startMs = before.startMs + clip.lengthMs) +
                    clip.copy(startMs = before.startMs)
            after != null && center > after.startMs + after.lengthMs / 2 ->
                others.filter { it.id != after.id } +
                    after.copy(startMs = clip.startMs) +
                    clip.copy(startMs = clip.startMs + after.lengthMs)
            else -> {
                val lower = before?.endMs ?: 0L
                val upper = if (after != null) after.startMs - clip.lengthMs else Long.MAX_VALUE
                others + clip.copy(startMs = desiredStart.coerceIn(lower, maxOf(lower, upper)))
            }
        }
        t.copy(clips = result.sortedBy { it.startMs })
    }

    /** Moves the left edge of a clip to [newStart] on the timeline. */
    fun trimStart(tracks: List<Track>, clipId: Long, newStart: Long): List<Track> =
        updateClip(tracks, clipId) { clip, others ->
            val before = others.lastOrNull { it.endMs <= clip.startMs }
            var lower = before?.endMs ?: 0L
            if (clip.mediaDurationMs > 0L) lower = maxOf(lower, clip.startMs - clip.inMs)
            val s = newStart.coerceIn(lower, maxOf(lower, clip.endMs - MIN_CLIP_MS))
            val delta = s - clip.startMs
            clip.copy(
                startMs = s,
                lengthMs = clip.lengthMs - delta,
                inMs = if (clip.mediaDurationMs > 0L) clip.inMs + delta else 0L
            )
        }

    /** Moves the right edge of a clip to [newEnd] on the timeline. */
    fun trimEnd(tracks: List<Track>, clipId: Long, newEnd: Long): List<Track> =
        updateClip(tracks, clipId) { clip, others ->
            val after = others.firstOrNull { it.startMs >= clip.endMs }
            var upper = after?.startMs ?: Long.MAX_VALUE
            if (clip.mediaDurationMs > 0L) upper = minOf(upper, clip.startMs - clip.inMs + clip.mediaDurationMs)
            val low = clip.startMs + MIN_CLIP_MS
            val e = newEnd.coerceIn(low, maxOf(low, upper))
            clip.copy(lengthMs = e - clip.startMs)
        }

    /** Cuts a clip in two at timeline time [atMs]. Null when a piece would be too short. */
    fun split(tracks: List<Track>, clipId: Long, atMs: Long, newId: Long): List<Track>? {
        val track = tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return null
        val clip = track.clips.first { it.id == clipId }
        if (atMs - clip.startMs < MIN_CLIP_MS || clip.endMs - atMs < MIN_CLIP_MS) return null
        val first = clip.copy(lengthMs = atMs - clip.startMs, fadeOutMs = 0L)
        val second = clip.copy(
            id = newId,
            startMs = atMs,
            inMs = if (clip.mediaDurationMs > 0L) clip.inMs + (atMs - clip.startMs) else 0L,
            lengthMs = clip.endMs - atMs,
            fadeInMs = 0L
        )
        return tracks.map { t ->
            if (t.id == track.id) {
                t.copy(clips = (t.clips.filter { it.id != clipId } + first + second).sortedBy { it.startMs })
            } else {
                t
            }
        }
    }

    private fun updateClip(
        tracks: List<Track>,
        clipId: Long,
        change: (TimelineClip, List<TimelineClip>) -> TimelineClip
    ): List<Track> = tracks.map { t ->
        val clip = t.clips.firstOrNull { it.id == clipId }
        if (clip == null) {
            t
        } else {
            val others = t.clips.filter { it.id != clipId }.sortedBy { it.startMs }
            t.copy(clips = (others + change(clip, others)).sortedBy { it.startMs })
        }
    }
}

/** Where a picture clip is drawn on the preview frame. All numbers are in pixels. */
object OverlayLayout {
    class Rect(val left: Float, val top: Float, val width: Float, val height: Float)

    fun rect(frameW: Float, frameH: Float, posX: Float, posY: Float, scale: Float, aspect: Float): Rect {
        val a = if (aspect > 0f) aspect else 16f / 9f
        var w = scale * frameW
        var h = w / a
        if (h > frameH) {
            h = frameH
            w = h * a
        }
        val cx = (posX + 1f) / 2f * frameW
        val cy = (1f - posY) / 2f * frameH
        return Rect(cx - w / 2f, cy - h / 2f, w, h)
    }
}

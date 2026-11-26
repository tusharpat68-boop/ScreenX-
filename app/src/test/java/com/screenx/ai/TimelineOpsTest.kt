package com.screenx.ai

import com.screenx.ai.editor.ImportKind
import com.screenx.ai.editor.TimelineClip
import com.screenx.ai.editor.TimelineOps
import com.screenx.ai.editor.Track
import com.screenx.ai.editor.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOpsTest {

    private fun clip(
        id: Long,
        start: Long,
        length: Long,
        kind: ImportKind = ImportKind.VIDEO,
        inMs: Long = 0L,
        media: Long = 60_000L
    ) = TimelineClip(id, 0L, "content://m/$id", "c$id", kind, start, inMs, length, media)

    private fun starts(tracks: List<Track>, trackIndex: Int = 0) = tracks[trackIndex].clips.map { it.id to it.startMs }

    @Test
    fun addCreatesTrackThenReusesFreeOne() {
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 2000), 100L)
        assertEquals(1, t.size)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 2000, 1000), 101L)
        assertEquals(1, t.size)
        assertEquals(2, t[0].clips.size)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(3, 500, 1000), 102L)
        assertEquals(2, t.size)
        assertEquals(102L, t[1].id)
    }

    @Test
    fun tracksStayInKindOrder() {
        var t = TimelineOps.add(emptyList(), TrackKind.SFX, clip(1, 0, 1000, ImportKind.AUDIO), 100L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 0, 1000), 101L)
        t = TimelineOps.add(t, TrackKind.AUDIO, clip(3, 0, 1000, ImportKind.AUDIO), 102L)
        assertEquals(listOf(TrackKind.VIDEO, TrackKind.AUDIO, TrackKind.SFX), t.map { it.kind })
    }

    @Test
    fun compatibility() {
        assertTrue(TimelineOps.compatible(ImportKind.VIDEO, TrackKind.OVERLAY))
        assertTrue(!TimelineOps.compatible(ImportKind.VIDEO, TrackKind.AUDIO))
        assertTrue(TimelineOps.compatible(ImportKind.AUDIO, TrackKind.SFX))
        assertEquals(listOf(TrackKind.OVERLAY, TrackKind.IMAGE), TimelineOps.tracksFor(ImportKind.IMAGE))
    }

    @Test
    fun deleteDropsEmptyTrack() {
        val t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 1000), 100L)
        assertTrue(TimelineOps.delete(t, 1L).isEmpty())
    }

    @Test
    fun moveIsBlockedByNeighbourUnlessItSwaps() {
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 2000), 100L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 3000, 2000), 101L)
        // Small move inside the gap
        t = TimelineOps.move(t, 1L, 500L)
        assertEquals(listOf(1L to 500L, 2L to 3000L), starts(t))
        // Cannot go left of 0
        t = TimelineOps.move(t, 1L, -400L)
        assertEquals(1L to 0L, starts(t)[0])
        // Pushing against the neighbour stops at its edge (centre not yet past the middle)
        t = TimelineOps.move(t, 1L, 900L)
        assertEquals(1L to 900L, starts(t)[0])
    }

    @Test
    fun moveSwapsWhenDraggedPastMiddle() {
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 2000), 100L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 2000, 2000), 101L)
        t = TimelineOps.move(t, 1L, 2500L) // clip 1 centre = 3500 > centre of clip 2 (3000)
        assertEquals(listOf(2L to 0L, 1L to 2000L), starts(t))
        // and back
        t = TimelineOps.move(t, 1L, -500L)
        assertEquals(listOf(1L to 0L, 2L to 2000L), starts(t))
    }

    @Test
    fun clipsNeverOverlapAfterMoves() {
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 1500), 100L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 1500, 1000), 101L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(3, 2500, 2500), 102L)
        for (d in listOf(4000L, -100L, 1200L, 3000L, 0L, 2200L, 900L)) {
            t = TimelineOps.move(t, 1L, d)
            val sorted = t[0].clips.sortedBy { it.startMs }
            for (i in 1 until sorted.size) assertTrue(sorted[i - 1].endMs <= sorted[i].startMs)
            assertEquals(3, sorted.size)
        }
    }

    @Test
    fun splitKeepsMediaPosition() {
        val t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 1000, 4000, inMs = 500), 100L)
        val s = TimelineOps.split(t, 1L, 2000L, 7L)
        assertNotNull(s)
        val clips = s!![0].clips
        assertEquals(2, clips.size)
        assertEquals(1000L, clips[0].startMs)
        assertEquals(1000L, clips[0].lengthMs)
        assertEquals(2000L, clips[1].startMs)
        assertEquals(3000L, clips[1].lengthMs)
        assertEquals(1500L, clips[1].inMs)
        assertEquals(7L, clips[1].id)
    }

    @Test
    fun splitNearEdgeIsRefused() {
        val t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 4000), 100L)
        assertNull(TimelineOps.split(t, 1L, 100L, 7L))
        assertNull(TimelineOps.split(t, 1L, 3900L, 7L))
        assertNull(TimelineOps.split(t, 99L, 2000L, 7L))
    }

    @Test
    fun trimRespectsMediaAndNeighbours() {
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 2000, 4000, inMs = 1000, media = 10_000), 100L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 8000, 1000), 101L)
        // Left edge can go back only as far as the start of the media (inMs = 1000)
        t = TimelineOps.trimStart(t, 1L, 0L)
        var c = t[0].clips.first { it.id == 1L }
        assertEquals(1000L, c.startMs)
        assertEquals(0L, c.inMs)
        assertEquals(5000L, c.lengthMs)
        // Right edge stops before the neighbour (8000)
        t = TimelineOps.trimEnd(t, 1L, 20_000L)
        c = t[0].clips.first { it.id == 1L }
        assertEquals(8000L, c.endMs)
        // Cannot shrink below the minimum length
        t = TimelineOps.trimEnd(t, 1L, 1001L)
        c = t[0].clips.first { it.id == 1L }
        assertEquals(TimelineOps.MIN_CLIP_MS, c.lengthMs)
    }

    @Test
    fun imageCanBeStretchedFreely() {
        var t = TimelineOps.add(emptyList(), TrackKind.IMAGE, clip(1, 1000, 3000, ImportKind.IMAGE, media = 0L), 100L)
        t = TimelineOps.trimEnd(t, 1L, 30_000L)
        assertEquals(30_000L, t[0].clips[0].endMs)
        t = TimelineOps.trimStart(t, 1L, 0L)
        assertEquals(0L, t[0].clips[0].startMs)
        assertEquals(0L, t[0].clips[0].inMs)
    }

    @Test
    fun endIsLastClipEnd() {
        assertEquals(0L, TimelineOps.end(emptyList()))
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 2000), 100L)
        t = TimelineOps.add(t, TrackKind.AUDIO, clip(2, 5000, 1000, ImportKind.AUDIO), 101L)
        assertEquals(6000L, TimelineOps.end(t))
    }

    @Test
    fun visualAtFiltersByTimeAndKindInTrackOrder() {
        var t = TimelineOps.add(emptyList(), TrackKind.OVERLAY, clip(1, 1000, 2000), 100L)
        t = TimelineOps.add(t, TrackKind.VIDEO, clip(2, 0, 5000), 101L)
        t = TimelineOps.add(t, TrackKind.AUDIO, clip(3, 0, 5000, ImportKind.AUDIO), 102L)
        t = TimelineOps.add(t, TrackKind.IMAGE, clip(4, 500, 1000, ImportKind.IMAGE, media = 0L), 103L)
        // Track order: VIDEO, OVERLAY, IMAGE, AUDIO -> bottom layer first
        assertEquals(listOf(2L, 1L, 4L), TimelineOps.visualAt(t, 1200L).map { it.id })
        assertEquals(listOf(2L), TimelineOps.visualAt(t, 4000L).map { it.id })
        assertEquals(listOf(2L, 4L), TimelineOps.visualAt(t, 500L).map { it.id })
        assertTrue(TimelineOps.visualAt(t, 5000L).isEmpty()) // end is exclusive
    }

    @Test
    fun transformClampsAndKeepsOthers() {
        var t = TimelineOps.add(emptyList(), TrackKind.OVERLAY, clip(1, 0, 2000), 100L)
        t = TimelineOps.transform(t, 1L, posX = 5f, posY = -9f, scale = 0.01f, opacity = 3f)
        val c = t[0].clips[0]
        assertEquals(1f, c.posX, 0f)
        assertEquals(-1f, c.posY, 0f)
        assertEquals(TimelineOps.MIN_SCALE, c.scale, 0f)
        assertEquals(1f, c.opacity, 0f)
        t = TimelineOps.transform(t, 1L, scale = 0.5f)
        assertEquals(0.5f, t[0].clips[0].scale, 0f)
        assertEquals(1f, t[0].clips[0].posX, 0f)
        assertEquals(t, TimelineOps.transform(t, 99L, scale = 0.9f))
    }

    @Test
    fun overlayLayoutPlacesCentreAndLimitsHeight() {
        // Centre of the frame, 50% wide, 2:1 picture on a 1000x500 frame
        val r = com.screenx.ai.editor.OverlayLayout.rect(1000f, 500f, 0f, 0f, 0.5f, 2f)
        assertEquals(500f, r.width, 0.01f)
        assertEquals(250f, r.height, 0.01f)
        assertEquals(250f, r.left, 0.01f)
        assertEquals(125f, r.top, 0.01f)
        // Top-left corner position (-1, 1): the centre is on the corner
        val c = com.screenx.ai.editor.OverlayLayout.rect(1000f, 500f, -1f, 1f, 0.2f, 1f)
        assertEquals(-100f, c.left, 0.01f)
        assertEquals(-100f, c.top, 0.01f)
        // A very tall picture never gets taller than the frame
        val tall = com.screenx.ai.editor.OverlayLayout.rect(1000f, 500f, 0f, 0f, 1f, 0.25f)
        assertEquals(500f, tall.height, 0.01f)
        assertEquals(125f, tall.width, 0.01f)
    }
}

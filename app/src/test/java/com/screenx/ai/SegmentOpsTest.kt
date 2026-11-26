package com.screenx.ai

import com.screenx.ai.editor.EditTimeline
import com.screenx.ai.editor.Segment
import com.screenx.ai.editor.SegmentOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentOpsTest {
    private fun seg(id: Long, start: Long, end: Long, uri: String = "a", speed: Float = 1f) =
        Segment(id, start, end, speed = speed, uri = uri, mediaDurationMs = 60_000L)

    @Test
    fun timelineMapsAcrossSources() {
        val list = listOf(seg(1, 1000, 3000, "a"), seg(2, 0, 4000, "b", speed = 2f), seg(3, 500, 1500, "a"))
        // lengths on the timeline: 2000, 2000, 1000
        assertEquals(5000L, EditTimeline.total(list))
        assertEquals(2000L, EditTimeline.startOf(list, 1))
        assertEquals(4000L, EditTimeline.startOf(list, 2))
        val loc = EditTimeline.locate(list, 3000L)!!
        assertEquals(1, loc.index)
        assertEquals(2000L, loc.sourceMs) // 1000 ms into a 2x clip = 2000 ms of source
        assertEquals(3000L, EditTimeline.timelineOf(list, 1, 2000L))
        assertEquals(5000L, EditTimeline.locate(list, 99_000L)!!.let { EditTimeline.timelineOf(list, it.index, it.sourceMs - list[it.index].startMs) })
    }

    @Test
    fun duplicateInsertsRightAfterWithNewId() {
        val list = listOf(seg(1, 0, 1000), seg(2, 0, 2000))
        val out = SegmentOps.duplicate(list, 1L, 9L)!!
        assertEquals(listOf(1L, 9L, 2L), out.map { it.id })
        assertEquals(list[0].copy(id = 9L), out[1])
        assertNull(SegmentOps.duplicate(list, 77L, 9L))
    }

    @Test
    fun moveSwapsNeighboursAndStopsAtEnds() {
        val list = listOf(seg(1, 0, 1000), seg(2, 0, 2000), seg(3, 0, 3000))
        assertEquals(listOf(2L, 1L, 3L), SegmentOps.move(list, 1L, 1)!!.map { it.id })
        assertEquals(listOf(1L, 3L, 2L), SegmentOps.move(list, 3L, -1)!!.map { it.id })
        assertNull(SegmentOps.move(list, 1L, -1))
        assertNull(SegmentOps.move(list, 3L, 1))
    }

    @Test
    fun mergeJoinsOnlyTouchingPiecesOfTheSameVideo() {
        val list = listOf(seg(1, 0, 1000), seg(2, 1000, 3000), seg(3, 3500, 4000), seg(4, 0, 500, "b"))
        val merged = SegmentOps.mergeWithNext(list, 1L)!!
        assertEquals(listOf(1L, 3L, 4L), merged.map { it.id })
        assertEquals(0L, merged[0].startMs)
        assertEquals(3000L, merged[0].endMs)
        assertNull(SegmentOps.mergeWithNext(list, 2L)) // gap between 3000 and 3500
        assertNull(SegmentOps.mergeWithNext(list, 3L)) // different video
        assertNull(SegmentOps.mergeWithNext(list, 4L)) // last clip
        assertTrue(!SegmentOps.canMerge(seg(1, 0, 1000), seg(2, 1000, 2000, speed = 2f)))
    }

    @Test
    fun insertAfterPositions() {
        val list = listOf(seg(1, 0, 1000), seg(2, 0, 2000))
        assertEquals(listOf(5L, 1L, 2L), SegmentOps.insertAfter(list, -1, seg(5, 0, 1)).map { it.id })
        assertEquals(listOf(1L, 5L, 2L), SegmentOps.insertAfter(list, 0, seg(5, 0, 1)).map { it.id })
        assertEquals(listOf(1L, 2L, 5L), SegmentOps.insertAfter(list, 9, seg(5, 0, 1)).map { it.id })
    }
}

package com.screenx.ai

import com.screenx.ai.editor.Segment
import com.screenx.ai.editor.Transitions
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionsTest {
    private fun seg(id: Long, len: Long, t: Long = 0L) = Segment(id, 0L, len, transitionMs = t)

    @Test fun noTransitionMeansNoDarkness() {
        val l = listOf(seg(1, 5000), seg(2, 5000))
        assertEquals(0f, Transitions.darkness(l, 4999), 0f)
        assertEquals(0f, Transitions.darkness(l, 5000), 0f)
    }

    @Test fun fadeIsBlackAtTheCut() {
        val l = listOf(seg(1, 5000, 1000), seg(2, 5000))
        assertEquals(0f, Transitions.darkness(l, 4500), 0.001f)
        assertEquals(0.5f, Transitions.darkness(l, 4750), 0.001f)
        assertEquals(1f, Transitions.darkness(l, 5000), 0.001f)
        assertEquals(0.5f, Transitions.darkness(l, 5250), 0.001f)
        assertEquals(0f, Transitions.darkness(l, 5500), 0.001f)
    }

    @Test fun lastClipHasNoTransition() {
        val l = listOf(seg(1, 5000), seg(2, 5000, 1000))
        assertEquals(0L, Transitions.effective(l, 1))
        assertEquals(0f, Transitions.darkness(l, 9999), 0f)
    }

    @Test fun tooLongIsShortened() {
        val l = listOf(seg(1, 1000, 2000), seg(2, 5000))
        assertEquals(500L, Transitions.effective(l, 0))
    }

    @Test fun tooShortClipsGetNone() {
        val l = listOf(seg(1, 300, 1000), seg(2, 5000))
        assertEquals(0L, Transitions.effective(l, 0))
    }

    @Test fun gainOfEachSide() {
        val l = listOf(seg(1, 5000, 1000), seg(2, 5000))
        assertEquals(0.5f, Transitions.gain(l, 0, 4750), 0.001f)
        assertEquals(1f, Transitions.gain(l, 0, 1000), 0f)
        assertEquals(0f, Transitions.gain(l, 1, 0), 0.001f)
        assertEquals(1f, Transitions.gain(l, 1, 500), 0.001f)
        assertEquals(true, Transitions.touches(l, 1))
    }

    @Test fun mergeKeepsTheSecondClipsTransition() {
        val a = Segment(1, 0, 1000, uri = "x")
        val b = Segment(2, 1000, 2000, uri = "x", transitionMs = 600)
        val merged = com.screenx.ai.editor.SegmentOps.mergeWithNext(listOf(a, b), 1)!!
        assertEquals(600L, merged[0].transitionMs)
    }
}

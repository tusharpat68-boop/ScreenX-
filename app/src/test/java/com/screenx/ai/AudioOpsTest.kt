package com.screenx.ai

import com.screenx.ai.editor.ImportKind
import com.screenx.ai.editor.TimelineClip
import com.screenx.ai.editor.TimelineOps
import com.screenx.ai.editor.TrackKind
import com.screenx.ai.editor.WaveformMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOpsTest {
    private fun clip(id: Long, start: Long, length: Long, fin: Long = 0, fout: Long = 0) =
        TimelineClip(id, 0L, "u$id", "c$id", ImportKind.AUDIO, start, 0L, length, 60_000L, fadeInMs = fin, fadeOutMs = fout)

    @Test
    fun fadeFactorRampsAtBothEnds() {
        // 10 s clip, 2 s fade in, 4 s fade out
        assertEquals(0f, TimelineOps.fadeFactor(0, 10_000, 2000, 4000), 0.001f)
        assertEquals(0.5f, TimelineOps.fadeFactor(1000, 10_000, 2000, 4000), 0.001f)
        assertEquals(1f, TimelineOps.fadeFactor(5000, 10_000, 2000, 4000), 0.001f)
        assertEquals(0.5f, TimelineOps.fadeFactor(8000, 10_000, 2000, 4000), 0.001f)
        assertEquals(0f, TimelineOps.fadeFactor(10_000, 10_000, 2000, 4000), 0.001f)
        assertEquals(1f, TimelineOps.fadeFactor(0, 10_000, 0, 0), 0.001f)
    }

    @Test
    fun audioClampsVolumeAndFades() {
        var t = TimelineOps.add(emptyList(), TrackKind.AUDIO, clip(1, 0, 4000), 100L)
        t = TimelineOps.audio(t, 1L, volume = 3f, fadeInMs = 9000L, fadeOutMs = 1000L)
        val c = t[0].clips[0]
        assertEquals(1f, c.volume, 0f)
        assertEquals(2000L, c.fadeInMs) // at most half of 4000
        assertEquals(1000L, c.fadeOutMs)
        t = TimelineOps.audio(t, 1L, volume = 0.25f)
        assertEquals(0.25f, t[0].clips[0].volume, 0f)
        assertEquals(2000L, t[0].clips[0].fadeInMs)
    }

    @Test
    fun splitMovesFadesToTheOuterEdges() {
        val t = TimelineOps.add(emptyList(), TrackKind.AUDIO, clip(1, 0, 6000, fin = 1000, fout = 1500), 100L)
        val s = TimelineOps.split(t, 1L, 3000L, 7L)!![0].clips
        assertEquals(1000L, s[0].fadeInMs)
        assertEquals(0L, s[0].fadeOutMs)
        assertEquals(0L, s[1].fadeInMs)
        assertEquals(1500L, s[1].fadeOutMs)
    }

    @Test
    fun audibleAtOnlyReturnsSoundTracks() {
        var t = TimelineOps.add(emptyList(), TrackKind.VIDEO, clip(1, 0, 5000), 100L)
        t = TimelineOps.add(t, TrackKind.SFX, clip(2, 1000, 1000), 101L)
        t = TimelineOps.add(t, TrackKind.VOICEOVER, clip(3, 0, 5000), 102L)
        assertEquals(listOf(3L, 2L), TimelineOps.audibleAt(t, 1500L).map { it.id }.sortedDescending())
        assertEquals(listOf(3L), TimelineOps.audibleAt(t, 3000L).map { it.id })
    }

    @Test
    fun accumulateFindsLoudestSamplePerSlot() {
        // 1000 Hz, mono, 100 ms slots = 100 frames per slot
        val pcm = ShortArray(300) { i -> if (i < 100) 1000 else if (i < 200) -16384 else 0 }
        val peaks = FloatArray(3)
        WaveformMath.accumulate(peaks, pcm, 1, 1000, 0L, 100L)
        assertEquals(1000f / 32768f, peaks[0], 0.0001f)
        assertEquals(0.5f, peaks[1], 0.0001f)
        assertEquals(0f, peaks[2], 0f)
    }

    @Test
    fun accumulateHandlesStereoOffsetAndOverflow() {
        // stereo: left quiet, right loud; starts at 150 ms, so it covers the rest of slot 1 and slot 2
        val pcm = ShortArray(2 * 100) { i -> if (i % 2 == 0) 100 else 8192 }
        val peaks = FloatArray(3)
        WaveformMath.accumulate(peaks, pcm, 2, 1000, 150_000L, 100L)
        assertEquals(0f, peaks[0], 0f)
        assertEquals(0.25f, peaks[1], 0.0001f)
        assertEquals(0.25f, peaks[2], 0.0001f)
        // more sound than slots: ignored, no crash
        WaveformMath.accumulate(FloatArray(1), ShortArray(500) { 5 }, 1, 1000, 0L, 100L)
    }

    @Test
    fun sliceTakesTheMaxUnderEachBar() {
        val peaks = floatArrayOf(0.1f, 0.9f, 0.2f, 0.3f)
        val two = WaveformMath.slice(peaks, 100L, 0L, 400L, 2)
        assertEquals(0.9f, two[0], 0f)
        assertEquals(0.3f, two[1], 0f)
        val four = WaveformMath.slice(peaks, 100L, 0L, 400L, 4)
        assertTrue(four.contentEquals(peaks))
        // a window inside the sound, and a window past its end
        assertEquals(0.9f, WaveformMath.slice(peaks, 100L, 100L, 200L, 1)[0], 0f)
        assertEquals(0.3f, WaveformMath.slice(peaks, 100L, 900L, 1000L, 1)[0], 0f)
        assertEquals(0, WaveformMath.slice(peaks, 100L, 0L, 400L, 0).size)
    }
}

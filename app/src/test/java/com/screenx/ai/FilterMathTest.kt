package com.screenx.ai

import com.screenx.ai.editor.FilterMath
import com.screenx.ai.editor.FilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMathTest {
    @Test fun noneIsIdentity() {
        assertTrue(FilterMath.params(FilterPreset.NONE, 1f).isIdentity)
    }

    @Test fun zeroStrengthIsIdentityForEveryLook() {
        for (f in FilterPreset.values()) assertTrue(f.name, FilterMath.params(f, 0f).isIdentity)
    }

    @Test fun halfStrengthIsHalfway() {
        val p = FilterMath.params(FilterPreset.WARM, 0.5f)
        assertEquals(1.06f, p.r, 0.0001f)
        assertEquals(0.94f, p.b, 0.0001f)
        assertEquals(-50f, FilterMath.params(FilterPreset.BW, 0.5f).satPct, 0.0001f)
    }

    @Test fun negativeIsAllOrNothing() {
        assertTrue(FilterMath.params(FilterPreset.NEGATIVE, 1f).invert)
        assertTrue(FilterMath.params(FilterPreset.NEGATIVE, 0.2f).isIdentity)
    }

    @Test fun identityMatrixLeavesColorsAlone() {
        val m = FilterMath.colorMatrix(FilterMath.Params())
        for (row in 0 until 3) for (col in 0 until 3) assertEquals(if (row == col) 1f else 0f, m[row * 5 + col], 0.0001f)
        for (row in 0 until 4) assertEquals(0f, m[row * 5 + 4], 0.0001f)
        assertEquals(1f, m[18], 0f)
    }

    @Test fun greyMatrixGivesEqualChannels() {
        val m = FilterMath.colorMatrix(FilterMath.params(FilterPreset.BW, 1f))
        // Any pixel gives the same value in red, green and blue.
        fun ch(row: Int, r: Float, g: Float, b: Float) = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]
        assertEquals(ch(0, 200f, 100f, 50f), ch(1, 200f, 100f, 50f), 0.01f)
        assertEquals(ch(1, 200f, 100f, 50f), ch(2, 200f, 100f, 50f), 0.01f)
    }

    @Test fun invertFlipsWhiteToBlack() {
        val m = FilterMath.colorMatrix(FilterMath.params(FilterPreset.NEGATIVE, 1f))
        assertEquals(0f, m[0] * 255f + m[4], 0.01f)
        assertEquals(255f, m[0] * 0f + m[4], 0.01f)
    }

    @Test fun warmthScales() {
        val k = FilterMath.warmthScales(1f, 0f)
        assertTrue(k[0] > 1f && k[2] < 1f)
        assertEquals(1f, FilterMath.warmthScales(0f, 0f)[1], 0f)
    }
}

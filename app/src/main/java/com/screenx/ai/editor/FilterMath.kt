package com.screenx.ai.editor

/**
 * The numbers behind each one-tap look. The export builds its Media3 effects from them and the
 * filter thumbnails are tinted from the same numbers, so what you pick is what you get.
 */
object FilterMath {
    /**
     * [satPct] is the saturation change in percent (-100 = grey), [contrast] and [brightness] are
     * changes (0 = none), [r] [g] [b] multiply the color channels (1 = none).
     */
    class Params(
        val satPct: Float = 0f,
        val contrast: Float = 0f,
        val brightness: Float = 0f,
        val r: Float = 1f,
        val g: Float = 1f,
        val b: Float = 1f,
        val invert: Boolean = false
    ) {
        val isIdentity: Boolean
            get() = satPct == 0f && contrast == 0f && brightness == 0f && r == 1f && g == 1f && b == 1f && !invert
    }

    private fun full(f: FilterPreset): Params = when (f) {
        FilterPreset.NONE -> Params()
        FilterPreset.VIVID -> Params(satPct = 35f, contrast = 0.12f)
        FilterPreset.WARM -> Params(r = 1.12f, b = 0.88f)
        FilterPreset.COOL -> Params(r = 0.9f, b = 1.12f)
        FilterPreset.VINTAGE -> Params(satPct = -30f, contrast = -0.05f, r = 1.08f, b = 0.85f)
        FilterPreset.FADE -> Params(satPct = -15f, contrast = -0.18f, brightness = 0.06f)
        FilterPreset.BW -> Params(satPct = -100f)
        FilterPreset.NEGATIVE -> Params(invert = true)
        FilterPreset.CINEMATIC -> Params(satPct = -10f, contrast = 0.15f, r = 1.06f, g = 0.98f, b = 1.04f)
        FilterPreset.SUNSET -> Params(satPct = 20f, r = 1.18f, b = 0.85f)
        FilterPreset.MINT -> Params(brightness = 0.04f, r = 0.92f, g = 1.06f, b = 1.04f)
        FilterPreset.NOIR -> Params(satPct = -100f, contrast = 0.3f)
        FilterPreset.DRAMA -> Params(satPct = 15f, contrast = 0.3f, brightness = -0.04f)
        FilterPreset.SOFT -> Params(satPct = -8f, contrast = -0.12f, brightness = 0.05f)
    }

    /** The look at [strength] 0..1 (0 = untouched picture, 1 = the full look). Negative is all or nothing. */
    fun params(filter: FilterPreset, strength: Float): Params {
        val s = strength.coerceIn(0f, 1f)
        val p = full(filter)
        if (p.invert) return if (s >= 0.5f) p else Params()
        return Params(
            satPct = p.satPct * s,
            contrast = p.contrast * s,
            brightness = p.brightness * s,
            r = 1f + (p.r - 1f) * s,
            g = 1f + (p.g - 1f) * s,
            b = 1f + (p.b - 1f) * s
        )
    }

    /** Whether the strength slider does anything for this look (the negative cannot be partial). */
    fun hasStrength(filter: FilterPreset): Boolean = filter != FilterPreset.NONE && filter != FilterPreset.NEGATIVE

    /** Channel multipliers from the warmth and tint sliders (-1..1 each). */
    fun warmthScales(warmth: Float, tint: Float): FloatArray {
        val w = warmth.coerceIn(-1f, 1f)
        val t = tint.coerceIn(-1f, 1f)
        return floatArrayOf(1f + 0.15f * w, 1f - 0.12f * t, 1f - 0.15f * w)
    }

    /**
     * A 4x5 color matrix (row-major, offsets in 0..255, like android.graphics.ColorMatrix) that
     * approximates [p]. Used to tint the filter thumbnails.
     */
    fun colorMatrix(p: Params): FloatArray {
        val f = 1f + p.satPct / 100f
        val luma = floatArrayOf(0.213f, 0.715f, 0.072f)
        val scale = floatArrayOf(p.r, p.g, p.b)
        val c = 1f + p.contrast
        val out = FloatArray(20)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val sat = (if (row == col) f else 0f) + (1f - f) * luma[col]
                out[row * 5 + col] = scale[row] * c * sat
            }
            out[row * 5 + 4] = 255f * scale[row] * (0.5f * (1f - c) + p.brightness)
        }
        out[18] = 1f
        if (p.invert) {
            for (row in 0 until 3) {
                for (col in 0 until 3) out[row * 5 + col] = -out[row * 5 + col]
                out[row * 5 + 4] = 255f - out[row * 5 + 4]
            }
        }
        return out
    }
}

package com.screenx.ai.utils

import java.util.Locale

object TimeFormat {
    /** Formats milliseconds as mm:ss, or h:mm:ss when an hour or more. */
    fun elapsed(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

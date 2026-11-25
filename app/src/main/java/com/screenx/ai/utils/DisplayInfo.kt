package com.screenx.ai.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

object DisplayInfo {
    /** Real size of the screen in pixels, including system bars. */
    @Suppress("DEPRECATION")
    fun realSize(context: Context): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) {
            val m = context.resources.displayMetrics
            return m.widthPixels to m.heightPixels
        }
        val dm = DisplayMetrics()
        display.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    fun dpi(context: Context): Int = context.resources.displayMetrics.densityDpi
}

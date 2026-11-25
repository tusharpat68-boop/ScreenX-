package com.screenx.ai.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * A small text watermark drawn over the screen while recording. It cannot be touched, and
 * because it is part of the screen, it is recorded into the video.
 * [corner]: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right.
 */
class WatermarkOverlay(context: Context, text: String, corner: Int) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private var attached = false

    private val view = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        textSize = 13f
        setShadowLayer(3f, 0f, 1f, 0xCC000000.toInt())
        val h = (10 * density).toInt()
        val v = (5 * density).toInt()
        setPadding(h, v, h, v)
        background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(0x55000000)
        }
    }

    // Android 12+ blocks touches that pass through overlays above 80% opacity.
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        alpha = 0.8f
        val top = corner == 0 || corner == 1
        val left = corner == 0 || corner == 2
        gravity = (if (top) Gravity.TOP else Gravity.BOTTOM) or (if (left) Gravity.START else Gravity.END)
        x = (12 * density).toInt()
        y = ((if (top) 40 else 56) * density).toInt()
    }

    fun show(): Boolean = try {
        wm.addView(view, params)
        attached = true
        true
    } catch (e: Exception) {
        false
    }

    fun dismiss() {
        if (attached) {
            runCatching { wm.removeView(view) }
            attached = false
        }
    }
}

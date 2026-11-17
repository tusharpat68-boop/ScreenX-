package com.screenx.ai.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.compose.ui.graphics.toArgb
import com.screenx.ai.R
import com.screenx.ai.presentation.theme.AccentState
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.RecordingStatus
import com.screenx.ai.utils.TimeFormat
import kotlin.math.abs
import kotlin.math.max

/**
 * Draggable floating recording controller drawn over other apps (needs the overlay permission).
 * Collapsed: a small round bubble with a blinking record dot. Expanded: timer, pause/resume,
 * optional camera and drawing buttons, stop and hide. Tap the red dot to collapse it again. It is part of the screen, so it also appears
 * in the recording.
 */
class FloatingControls(
    context: Context,
    private val config: FloatingConfig,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
    private val onStop: () -> Unit,
    private val onHide: () -> Unit,
    private val onMoved: (x: Int, y: Int) -> Unit,
    onToggleCamera: (() -> Unit)? = null,
    onToggleDrawing: (() -> Unit)? = null
) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    // Button size: the chosen size, shrunk if needed so the expanded row fits the screen width.
    private val unit: Int = run {
        val desired = (when (config.sizeLevel) { 0 -> 40; 2 -> 64; else -> 52 } * density).toInt()
        val slots = 5.4f + (if (onToggleCamera != null) 1f else 0f) + (if (onToggleDrawing != null) 1f else 0f)
        val available = ctx.resources.displayMetrics.widthPixels - 24 * density
        minOf(desired, (available / slots).toInt())
    }

    private var expanded = false
    private var attached = false
    private var paused = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val dm = ctx.resources.displayMetrics
        if (config.x >= 0 && config.y >= 0) {
            x = config.x
            y = config.y
        } else {
            x = dm.widthPixels - unit - (16 * density).toInt()
            y = (120 * density).toInt()
        }
    }

    private val container = DragLayout().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        alpha = config.opacityPercent / 100f
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(AccentState.primary.toArgb(), AccentState.secondary.toArgb())
        ).apply { cornerRadius = unit / 2f }
    }

    private val dot = button(R.drawable.ic_overlay_dot, R.string.home_recording) { toggleExpanded() }
    private val timer = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.32f)
        gravity = Gravity.CENTER
        minWidth = (unit * 1.4f).toInt()
        setPadding(unit / 8, 0, unit / 8, 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, unit)
        text = TimeFormat.elapsed(0)
    }
    private val playPause = button(R.drawable.ic_overlay_pause, R.string.action_pause) {
        if (paused) onResume() else onPause()
    }
    private val cameraButton: ImageView? = onToggleCamera?.let { toggle ->
        button(R.drawable.ic_overlay_camera, R.string.overlay_camera) { toggle() }
    }
    private val drawButton: ImageView? = onToggleDrawing?.let { toggle ->
        button(R.drawable.ic_draw_pen, R.string.overlay_drawing) { toggle() }
    }
    private val stop = button(R.drawable.ic_overlay_stop, R.string.action_stop) { onStop() }
    private val hide = button(R.drawable.ic_overlay_close, R.string.overlay_hide) { onHide() }

    private val blink = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f).apply {
        duration = 700
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
    }

    init {
        container.addView(dot)
        container.addView(timer)
        container.addView(playPause)
        cameraButton?.let { container.addView(it) }
        drawButton?.let { container.addView(it) }
        container.addView(stop)
        container.addView(hide)
        applyExpanded()
    }

    /** Adds the controller to the screen. Returns false if Android refuses (no permission). */
    fun show(): Boolean = try {
        wm.addView(container, params)
        attached = true
        true
    } catch (e: Exception) {
        false
    }

    fun dismiss() {
        blink.cancel()
        if (attached) {
            runCatching { wm.removeView(container) }
            attached = false
        }
    }

    /** Hides or shows the controller without losing its state. */
    fun setVisible(visible: Boolean) {
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun update(status: RecordingStatus, elapsedMs: Long) {
        paused = status == RecordingStatus.PAUSED
        timer.text = TimeFormat.elapsed(elapsedMs)
        playPause.setImageResource(if (paused) R.drawable.ic_overlay_play else R.drawable.ic_overlay_pause)
        playPause.contentDescription = ctx.getString(if (paused) R.string.action_resume else R.string.action_pause)
        if (paused) {
            blink.cancel()
            dot.alpha = 0.5f
        } else if (!blink.isStarted) {
            blink.start()
        }
        clampToScreen()
    }

    private fun toggleExpanded() = setExpanded(!expanded)

    private fun setExpanded(value: Boolean) {
        expanded = value
        applyExpanded()
        // After the window resizes, make sure it is still fully on screen.
        container.doOnNextLayout { clampToScreen() }
    }

    private fun applyExpanded() {
        val v = if (expanded) View.VISIBLE else View.GONE
        timer.visibility = v
        playPause.visibility = v
        cameraButton?.visibility = v
        drawButton?.visibility = v
        stop.visibility = v
        hide.visibility = v
    }

    private fun clampToScreen() {
        if (!attached) return
        val dm = ctx.resources.displayMetrics
        val nx = params.x.coerceIn(0, max(0, dm.widthPixels - container.width))
        val ny = params.y.coerceIn(0, max(0, dm.heightPixels - container.height))
        if (nx != params.x || ny != params.y) {
            params.x = nx
            params.y = ny
            runCatching { wm.updateViewLayout(container, params) }
        }
    }

    private fun button(icon: Int, description: Int, onClick: () -> Unit): ImageView =
        ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(unit, unit)
            val pad = unit / 4
            setPadding(pad, pad, pad, pad)
            setImageResource(icon)
            contentDescription = ctx.getString(description)
            setOnClickListener { onClick() }
        }

    /** Row that lets the user drag the whole controller, while child buttons still get taps. */
    private inner class DragLayout : LinearLayout(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> begin(ev)
                MotionEvent.ACTION_MOVE ->
                    if (!dragging && moved(ev)) dragging = true
            }
            return dragging
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> begin(ev)
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && moved(ev)) dragging = true
                    if (dragging) {
                        val dm = ctx.resources.displayMetrics
                        params.x = (startX + (ev.rawX - downX).toInt())
                            .coerceIn(0, max(0, dm.widthPixels - width))
                        params.y = (startY + (ev.rawY - downY).toInt())
                            .coerceIn(0, max(0, dm.heightPixels - height))
                        runCatching { wm.updateViewLayout(this, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        onMoved(params.x, params.y)
                    }
                }
            }
            return true
        }

        private fun begin(ev: MotionEvent) {
            downX = ev.rawX
            downY = ev.rawY
            startX = params.x
            startY = params.y
            dragging = false
        }

        private fun moved(ev: MotionEvent): Boolean =
            abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop
    }
}

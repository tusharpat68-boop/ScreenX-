package com.screenx.ai.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.doOnNextLayout
import androidx.compose.ui.graphics.toArgb
import com.screenx.ai.R
import com.screenx.ai.presentation.theme.AccentState
import kotlin.math.abs
import kotlin.math.max

/**
 * Live drawing over other apps. Two overlay windows:
 *  - a full-screen canvas (touchable only while you draw), and
 *  - a small draggable toolbar that can shrink to a pen bubble.
 * While the toolbar is minimized, or in cursor mode, touches pass through to the app below,
 * and your drawings stay visible. Drawings and toolbar are part of the screen, so they are
 * recorded too.
 */
class DrawingOverlay(
    context: Context,
    private val canHide: Boolean
) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()

    private val view = DrawingView(ctx)

    private var expanded = false
    private var interact = false
    private var visible = true
    private var attached = false
    private var sizeIndex = 1
    private var colorIndex = 1

    private val sizesDp = floatArrayOf(4f, 8f, 14f)
    private val sizeLabels = arrayOf("S", "M", "L")
    private val palette = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFEF4444.toInt(), 0xFFFACC15.toInt(), 0xFF22C55E.toInt(),
        0xFF22D3EE.toInt(), 0xFF246BFD.toInt(), 0xFF8B5CF6.toInt()
    )

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    // Android 12+ only lets touches pass through overlays that are at most 80% opaque.
    private val canvasParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        alpha = 0.8f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val toolbarParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(12f)
        y = dp(160f)
    }

    // ---- toolbar views ----

    private val toolButtons = LinkedHashMap<DrawTool, ImageView>()
    private val swatches = ArrayList<GradientDrawable>()
    private val selectedBg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x44FFFFFF)
    }

    private val root = DragFrame()
    private val panel = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        background = GradientDrawable().apply {
            cornerRadius = dp(20f).toFloat()
            setColor(0xF0121827.toInt())
            setStroke(dp(1.5f), 0x66FFFFFF)
        }
    }
    private val bubble = ImageView(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(dp(48f), dp(48f))
        setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        setImageResource(R.drawable.ic_draw_pen)
        contentDescription = ctx.getString(R.string.overlay_drawing)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(AccentState.primary.toArgb(), AccentState.secondary.toArgb())
        ).apply { shape = GradientDrawable.OVAL }
        setOnClickListener { setExpanded(true) }
    }
    private val sizeButton = TextView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        textSize = 14f
        contentDescription = ctx.getString(R.string.draw_size)
        setOnClickListener {
            sizeIndex = (sizeIndex + 1) % sizesDp.size
            applyStyle()
        }
    }
    private lateinit var interactButton: ImageView

    init {
        // Row 1: tools
        val tools = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addTool(tools, DrawTool.PEN, R.drawable.ic_draw_pen, R.string.draw_pen)
        addTool(tools, DrawTool.HIGHLIGHTER, R.drawable.ic_draw_highlighter, R.string.draw_highlighter)
        addTool(tools, DrawTool.ERASER, R.drawable.ic_draw_eraser, R.string.draw_eraser)
        addTool(tools, DrawTool.ARROW, R.drawable.ic_draw_arrow, R.string.draw_arrow)
        addTool(tools, DrawTool.CIRCLE, R.drawable.ic_draw_circle, R.string.draw_circle)
        addTool(tools, DrawTool.RECTANGLE, R.drawable.ic_draw_rect, R.string.draw_rect)

        // Row 2: colors
        val colors = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        palette.forEachIndexed { index, c ->
            val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
            swatches.add(dot)
            val v = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26f), dp(26f)).apply {
                    setMargins(dp(7f), dp(7f), dp(7f), dp(7f))
                }
                background = dot
                contentDescription = ctx.getString(R.string.draw_color)
                setOnClickListener {
                    colorIndex = index
                    // Picking a color while erasing switches back to the pen.
                    if (view.tool == DrawTool.ERASER) selectTool(DrawTool.PEN)
                    applyStyle()
                }
            }
            colors.addView(v)
        }

        // Row 3: size, undo, clear, cursor mode, minimize, hide
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(sizeButton)
        actions.addView(iconButton(R.drawable.ic_draw_undo, R.string.draw_undo) { view.undo() })
        actions.addView(iconButton(R.drawable.ic_draw_clear, R.string.draw_clear) { view.clear() })
        interactButton = iconButton(R.drawable.ic_draw_cursor, R.string.draw_interact) {
            interact = !interact
            applyTouchable()
            refreshHighlights()
        }
        actions.addView(interactButton)
        actions.addView(iconButton(R.drawable.ic_overlay_minimize, R.string.overlay_minimize) { setExpanded(false) })
        if (canHide) {
            actions.addView(iconButton(R.drawable.ic_overlay_close, R.string.draw_hide) { toggleVisible() })
        }

        panel.addView(tools)
        panel.addView(colors)
        panel.addView(actions)
        root.addView(bubble)
        root.addView(panel)

        selectTool(DrawTool.PEN)
        applyStyle()
        applyExpandedViews()
    }

    // ---- public API ----

    /** Adds both windows (starting minimized). Returns false if Android refuses. */
    fun show(): Boolean = try {
        wm.addView(view, canvasParams)
        wm.addView(root, toolbarParams)
        attached = true
        true
    } catch (e: Exception) {
        runCatching { wm.removeView(view) }
        false
    }

    fun dismiss() {
        if (!attached) return
        runCatching { wm.removeView(root) }
        runCatching { wm.removeView(view) }
        attached = false
    }

    /** Hides or shows the drawing and the toolbar. Drawings are kept while hidden. */
    fun toggleVisible() {
        visible = !visible
        val v = if (visible) View.VISIBLE else View.GONE
        view.visibility = v
        root.visibility = v
        applyTouchable()
    }

    // ---- internals ----

    private fun setExpanded(value: Boolean) {
        expanded = value
        if (value) interact = false
        applyExpandedViews()
        applyTouchable()
        refreshHighlights()
        root.doOnNextLayout { clampToolbar() }
    }

    private fun applyExpandedViews() {
        bubble.visibility = if (expanded) View.GONE else View.VISIBLE
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    /** The canvas only captures touches while you are actually drawing. */
    private fun applyTouchable() {
        val touchable = visible && expanded && !interact
        canvasParams.flags = if (touchable) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (attached) runCatching { wm.updateViewLayout(view, canvasParams) }
    }

    private fun selectTool(tool: DrawTool) {
        view.tool = tool
        // Drawing again leaves cursor mode.
        if (interact) {
            interact = false
            applyTouchable()
        }
        refreshHighlights()
    }

    private fun applyStyle() {
        view.color = palette[colorIndex]
        view.brushSizeDp = sizesDp[sizeIndex]
        sizeButton.text = sizeLabels[sizeIndex]
        swatches.forEachIndexed { i, d ->
            if (i == colorIndex) d.setStroke(dp(3f), Color.WHITE) else d.setStroke(dp(1f), 0x66000000)
        }
    }

    private fun refreshHighlights() {
        toolButtons.forEach { (tool, button) ->
            button.background = if (tool == view.tool && !interact) selectedBg else null
        }
        interactButton.background = if (interact) selectedBg else null
    }

    private fun clampToolbar() {
        if (!attached) return
        val dm = ctx.resources.displayMetrics
        val nx = toolbarParams.x.coerceIn(0, max(0, dm.widthPixels - root.width))
        val ny = toolbarParams.y.coerceIn(0, max(0, dm.heightPixels - root.height))
        if (nx != toolbarParams.x || ny != toolbarParams.y) {
            toolbarParams.x = nx
            toolbarParams.y = ny
            runCatching { wm.updateViewLayout(root, toolbarParams) }
        }
    }

    private fun addTool(row: LinearLayout, tool: DrawTool, @DrawableRes icon: Int, @StringRes desc: Int) {
        val button = iconButton(icon, desc) { selectTool(tool) }
        toolButtons[tool] = button
        row.addView(button)
    }

    private fun iconButton(@DrawableRes icon: Int, @StringRes desc: Int, onClick: () -> Unit): ImageView =
        ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
            setPadding(dp(9f), dp(9f), dp(9f), dp(9f))
            setImageResource(icon)
            contentDescription = ctx.getString(desc)
            setOnClickListener { onClick() }
        }

    /** Lets the user drag the toolbar by any empty part, while buttons still get taps. */
    private inner class DragFrame : FrameLayout(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> begin(ev)
                MotionEvent.ACTION_MOVE -> if (!dragging && moved(ev)) dragging = true
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
                        toolbarParams.x = (startX + (ev.rawX - downX).toInt())
                            .coerceIn(0, max(0, dm.widthPixels - width))
                        toolbarParams.y = (startY + (ev.rawY - downY).toInt())
                            .coerceIn(0, max(0, dm.heightPixels - height))
                        runCatching { wm.updateViewLayout(this, toolbarParams) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }

        private fun begin(ev: MotionEvent) {
            downX = ev.rawX
            downY = ev.rawY
            startX = toolbarParams.x
            startY = toolbarParams.y
            dragging = false
        }

        private fun moved(ev: MotionEvent): Boolean =
            abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop
    }
}

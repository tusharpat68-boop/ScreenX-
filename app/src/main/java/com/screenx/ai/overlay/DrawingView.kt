package com.screenx.ai.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class DrawTool { PEN, HIGHLIGHTER, ERASER, ARROW, CIRCLE, RECTANGLE }

/**
 * A transparent canvas for live annotation. Everything drawn is kept as vector items, so
 * undo works and the eraser removes whole strokes or shapes you touch.
 */
class DrawingView(context: Context) : View(context) {

    var tool: DrawTool = DrawTool.PEN
    var color: Int = 0xFFEF4444.toInt()
    var brushSizeDp: Float = 8f

    private class DrawItem(val tool: DrawTool, val color: Int, val width: Float) {
        val points = ArrayList<PointF>() // pen/highlighter: the path; shapes: start and end
        val path = Path()
    }

    private class IndexedItem(val index: Int, val item: DrawItem)

    private sealed interface UndoOp {
        class Added(val item: DrawItem) : UndoOp
        class Removed(val removed: List<IndexedItem>) : UndoOp
    }

    private val density = resources.displayMetrics.density
    private val items = ArrayList<DrawItem>()
    private val undoStack = ArrayList<UndoOp>()
    private var current: DrawItem? = null
    private var gestureRemoved = ArrayList<IndexedItem>()
    private var eraserPoint: PointF? = null
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ---- public actions ----

    fun undo(): Boolean {
        val op = undoStack.removeLastOrNull() ?: return false
        when (op) {
            is UndoOp.Added -> items.remove(op.item)
            is UndoOp.Removed -> for (r in op.removed.asReversed()) {
                items.add(r.index.coerceAtMost(items.size), r.item)
            }
        }
        invalidate()
        return true
    }

    fun clear() {
        if (items.isEmpty()) return
        // Recorded from the end so that undo restores the original order.
        val removed = ArrayList<IndexedItem>()
        for (i in items.indices.reversed()) removed.add(IndexedItem(i, items[i]))
        items.clear()
        pushUndo(UndoOp.Removed(removed))
        invalidate()
    }

    // ---- touch ----

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(ev.x, ev.y)
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until ev.historySize) move(ev.getHistoricalX(h), ev.getHistoricalY(h))
                move(ev.x, ev.y)
            }
            MotionEvent.ACTION_UP -> {
                move(ev.x, ev.y)
                up()
            }
            MotionEvent.ACTION_CANCEL -> up()
        }
        invalidate()
        return true
    }

    private fun down(x: Float, y: Float) {
        if (tool == DrawTool.ERASER) {
            gestureRemoved = ArrayList()
            eraserPoint = PointF(x, y)
            erase(x, y)
            return
        }
        val item = DrawItem(tool, color, widthFor(tool))
        item.points.add(PointF(x, y))
        when (tool) {
            DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                item.path.moveTo(x, y)
                item.path.lineTo(x + 0.1f, y) // makes a single tap draw a dot
            }
            else -> item.points.add(PointF(x, y))
        }
        current = item
    }

    private fun move(x: Float, y: Float) {
        if (tool == DrawTool.ERASER) {
            eraserPoint = PointF(x, y)
            erase(x, y)
            return
        }
        val item = current ?: return
        when (item.tool) {
            DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                val last = item.points.last()
                item.path.quadTo(last.x, last.y, (x + last.x) / 2f, (y + last.y) / 2f)
                item.points.add(PointF(x, y))
            }
            else -> item.points[1].set(x, y)
        }
    }

    private fun up() {
        if (tool == DrawTool.ERASER) {
            if (gestureRemoved.isNotEmpty()) pushUndo(UndoOp.Removed(gestureRemoved))
            gestureRemoved = ArrayList()
            eraserPoint = null
            return
        }
        val item = current ?: return
        current = null
        val isShape = item.tool == DrawTool.ARROW || item.tool == DrawTool.CIRCLE || item.tool == DrawTool.RECTANGLE
        if (isShape) {
            val a = item.points[0]
            val b = item.points[1]
            if (hypot(b.x - a.x, b.y - a.y) < 6f * density) return // too small, ignore
        }
        items.add(item)
        pushUndo(UndoOp.Added(item))
    }

    private fun pushUndo(op: UndoOp) {
        undoStack.add(op)
        if (undoStack.size > 100) undoStack.removeAt(0)
    }

    private fun widthFor(tool: DrawTool): Float =
        if (tool == DrawTool.HIGHLIGHTER) brushSizeDp * 2.5f * density else brushSizeDp * density

    private fun eraserRadius(): Float = max(16f * density, brushSizeDp * 2.5f * density)

    private fun erase(x: Float, y: Float) {
        val radius = eraserRadius()
        for (i in items.indices.reversed()) {
            if (hits(items[i], x, y, radius)) {
                gestureRemoved.add(IndexedItem(i, items.removeAt(i)))
            }
        }
    }

    // ---- hit testing (for the eraser) ----

    private fun hits(item: DrawItem, x: Float, y: Float, radius: Float): Boolean {
        val tol = radius + item.width / 2f
        return when (item.tool) {
            DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                val pts = item.points
                if (pts.size == 1) {
                    hypot(x - pts[0].x, y - pts[0].y) <= tol
                } else {
                    (1 until pts.size).any { i ->
                        distToSegment(x, y, pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y) <= tol
                    }
                }
            }
            DrawTool.ARROW -> {
                val a = item.points[0]
                val b = item.points[1]
                distToSegment(x, y, a.x, a.y, b.x, b.y) <= tol
            }
            DrawTool.RECTANGLE -> {
                val a = item.points[0]
                val b = item.points[1]
                val l = min(a.x, b.x)
                val t = min(a.y, b.y)
                val r = max(a.x, b.x)
                val bt = max(a.y, b.y)
                distToSegment(x, y, l, t, r, t) <= tol ||
                    distToSegment(x, y, r, t, r, bt) <= tol ||
                    distToSegment(x, y, r, bt, l, bt) <= tol ||
                    distToSegment(x, y, l, bt, l, t) <= tol
            }
            DrawTool.CIRCLE -> {
                val a = item.points[0]
                val b = item.points[1]
                val cx = (a.x + b.x) / 2f
                val cy = (a.y + b.y) / 2f
                val rx = abs(b.x - a.x) / 2f
                val ry = abs(b.y - a.y) / 2f
                if (rx < 1f || ry < 1f) {
                    hypot(x - cx, y - cy) <= tol
                } else {
                    val d = hypot((x - cx) / rx, (y - cy) / ry)
                    abs(d - 1f) * min(rx, ry) <= tol
                }
            }
            DrawTool.ERASER -> false
        }
    }

    private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        for (item in items) drawItem(canvas, item)
        current?.let { drawItem(canvas, it) }
        eraserPoint?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = 0xCCFFFFFF.toInt()
            canvas.drawCircle(it.x, it.y, eraserRadius(), paint)
        }
    }

    private fun drawItem(canvas: Canvas, item: DrawItem) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = item.width
        paint.strokeCap = if (item.tool == DrawTool.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
        paint.color = if (item.tool == DrawTool.HIGHLIGHTER) {
            (item.color and 0x00FFFFFF) or 0x66000000
        } else {
            item.color
        }
        when (item.tool) {
            DrawTool.PEN, DrawTool.HIGHLIGHTER -> canvas.drawPath(item.path, paint)
            DrawTool.ARROW -> drawArrow(canvas, item)
            DrawTool.CIRCLE -> {
                setRect(item)
                canvas.drawOval(rect, paint)
            }
            DrawTool.RECTANGLE -> {
                setRect(item)
                canvas.drawRect(rect, paint)
            }
            DrawTool.ERASER -> Unit
        }
    }

    private fun setRect(item: DrawItem) {
        val a = item.points[0]
        val b = item.points[1]
        rect.set(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    private fun drawArrow(canvas: Canvas, item: DrawItem) {
        val a = item.points[0]
        val b = item.points[1]
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        if (hypot(b.x - a.x, b.y - a.y) < 1f) return
        val angle = atan2(b.y - a.y, b.x - a.x)
        val headLen = max(14f * density, item.width * 3f)
        val spread = 0.52f // about 30 degrees
        for (sign in intArrayOf(-1, 1)) {
            val a2 = angle + Math.PI.toFloat() + sign * spread
            canvas.drawLine(b.x, b.y, b.x + headLen * cos(a2), b.y + headLen * sin(a2), paint)
        }
    }
}

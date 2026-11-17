package com.screenx.ai.overlay

import android.content.Context
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.compose.ui.graphics.toArgb
import com.screenx.ai.R
import com.screenx.ai.presentation.theme.AccentState
import com.screenx.ai.camera.CameraCapabilities
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
import kotlin.math.abs
import kotlin.math.max

/**
 * Draggable face-camera bubble drawn over other apps (needs overlay + camera permission).
 * Tap = flip camera, long-press = turn the camera off. While the camera is off the bubble is
 * hidden and the camera is released, so Android's camera indicator turns off too.
 * Like every overlay, the bubble is part of the screen and appears in the recording.
 */
class FacecamOverlay(
    context: Context,
    private val owner: LifecycleOwner,
    private val config: FacecamConfig,
    private val onMoved: (x: Int, y: Int) -> Unit,
    private val onWarning: (String) -> Unit
) {
    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val circle = config.shape == FacecamShape.CIRCLE

    private val sizePx = (when (config.sizeLevel) { 0 -> 90; 2 -> 180; 3 -> 240; else -> 130 } * density).toInt()
    private val ringPx = (3 * density).toInt()
    private val innerW = sizePx
    private val innerH = if (circle) sizePx else sizePx * 3 / 4
    private val cornerPx = 16 * density

    private var lens = config.lens
    private var attached = false
    private var provider: ProcessCameraProvider? = null
    private var stateLive: LiveData<CameraState>? = null
    private var warnedThisBind = false

    var isCameraOn = true
        private set

    private val params = WindowManager.LayoutParams(
        innerW + 2 * ringPx,
        innerH + 2 * ringPx,
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
            x = (16 * density).toInt()
            y = dm.heightPixels - height - (200 * density).toInt()
        }
    }

    private val previewView = PreviewView(ctx).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (circle) outline.setOval(0, 0, view.width, view.height)
                else outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
    }

    private val root = FrameLayout(ctx).apply {
        setPadding(ringPx, ringPx, ringPx, ringPx)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(AccentState.primary.toArgb(), AccentState.secondary.toArgb())
        ).apply {
            if (circle) {
                shape = GradientDrawable.OVAL
            } else {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerPx + ringPx
            }
        }
        clipToOutline = true
        addView(previewView)
    }

    // touch handling
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longFired = false
    private val longPress = Runnable {
        if (!dragging) {
            longFired = true
            setCameraEnabled(false)
        }
    }

    init {
        root.setOnTouchListener { _, ev -> handleTouch(ev) }
    }

    /** Adds the bubble and starts the camera. Returns false if Android refuses (no permission). */
    fun show(): Boolean = try {
        wm.addView(root, params)
        attached = true
        bind()
        true
    } catch (e: Exception) {
        false
    }

    fun dismiss() {
        root.removeCallbacks(longPress)
        unbind()
        if (attached) {
            runCatching { wm.removeView(root) }
            attached = false
        }
    }

    /** Turns the camera (and the bubble) on or off without ending the recording. */
    fun setCameraEnabled(on: Boolean) {
        if (isCameraOn == on) return
        isCameraOn = on
        root.visibility = if (on) View.VISIBLE else View.GONE
        if (on) bind() else unbind()
    }

    fun flip() {
        val target = if (lens == CameraLens.FRONT) CameraLens.BACK else CameraLens.FRONT
        if (!CameraCapabilities.has(ctx, target)) {
            onWarning(ctx.getString(R.string.camera_flip_unavailable))
            return
        }
        lens = target
        if (isCameraOn) bind()
    }

    private fun bind() {
        warnedThisBind = false
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val selector = if (lens == CameraLens.FRONT) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                p.unbindAll()
                val camera = p.bindToLifecycle(owner, selector, preview)

                stateLive?.removeObservers(owner)
                val live = camera.cameraInfo.cameraState
                stateLive = live
                live.observe(owner) { state ->
                    val error = state.error
                    if (error != null && !warnedThisBind) {
                        warnedThisBind = true
                        onWarning(ctx.getString(R.string.warn_camera_unavailable, error.code.toString()))
                    }
                }
            } catch (e: Exception) {
                onWarning(ctx.getString(R.string.warn_camera_unavailable, e.message ?: e.javaClass.simpleName))
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun unbind() {
        stateLive?.removeObservers(owner)
        stateLive = null
        runCatching { provider?.unbindAll() }
    }

    private fun handleTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                startX = params.x
                startY = params.y
                dragging = false
                longFired = false
                root.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                    dragging = true
                    root.removeCallbacks(longPress)
                }
                if (dragging) {
                    val dm = ctx.resources.displayMetrics
                    params.x = (startX + (ev.rawX - downX).toInt())
                        .coerceIn(0, max(0, dm.widthPixels - params.width))
                    params.y = (startY + (ev.rawY - downY).toInt())
                        .coerceIn(0, max(0, dm.heightPixels - params.height))
                    runCatching { wm.updateViewLayout(root, params) }
                }
            }
            MotionEvent.ACTION_UP -> {
                root.removeCallbacks(longPress)
                if (dragging) {
                    dragging = false
                    onMoved(params.x, params.y)
                } else if (!longFired) {
                    flip()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                root.removeCallbacks(longPress)
                if (dragging) {
                    dragging = false
                    onMoved(params.x, params.y)
                }
            }
        }
        return true
    }
}

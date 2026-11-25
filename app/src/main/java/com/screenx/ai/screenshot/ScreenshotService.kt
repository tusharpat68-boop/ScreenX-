package com.screenx.ai.screenshot

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
import com.screenx.ai.storage.StorageConfig
import com.screenx.ai.utils.LocaleHelper
import kotlinx.coroutines.flow.first
import com.screenx.ai.domain.MediaKind
import com.screenx.ai.service.NotificationHelper
import com.screenx.ai.storage.MediaLibrary
import com.screenx.ai.utils.DisplayInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Takes one screenshot with MediaProjection: consent -> (optional countdown) -> one frame -> PNG.
 * Android requires a foreground service of type mediaProjection for this.
 */
class ScreenshotService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var latest: Bitmap? = null
    private var startedAt = 0L
    private var busy = false
    private var finished = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE -> handleCapture(intent)
            ACTION_CANCEL -> finish(null)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleCapture(intent: Intent) {
        NotificationHelper.ensureChannel(this)
        if (busy) {
            promote(buildNotification(0))
            return
        }
        busy = true
        val delaySec = intent.getIntExtra(EXTRA_DELAY, 3).coerceIn(0, 30)
        try {
            promote(buildNotification(delaySec))
        } catch (e: Exception) {
            finish(R.string.shots_failed)
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || data == null) {
            finish(R.string.error_no_consent)
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            null
        }
        if (p == null) {
            finish(R.string.shots_failed)
            return
        }
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                finish(null)
            }
        }
        p.registerCallback(callback, handler)
        projection = p
        projectionCallback = callback

        scope.launch {
            for (s in delaySec downTo 1) {
                notify(buildNotification(s))
                delay(1000)
            }
            notify(buildNotification(0))
            startCapture()
        }
    }

    private fun startCapture() {
        val p = projection ?: return
        val (w, h) = DisplayInfo.realSize(this)
        val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bmp = imageToBitmap(image, w, h)
                latest?.recycle()
                latest = bmp
            } finally {
                image.close()
            }
        }, handler)
        try {
            virtualDisplay = p.createVirtualDisplay(
                "ScreenXShot", w, h, DisplayInfo.dpi(this),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, handler
            )
        } catch (e: Exception) {
            finish(R.string.shots_failed)
            return
        }
        startedAt = SystemClock.elapsedRealtime()
        handler.postDelayed({ tryFinish() }, 400)
    }

    /** A screen that is not changing only sends one frame, so we wait briefly for the latest one. */
    private fun tryFinish() {
        if (finished) return
        val bmp = latest
        if (bmp != null) {
            latest = null
            releaseCapture()
            saveAndFinish(bmp)
        } else if (SystemClock.elapsedRealtime() - startedAt < 2500) {
            handler.postDelayed({ tryFinish() }, 200)
        } else {
            finish(R.string.shots_failed)
        }
    }

    private fun saveAndFinish(bitmap: Bitmap) {
        scope.launch {
            StorageConfig.folder =
                (application as ScreenXApp).settingsRepository.generalSettings.first().saveFolder
            val ok = withContext(Dispatchers.IO) {
                try {
                    MediaLibrary(applicationContext, MediaKind.IMAGE).saveBitmap(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            if (ok) ScreenshotEvents.notifySaved()
            finish(if (ok) R.string.shots_saved else R.string.shots_failed)
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    private fun releaseCapture() {
        handler.removeCallbacksAndMessages(null)
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.setOnImageAvailableListener(null, null) }
        runCatching { reader?.close() }
        reader = null
        projectionCallback?.let { runCatching { projection?.unregisterCallback(it) } }
        runCatching { projection?.stop() }
        projection = null
        projectionCallback = null
    }

    private fun finish(messageRes: Int?) {
        if (finished) return
        finished = true
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        releaseCapture()
        latest?.recycle()
        latest = null
        if (messageRes != null) {
            Toast.makeText(applicationContext, messageRes, Toast.LENGTH_SHORT).show()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promote(notification: Notification) {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(secondsLeft: Int): Notification {
        val cancel = PendingIntent.getService(
            this, 11,
            Intent(this, ScreenshotService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                if (secondsLeft > 0) getString(R.string.notif_shot_countdown, secondsLeft)
                else getString(R.string.notif_shot_taking)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(NotificationCompat.Action.Builder(0, getString(R.string.dialog_cancel), cancel).build())
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseCapture()
        latest?.recycle()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CAPTURE = "com.screenx.ai.action.CAPTURE"
        const val ACTION_CANCEL = "com.screenx.ai.action.CAPTURE_CANCEL"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_DELAY = "extra_delay"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context, resultCode: Int, data: Intent, delaySec: Int) {
            val intent = Intent(context, ScreenshotService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
                .putExtra(EXTRA_DELAY, delaySec)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

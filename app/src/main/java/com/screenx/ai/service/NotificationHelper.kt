package com.screenx.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.screenx.ai.MainActivity
import com.screenx.ai.R
import com.screenx.ai.domain.RecordingStatus
import com.screenx.ai.utils.TimeFormat

/** Which "floating controls" button, if any, the notification should offer. */
enum class ControlsToggle { NONE, SHOW, HIDE }

object NotificationHelper {
    const val CHANNEL_ID = "screenx_recording"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        status: RecordingStatus,
        elapsedMs: Long,
        controls: ControlsToggle = ControlsToggle.NONE,
        showPause: Boolean = true,
        countdown: Int = 0
    ): Notification {
        val paused = status == RecordingStatus.PAUSED
        val time = TimeFormat.elapsed(elapsedMs)
        val text = when {
            countdown > 0 -> context.getString(R.string.notif_text_countdown, countdown)
            paused -> context.getString(R.string.notif_text_paused, time)
            else -> context.getString(R.string.notif_text_recording, time)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (showPause && countdown == 0) {
            builder.addAction(
                if (paused) action(context, RecordingService.ACTION_RESUME, R.string.action_resume, 1)
                else action(context, RecordingService.ACTION_PAUSE, R.string.action_pause, 2)
            )
        }
        builder.addAction(action(context, RecordingService.ACTION_STOP, R.string.action_stop, 3))

        when (controls) {
            ControlsToggle.SHOW ->
                builder.addAction(action(context, RecordingService.ACTION_TOGGLE_CONTROLS, R.string.action_show_controls, 4))
            ControlsToggle.HIDE ->
                builder.addAction(action(context, RecordingService.ACTION_TOGGLE_CONTROLS, R.string.action_hide_controls, 4))
            ControlsToggle.NONE -> Unit
        }
        return builder.build()
    }

    private fun action(
        context: Context,
        action: String,
        @StringRes label: Int,
        requestCode: Int
    ): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            context, requestCode,
            Intent(context, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pi).build()
    }
}

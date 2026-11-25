package com.screenx.ai.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.screenx.ai.MainActivity
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
import com.screenx.ai.utils.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android requires the user to approve screen capture every time, so a recording cannot start
 * by itself. Instead, ScreenX shows a reminder at the chosen time; one tap starts the normal flow.
 */
object ScheduleManager {
    private const val REQUEST_CODE = 77

    fun schedule(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Inexact on purpose: exact alarms need a special permission that this feature does not justify.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ScheduleReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = LocaleHelper.wrap(context)
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val open = PendingIntent.getActivity(
            ctx, 78,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_QUICK_RECORD, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(ctx.getString(R.string.reminder_title))
            .setContentText(ctx.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification) // silently dropped if notifications are off

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as ScreenXApp).settingsRepository.setScheduledAt(0L)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val CHANNEL_ID = "screenx_reminder"
        const val NOTIFICATION_ID = 2001
    }
}

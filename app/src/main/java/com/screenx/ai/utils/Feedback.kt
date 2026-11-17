package com.screenx.ai.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    /** One short vibration. */
    fun tick(context: Context, millis: Long = 35L) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

object SoundEffects {
    enum class Kind { START, STOP }

    /** Plays a short beep. Only call this while NOT recording, so it never ends up in a video. */
    fun play(kind: Kind, volumePercent: Int) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volumePercent.coerceIn(0, 100))
            tone.startTone(
                if (kind == Kind.START) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK,
                180
            )
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
    }
}

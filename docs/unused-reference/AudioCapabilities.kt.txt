package com.screenx.ai.audio

import android.os.Build
import com.screenx.ai.domain.AudioSource

object AudioCapabilities {
    /** Playback capture (internal audio) exists only on Android 10+. */
    val supportsInternalAudio: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun availableSources(): List<AudioSource> =
        if (supportsInternalAudio) {
            listOf(
                AudioSource.NONE, AudioSource.MIC,
                AudioSource.INTERNAL, AudioSource.INTERNAL_AND_MIC
            )
        } else {
            listOf(AudioSource.NONE, AudioSource.MIC)
        }
}

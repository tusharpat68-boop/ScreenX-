package com.screenx.ai.camera

import android.content.Context
import android.content.pm.PackageManager
import com.screenx.ai.domain.CameraLens

object CameraCapabilities {
    fun has(context: Context, lens: CameraLens): Boolean {
        val pm = context.packageManager
        return when (lens) {
            CameraLens.FRONT -> pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
            CameraLens.BACK -> pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        }
    }

    fun available(context: Context): List<CameraLens> =
        CameraLens.values().filter { has(context, it) }
}

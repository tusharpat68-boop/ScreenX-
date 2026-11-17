package com.screenx.ai.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.screenx.ai.MainActivity
import com.screenx.ai.R
import com.screenx.ai.recording.RecordingSession

/**
 * Quick Settings tile. While recording it stops the recording. Otherwise it opens ScreenX and
 * starts the normal start flow (Android still asks for screen-capture permission).
 */
class RecordTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (RecordingSession.state.value.isActive) {
            RecordingService.send(this, RecordingService.ACTION_STOP)
            return
        }
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_QUICK_RECORD, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = RecordingSession.state.value.isActive
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(if (active) R.string.tile_active else R.string.tile_label)
        tile.updateTile()
    }
}

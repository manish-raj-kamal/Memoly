package com.memoly.dock.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.memoly.dock.ui.quickcapture.QuickCaptureActivity

/**
 * Quick Settings Tile — "New Memory"
 *
 * Users swipe down the notification shade, tap this tile,
 * and a minimal quick-note overlay opens instantly.
 *
 * Battery-efficient: does nothing in the background.
 * Only responds to user taps.
 */
class MemolyQSTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.label = "New Memory"
            tile.contentDescription = "Quickly save a new memory"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, QuickCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Android 14+ requires PendingIntent for startActivityAndCollapse
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

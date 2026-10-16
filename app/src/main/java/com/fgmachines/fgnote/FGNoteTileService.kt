package com.fgmachines.fgnote

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * User-initiated Quick Settings tile. Does not request accessibility services
 * or background display privileges. The system controls whether/where the tile
 * appears; users choose its order (e.g. next to Personal Hotspot).
 */
class FGNoteTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateAppearance(serviceRunning(this))
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggleFromUserTap() }
        else toggleFromUserTap()
    }

    private fun toggleFromUserTap() {
        val enabled = serviceRunning(this)
        if (enabled) {
            stopService(Intent(this, FloatingNoteService::class.java))
            updateAppearance(false)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            launchVisibleApp()
            return
        }
        try {
            startForegroundService(Intent(this, FloatingNoteService::class.java).apply {
                action = FloatingNoteService.ACTION_START
            })
            updateAppearance(true)
        } catch (error: RuntimeException) {
            Log.w("FGNoteTile", "Foreground launch denied, opening app", error)
            launchVisibleApp()
        }
    }

    private fun launchVisibleApp() {
        val activity = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_TILE_TOGGLE, true)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 413, activity,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(activity)
        }
    }

    private fun updateAppearance(active: Boolean) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.quick_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_tile)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        @Suppress("DEPRECATION")
        fun serviceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(100).any {
                it.service.className == FloatingNoteService::class.java.name
            }
        }

        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, FGNoteTileService::class.java))
            } catch (error: RuntimeException) {
                // User might not have installed the tile yet.
                Log.d("FGNoteTile", "Tile not available for refresh", error)
            }
        }
    }
}

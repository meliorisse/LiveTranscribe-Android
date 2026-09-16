package com.charles.livecaptionn.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.charles.livecaptionn.LiveCaptionApp
import com.charles.livecaptionn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-triggered shortcut; capture permissions still go through the foreground activity. */
class CaptionTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            (application as LiveCaptionApp).container.runtimeStore.state.collect { runtime ->
                qsTile?.apply {
                    state = if (runtime.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val running = (application as LiveCaptionApp).container.runtimeStore.state.value.running
            if (running) {
                startService(Intent(this, CaptionForegroundService::class.java).apply {
                    action = CaptionForegroundService.ACTION_STOP
                })
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_QUICK_START
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(PendingIntent.getActivity(
                        this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

package com.charles.livecaptionn.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.charles.livecaptionn.R
import com.charles.livecaptionn.service.CaptionTileService

fun addCaptionQuickSettingsTile(activity: Activity) {
    if (Build.VERSION.SDK_INT >= 33) {
        activity.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(activity, CaptionTileService::class.java),
            activity.getString(R.string.quick_caption_tile),
            Icon.createWithResource(activity, R.drawable.ic_quick_caption),
            activity.mainExecutor
        ) { result ->
            if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                Toast.makeText(activity, R.string.quick_caption_tile_help, Toast.LENGTH_LONG).show()
            }
        }
    } else {
        Toast.makeText(activity, R.string.quick_caption_tile_help, Toast.LENGTH_LONG).show()
    }
}

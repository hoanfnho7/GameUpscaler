package com.nho.upscaler

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class HudTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (FpsOverlayService.isServiceRunning) {
            stopService(Intent(this, FpsOverlayService::class.java))
            updateTileState(false)
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, Lang.str(this, R.string.toast_grant_overlay_long), Toast.LENGTH_LONG).show()
                return
            }
            val svc = Intent(this, FpsOverlayService::class.java).apply {
                putExtra("target_pkg", "")
                putExtra("game_name", "Control Center HUD")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
            updateTileState(true)
        }
    }

    private fun updateTileState(forceRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val active = forceRunning ?: FpsOverlayService.isServiceRunning
        tile.label = Lang.tileLabel(this)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
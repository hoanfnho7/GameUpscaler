package com.nho.upscaler

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class HudTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileUi(FpsOverlayService.isServiceRunning)
    }

    override fun onClick() {
        super.onClick()
        if (FpsOverlayService.isServiceRunning) {
            stopService(Intent(this, FpsOverlayService::class.java))
            syncTileUi(false)
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Hãy bật quyền 'Hiển thị trên ứng dụng khác' cho iQOO Upscaler!", Toast.LENGTH_LONG).show()
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    val pi = PendingIntent.getActivity(
                        this, 201, permIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(permIntent)
                }
                return
            }

            val svc = Intent(this, FpsOverlayService::class.java).apply {
                putExtra("target_pkg", "")
                putExtra("game_name", "Control Center HUD")
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
                syncTileUi(true)
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi bật HUD: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncTileUi(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "Đang bật" else "Chạm để mở"
        }
        tile.updateTile()
    }
}
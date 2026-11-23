package com.nho.upscaler.ui.hud

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nho.upscaler.FpsOverlayService
import com.nho.upscaler.PrefsManager
import com.nho.upscaler.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class HudLayoutPreset { HORIZONTAL, VERTICAL }

enum class PollingRatePreset(val label: String, val modeIdx: Int) {
    AUTO("Tự động", 0),
    FAST("150ms", 1),
    BALANCED("300ms", 2),
    BATTERY_SAVER("1s", 3)
}

data class HudSettingsUiState(
    val isHudActive: Boolean = false,
    val layoutPreset: HudLayoutPreset = HudLayoutPreset.HORIZONTAL,
    val scaleLevel: Float = 1.0f, // 0.7x to 1.5x
    val bgOpacity: Int = 85, // 0 to 100
    val colorAccentIdx: Int = 0, // 0: Lime, 1: White, 2: Cyan, 3: Amber
    val pollingRate: PollingRatePreset = PollingRatePreset.AUTO,
    val metricsMap: Map<String, Boolean> = mapOf(
        "m_fps" to true,
        "m_watt" to true,
        "m_gpu" to true,
        "m_vram" to false,
        "m_cpu_freq" to false,
        "m_bat_temp" to true,
        "m_soc_temp" to true,
        "m_cpu_temp" to false,
        "m_gpu_temp" to false,
        "m_ddr_temp" to false,
        "m_cam_temp" to false,
        "m_skin_temp" to false,
        "m_ram" to false
    ),
    val thermalSensors: List<Pair<String, Float>> = emptyList(),
    val isThermalDialogOpen: Boolean = false,
    val isQsTileDialogOpen: Boolean = false
)

class HudSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HudSettingsUiState())
    val uiState: StateFlow<HudSettingsUiState> = _uiState.asStateFlow()

    private var prefsMgr: PrefsManager? = null

    fun init(context: Context) {
        if (prefsMgr == null) {
            prefsMgr = PrefsManager(context.applicationContext)
            loadConfig()
        }
        observeServiceState()
    }

    private fun loadConfig() {
        val p = prefsMgr ?: return
        val layout = if (p.hudLayoutMode == 1 || p.overlayVertical) HudLayoutPreset.VERTICAL else HudLayoutPreset.HORIZONTAL
        val scaleVal = when (p.overlaySizeLevel) {
            0 -> 0.5f
            1 -> 0.7f
            2 -> 1.0f
            3 -> 1.25f
            else -> 1.5f
        }
        val opacityVal = when (p.overlayBgMode) {
            0 -> 100
            1 -> 85
            2 -> 40
            else -> 0
        }
        val poll = when (p.hudPollMode) {
            1 -> PollingRatePreset.FAST
            2 -> PollingRatePreset.BALANCED
            3 -> PollingRatePreset.BATTERY_SAVER
            else -> PollingRatePreset.AUTO
        }

        val map = mutableMapOf<String, Boolean>()
        listOf(
            "m_fps", "m_watt", "m_gpu", "m_vram", "m_cpu_freq",
            "m_bat_temp", "m_soc_temp", "m_cpu_temp", "m_gpu_temp",
            "m_ddr_temp", "m_cam_temp", "m_skin_temp", "m_ram"
        ).forEach { key ->
            map[key] = p.getMetric(key, key in listOf("m_fps", "m_watt", "m_gpu", "m_bat_temp", "m_soc_temp"))
        }

        _uiState.update {
            it.copy(
                isHudActive = FpsOverlayService.isServiceRunning,
                layoutPreset = layout,
                scaleLevel = scaleVal,
                bgOpacity = opacityVal,
                colorAccentIdx = p.hudColorAccent,
                pollingRate = poll,
                metricsMap = map
            )
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            while (isActive) {
                val running = FpsOverlayService.isServiceRunning
                if (_uiState.value.isHudActive != running) {
                    _uiState.update { it.copy(isHudActive = running) }
                }
                delay(1000)
            }
        }
    }

    fun toggleActualHud(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Vui lòng cấp quyền Vẽ trên ứng dụng khác", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        if (!FpsOverlayService.isServiceRunning) {
            val intent = Intent(context, FpsOverlayService::class.java).apply {
                putExtra("target_pkg", "")
                putExtra("game_name", "HUD Preview")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _uiState.update { it.copy(isHudActive = true) }
        } else {
            context.stopService(Intent(context, FpsOverlayService::class.java))
            _uiState.update { it.copy(isHudActive = false) }
        }
    }

    fun setLayoutPreset(preset: HudLayoutPreset, context: Context) {
        val modeIdx = if (preset == HudLayoutPreset.VERTICAL) 1 else 0
        prefsMgr?.hudLayoutMode = modeIdx
        prefsMgr?.overlayVertical = (preset == HudLayoutPreset.VERTICAL)
        _uiState.update { it.copy(layoutPreset = preset) }
        notifyStyleChanged(context)
    }

    fun setScale(scale: Float, context: Context) {
        val level = when {
            scale <= 0.6f -> 0
            scale <= 0.85f -> 1
            scale <= 1.15f -> 2
            scale <= 1.35f -> 3
            else -> 4
        }
        prefsMgr?.overlaySizeLevel = level
        _uiState.update { it.copy(scaleLevel = scale) }
        notifyStyleChanged(context)
    }

    fun setOpacity(opacity: Int, context: Context) {
        val mode = when {
            opacity <= 20 -> 3
            opacity <= 55 -> 2
            opacity <= 90 -> 1
            else -> 0
        }
        prefsMgr?.overlayBgMode = mode
        _uiState.update { it.copy(bgOpacity = opacity) }
        notifyStyleChanged(context)
    }

    fun setColorAccent(idx: Int, context: Context) {
        prefsMgr?.hudColorAccent = idx
        _uiState.update { it.copy(colorAccentIdx = idx) }
        notifyStyleChanged(context)
    }

    fun setPollingRate(preset: PollingRatePreset, context: Context) {
        prefsMgr?.hudPollMode = preset.modeIdx
        _uiState.update { it.copy(pollingRate = preset) }
        notifyStyleChanged(context)
    }

    fun toggleMetric(key: String, context: Context) {
        val current = _uiState.value.metricsMap[key] ?: false
        val next = !current
        prefsMgr?.setMetric(key, next)
        _uiState.update {
            val updated = it.metricsMap.toMutableMap()
            updated[key] = next
            it.copy(metricsMap = updated)
        }
        notifyStyleChanged(context)
    }

    fun resetHudCoordinates(context: Context) {
        prefsMgr?.resetHudPosition()
        if (FpsOverlayService.isServiceRunning) {
            context.startService(Intent(context, FpsOverlayService::class.java).apply {
                action = "RESET_POS"
            })
            Toast.makeText(context, "Đã khôi phục vị trí HUD về mặc định", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Đã đặt lại tọa độ HUD về mặc định", Toast.LENGTH_SHORT).show()
        }
    }

    fun openThermalDialog(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sensors = readThermalSensors(context)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        thermalSensors = sensors,
                        isThermalDialogOpen = true
                    )
                }
            }
        }
    }

    fun closeThermalDialog() {
        _uiState.update { it.copy(isThermalDialogOpen = false) }
    }

    fun openQsTileDialog() {
        _uiState.update { it.copy(isQsTileDialogOpen = true) }
    }

    fun closeQsTileDialog() {
        _uiState.update { it.copy(isQsTileDialogOpen = false) }
    }

    private fun notifyStyleChanged(context: Context) {
        if (FpsOverlayService.isServiceRunning) {
            context.startService(Intent(context, FpsOverlayService::class.java).apply {
                action = "STYLE_CHANGED"
            })
        }
    }

    private fun readThermalSensors(context: Context): List<Pair<String, Float>> {
        val list = mutableListOf<Pair<String, Float>>()
        val seen = mutableSetOf<String>()

        // 1. Shizuku shell thermal zones query
        if (ShizukuHelper.isBinderAlive()) {
            try {
                val cmd = "for z in /sys/class/thermal/thermal_zone*; do [ -r \"\$z/temp\" ] && [ -r \"\$z/type\" ] && echo \"\$(cat \$z/type 2>/dev/null): \$(cat \$z/temp 2>/dev/null)\"; done"
                val out = ShizukuHelper.runSync(cmd)
                for (line in out.splitlines()) {
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val raw = parts[1].trim().toFloatOrNull() ?: continue
                        val deg = if (raw > 1000f) raw / 1000f else if (raw > 150f) raw / 10f else raw
                        if (deg in 15f..125f && name.isNotEmpty() && seen.add(name)) {
                            list.add(Pair(name, deg))
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Fallback via dumpsys thermalservice
            if (list.isEmpty()) {
                try {
                    val dump = ShizukuHelper.runSync("dumpsys thermalservice")
                    val regex = Regex("""Temperature\{mValue=([0-9.]+)[^}]*mName=([^,}]+)""")
                    for (m in regex.findAll(dump)) {
                        val deg = m.groupValues[1].toFloatOrNull() ?: continue
                        val name = m.groupValues[2].trim()
                        if (deg in 15f..125f && name.isNotEmpty() && seen.add(name)) {
                            list.add(Pair(name, deg))
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Direct filesystem read
        if (list.isEmpty()) {
            for (i in 0..60) {
                try {
                    val typeFile = File("/sys/class/thermal/thermal_zone$i/type")
                    val tempFile = File("/sys/class/thermal/thermal_zone$i/temp")
                    if (typeFile.exists() && tempFile.exists()) {
                        val name = typeFile.readText().trim()
                        val raw = tempFile.readText().trim().toFloatOrNull() ?: continue
                        val deg = if (raw > 1000f) raw / 1000f else if (raw > 150f) raw / 10f else raw
                        if (deg in 15f..125f && name.isNotEmpty() && seen.add(name)) {
                            list.add(Pair(name, deg))
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 4. Battery sensor fallback from BatteryManager
        try {
            val bmIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawBat = bmIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (rawBat > 0) {
                val batDeg = rawBat / 10f
                if (seen.add("battery_internal")) {
                    list.add(0, Pair("battery_internal", batDeg))
                }
            }
        } catch (_: Exception) {}

        return list.sortedByDescending { it.second }
    }
}

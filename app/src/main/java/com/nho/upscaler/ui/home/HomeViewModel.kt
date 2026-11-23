package com.nho.upscaler.ui.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nho.upscaler.PerfLogManager
import com.nho.upscaler.PrefsManager
import com.nho.upscaler.ShizukuHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class HomeUiState(
    val isDaemonRunning: Boolean = true,
    val isShizukuReady: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isBatteryOptIgnored: Boolean = false,
    val isDarkTheme: Boolean = true,
    val currentLang: String = "VI",
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val ramUsedPct: Int = 0,
    val freeUfsGb: Int = 0,
    val batteryPct: Int = 80,
    val batteryTemp: Float = 36.0f,
    val isCharging: Boolean = false,
    val batteryStatus: String = "BÌNH THƯỜNG",
    val socName: String = "Snapdragon 8 Elite",
    val refreshRate: Int = 120,
    val nativeW: Int = 1080,
    val nativeH: Int = 2400,
    val nativeDen: Int = 420,
    val targetW: Int = 1242,
    val targetH: Int = 2760,
    val targetDen: Int = 483,
    val sessionCount: Int = 0
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var prefsMgr: PrefsManager? = null

    fun init(context: Context) {
        if (prefsMgr == null) {
            prefsMgr = PrefsManager(context.applicationContext)
            _uiState.update {
                it.copy(
                    isDarkTheme = (prefsMgr?.themeMode ?: 0) != 2,
                    currentLang = if ((prefsMgr?.appLang ?: 1) == 2) "EN" else "VI"
                )
            }
        }
        startPolling(context.applicationContext)
    }

    private fun startPolling(context: Context) {
        viewModelScope.launch {
            while (isActive) {
                updateTelemetry(context)
                delay(1000)
            }
        }
    }

    fun updateTelemetry(context: Context) {
        // Shizuku
        val shizukuReady = ShizukuHelper.isBinderAlive()

        // Permissions
        val overlayGranted = Settings.canDrawOverlays(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true

        // RAM Info
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalRam = mi.totalMem.toFloat() / (1024 * 1024 * 1024)
        val availRam = mi.availMem.toFloat() / (1024 * 1024 * 1024)
        val usedRam = totalRam - availRam
        val ramPct = if (totalRam > 0) ((usedRam / totalRam) * 100).toInt() else 0

        // UFS Storage
        val freeUfs = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            ((stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)).toInt()
        } catch (_: Exception) {
            128
        }

        // Battery
        val batFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batIntent = context.registerReceiver(null, batFilter)
        val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 80
        val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
        val tempRaw = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 360
        val batTemp = if (tempRaw > 0) tempRaw / 10f else 36f
        val status = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        val batStatus = if (isCharging) "ĐANG SẠC" else if (batTemp >= 43f) "QUÁ NHIỆT" else "BÌNH THƯỜNG"

        // Display
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rr = try {
            val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay
            }
            d?.mode?.refreshRate?.toInt() ?: 120
        } catch (_: Exception) {
            120
        }

        // Scale Factor
        val factor = ((prefsMgr?.targetScaleFactor ?: 115) / 100f)
        val curW = _uiState.value.nativeW
        val curH = _uiState.value.nativeH
        val curDen = _uiState.value.nativeDen

        val targetW = (curW * factor).toInt()
        val targetH = (curH * factor).toInt()
        val targetDen = (curDen * factor).toInt()

        // Sessions Count
        val sessions = PerfLogManager.listSessions(context)

        _uiState.update {
            it.copy(
                isShizukuReady = shizukuReady,
                isOverlayGranted = overlayGranted,
                isBatteryOptIgnored = batteryIgnored,
                ramUsedGb = usedRam,
                ramTotalGb = totalRam,
                ramUsedPct = ramPct,
                freeUfsGb = freeUfs,
                batteryPct = batPct,
                batteryTemp = batTemp,
                isCharging = isCharging,
                batteryStatus = batStatus,
                socName = getDeviceSocName(),
                refreshRate = rr,
                targetW = targetW,
                targetH = targetH,
                targetDen = targetDen,
                sessionCount = sessions.size
            )
        }
    }

    fun toggleTheme() {
        val current = _uiState.value.isDarkTheme
        val next = !current
        _uiState.update { it.copy(isDarkTheme = next) }
        prefsMgr?.themeMode = if (next) 1 else 2
    }

    fun toggleLanguage() {
        val current = _uiState.value.currentLang
        val next = if (current == "VI") "EN" else "VI"
        _uiState.update { it.copy(currentLang = next) }
        prefsMgr?.appLang = if (next == "VI") 1 else 2
    }

    fun resetNativeResolution(context: Context) {
        ShizukuHelper.resetDisplay()
    }

    fun requestShizukuPermission(context: Context) {
        if (!ShizukuHelper.ensureReady(context)) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {}
        }
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun getDeviceSocName(): String {
        return try {
            val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
            val board = Build.BOARD.lowercase(Locale.ROOT)
            val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.lowercase(Locale.ROOT)
            } else ""
            val roSoc = getSystemProp("ro.soc.model").lowercase(Locale.ROOT)
            val roPlatform = getSystemProp("ro.board.platform").lowercase(Locale.ROOT)
            val roChip = getSystemProp("ro.chipname").lowercase(Locale.ROOT)

            val combined = "$hardware $board $socModel $roSoc $roPlatform $roChip"

            when {
                combined.contains("sun") || combined.contains("8750") || combined.contains("elite") -> "Snapdragon 8 Elite"
                combined.contains("pineapple") || combined.contains("8650") -> "Snapdragon 8 Gen 3"
                combined.contains("kalama") || combined.contains("8550") -> "Snapdragon 8 Gen 2"
                combined.contains("taro") || combined.contains("8450") || combined.contains("8475") -> "Snapdragon 8 Gen 1"
                combined.contains("lahaina") || combined.contains("8350") -> "Snapdragon 888"
                combined.contains("kona") || combined.contains("8250") -> "Snapdragon 865"
                combined.contains("qcom") || combined.contains("qualcomm") || combined.contains("snapdragon") -> "Snapdragon Platform"
                combined.contains("mt") || combined.contains("dimensity") -> "MediaTek Dimensity"
                combined.contains("exynos") -> "Samsung Exynos"
                combined.contains("tensor") -> "Google Tensor"
                else -> "${Build.MANUFACTURER} SoC"
            }
        } catch (_: Exception) {
            "Snapdragon 8 Elite"
        }
    }

    private fun getSystemProp(key: String): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            (get.invoke(c, key) as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

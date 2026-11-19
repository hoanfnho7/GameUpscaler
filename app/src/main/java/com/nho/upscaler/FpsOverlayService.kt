package com.nho.upscaler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.hypot

class FpsOverlayService : Service() {
    companion object {
        @Volatile
        var isServiceRunning = false

        private val WS_REGEX = Regex("\\s+")
        private val NUM_REGEX = Regex("([0-9]+)")
        private val FPS_REGEX = Regex("fps:\\s*([0-9.]+)")
    }

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var closeTargetView: TextView? = null
    private lateinit var prefs: SharedPreferences
    private var batteryManager: BatteryManager? = null

    @Volatile private var isRunning = false
    @Volatile private var isScreenOn = true
    @Volatile private var telemetryThread: Thread? = null

    private var targetPkg = ""
    private var gameName = "Game"
    private var scaleLabel = ""
    private var sizeLevel = 1
    private var pollMode = 0
    private var isVertical = false
    private var isOverCloseZone = false

    private val shellLock = Any()
    @Volatile private var shellProcess: Process? = null
    @Volatile private var shellWriter: BufferedWriter? = null
    @Volatile private var shellReader: BufferedReader? = null

    private var lastFps = -1f
    @Volatile private var cachedBatTemp = 0f
    @Volatile private var cachedVoltMv = 0
    @Volatile private var isChargingNow = false

    private var lastSocTemp = 0f
    private var lastCpuTemp = 0f
    private var lastGpuTemp = 0f
    private var lastDdrTemp = 0f
    private var lastCamTemp = 0f
    private var lastSkinTemp = 0f
    private var lastWatts = 0f
    private var lastGpuVramMb = -1
    private var lastCpuKhz = 0L
    private var lastRamText = ""

    private var lastRenderedText = ""
    private var lastRenderedColor = 0

    private var prevLayerFrames = HashMap<String, Long>(16)
    private var prevGlobalFrames = -1L
    private var lastFpsSampleNs = 0L
    private var emptyFpsTicks = 0
    private var hasValidFpsSample = false
    private var initializedStatsForPkg: String? = null
    private var drmFpsAvailable = false
    private var tickCounter = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    telemetryThread?.interrupt()
                }
                Intent.ACTION_BATTERY_CHANGED -> updateBatteryFromIntent(intent)
            }
        }
    }

    private fun updateBatteryFromIntent(intent: Intent?) {
        if (intent == null) return
        val v = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        if (v > 0) cachedVoltMv = v
        val t = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        if (t > 0) cachedBatTemp = t / 10f
        val st = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        isChargingNow = (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val stickyBat = registerReceiver(systemReceiver, filter)
        updateBatteryFromIntent(stickyBat)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            prevLayerFrames.clear()
        }
    }

    private fun syncQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(this, ComponentName(this, HudTileService::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun isLandscapeMode(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun applyPositionForCurrentOrientation() {
        val params = overlayParams ?: return
        val isLand = isLandscapeMode()
        val dm = resources.displayMetrics

        val rawX = if (isLand) prefs.getInt("overlay_land_x", 160) else prefs.getInt("overlay_port_x", 60)
        val rawY = if (isLand) prefs.getInt("overlay_land_y", 36) else prefs.getInt("overlay_port_y", 120)

        val maxX = (dm.widthPixels - 120).coerceAtLeast(20)
        val maxY = (dm.heightPixels - 140).coerceAtLeast(20)
        params.x = rawX.coerceIn(10, maxX)
        params.y = rawY.coerceIn(10, maxY)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayView != null && overlayParams != null) {
            applyPositionForCurrentOrientation()
            try {
                windowManager?.updateViewLayout(overlayView, overlayParams)
            } catch (_: Exception) {}
        }
        telemetryThread?.interrupt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)
        sizeLevel = prefs.getInt("overlay_size_level", 1)
        pollMode = prefs.getInt("hud_poll_mode", 0)
        isVertical = prefs.getBoolean("overlay_vertical", false)

        when (intent?.action) {
            "STOP_OVERLAY" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "RESET_POS" -> {
                if (overlayView != null && overlayParams != null) {
                    applyPositionForCurrentOrientation()
                    try {
                        windowManager?.updateViewLayout(overlayView, overlayParams)
                    } catch (_: Exception) {}
                }
                return START_NOT_STICKY
            }
            "UPDATE_STYLE" -> {
                if (overlayView != null) {
                    lastRenderedText = ""
                    lastRenderedColor = 0
                    startAsForeground()
                    applyOverlayStyle()
                    renderTelemetryText()
                    syncQuickSettingsTile()
                    telemetryThread?.interrupt()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        targetPkg = intent?.getStringExtra("target_pkg") ?: ""
        gameName = intent?.getStringExtra("game_name") ?: "Control Center HUD"
        scaleLabel = intent?.getStringExtra("scale_label") ?: ""

        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, Lang.str(this, R.string.toast_missing_overlay), Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayView == null) {
            createOverlayViews()
        } else {
            lastRenderedText = ""
            applyOverlayStyle()
            applyPositionForCurrentOrientation()
            try {
                windowManager?.updateViewLayout(overlayView, overlayParams)
            } catch (_: Exception) {}
            renderTelemetryText()
        }

        isServiceRunning = true
        syncQuickSettingsTile()

        if (!isRunning) {
            isRunning = true
            startTelemetryLoop()
        } else {
            telemetryThread?.interrupt()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "upscaler_hud_silent_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                Lang.str(this, R.string.notif_channel_hud),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val isUpscaleActive = scaleLabel.isNotEmpty()
        val pi = if (isUpscaleActive) {
            val resetIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("action_type", "RESET_NOW")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                this, 99, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            val stopHudIntent = Intent(this, FpsOverlayService::class.java).apply {
                action = "STOP_OVERLAY"
            }
            PendingIntent.getService(
                this, 100, stopHudIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val titleText = if (isUpscaleActive) {
            Lang.str(this, R.string.notif_upscale_title, scaleLabel, gameName)
        } else {
            Lang.str(this, R.string.notif_hud_title)
        }

        val contentText = if (isUpscaleActive) {
            Lang.str(this, R.string.notif_upscale_hud_desc)
        } else {
            Lang.str(this, R.string.notif_hud_desc)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun createOverlayViews() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val closeTv = TextView(this).apply {
                text = "✕"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                visibility = View.GONE
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC261212"))
                    setStroke(3, Color.parseColor("#FF5252"))
                }
            }
            closeTargetView = closeTv

            val closeParams = WindowManager.LayoutParams(
                136,
                136,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 90
            }
            windowManager?.addView(closeTv, closeParams)

            val tv = TextView(this).apply {
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#00E676"))
            }
            overlayView = tv
            applyOverlayStyle()
            renderTelemetryText()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            overlayParams = params
            applyPositionForCurrentOrientation()

            tv.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            isOverCloseZone = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (abs(dx) > 10 || abs(dy) > 10) {
                                if (!isDragging) {
                                    isDragging = true
                                    closeTargetView?.visibility = View.VISIBLE
                                }
                                params.x = initialX + dx
                                params.y = initialY + dy
                                windowManager?.updateViewLayout(overlayView, params)

                                val dm = resources.displayMetrics
                                val targetCenterX = dm.widthPixels / 2f
                                val targetCenterY = dm.heightPixels - 90f - 68f
                                val dist = hypot(event.rawX - targetCenterX, event.rawY - targetCenterY)
                                val insideZone = dist < 145f

                                if (insideZone != isOverCloseZone) {
                                    isOverCloseZone = insideZone
                                    updateCloseTargetVisual(insideZone)
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            closeTargetView?.visibility = View.GONE
                            if (isDragging) {
                                if (isOverCloseZone) {
                                    Toast.makeText(this@FpsOverlayService, Lang.str(this@FpsOverlayService, R.string.toast_hud_closed), Toast.LENGTH_SHORT).show()
                                    stopSelf()
                                    return true
                                }
                                if (isLandscapeMode()) {
                                    prefs.edit().putInt("overlay_land_x", params.x).putInt("overlay_land_y", params.y).apply()
                                } else {
                                    prefs.edit().putInt("overlay_port_x", params.x).putInt("overlay_port_y", params.y).apply()
                                }
                            } else {
                                sizeLevel = (sizeLevel + 1) % 4
                                prefs.edit().putInt("overlay_size_level", sizeLevel).apply()
                                lastRenderedText = ""
                                applyOverlayStyle()
                                renderTelemetryText()
                            }
                            isOverCloseZone = false
                            updateCloseTargetVisual(false)
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(tv, params)
        } catch (e: Exception) {
            overlayView = null
            Toast.makeText(this, Lang.str(this, R.string.toast_overlay_error, e.message ?: ""), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun updateCloseTargetVisual(active: Boolean) {
        val cView = closeTargetView ?: return
        if (active) {
            cView.scaleX = 1.18f
            cView.scaleY = 1.18f
            cView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF1744"))
                setStroke(4, Color.WHITE)
            }
        } else {
            cView.scaleX = 1.0f
            cView.scaleY = 1.0f
            cView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC261212"))
                setStroke(3, Color.parseColor("#FF5252"))
            }
        }
    }

    private fun applyOverlayStyle() {
        val tv = overlayView ?: return
        tv.gravity = if (isVertical) Gravity.START else Gravity.CENTER_VERTICAL
        if (isVertical) tv.setLineSpacing(4f, 1.08f) else tv.setLineSpacing(0f, 1.0f)

        when (sizeLevel) {
            0 -> {
                tv.textSize = 9.5f
                tv.setPadding(14, 8, 14, 8)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#88101216"))
                    cornerRadius = 14f
                    setStroke(1, Color.parseColor("#4400E676"))
                }
            }
            1 -> {
                tv.textSize = 11f
                tv.setPadding(18, 10, 18, 10)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B3101216"))
                    cornerRadius = 18f
                    setStroke(1, Color.parseColor("#5500E676"))
                }
            }
            2 -> {
                tv.textSize = 12.5f
                tv.setPadding(22, 12, 22, 12)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC101216"))
                    cornerRadius = 22f
                    setStroke(2, Color.parseColor("#6600E676"))
                }
            }
            else -> {
                tv.textSize = 14f
                tv.setPadding(26, 14, 26, 14)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DD101216"))
                    cornerRadius = 26f
                    setStroke(2, Color.parseColor("#7700E676"))
                }
            }
        }
    }

    private fun renderTelemetryText() {
        val tv = overlayView ?: return

        val showFps = prefs.getBoolean("m_fps", true)
        val showWatt = prefs.getBoolean("m_watt", true)
        val showGpu = prefs.getBoolean("m_gpu", true)
        val showCpuFreq = prefs.getBoolean("m_cpu_freq", false)

        val showBatTemp = prefs.getBoolean("m_bat_temp", true)
        val showSocTemp = prefs.getBoolean("m_soc_temp", true)
        val showCpuTemp = prefs.getBoolean("m_cpu_temp", false)
        val showGpuTemp = prefs.getBoolean("m_gpu_temp", false)

        val showDdrTemp = prefs.getBoolean("m_ddr_temp", false)
        val showCamTemp = prefs.getBoolean("m_cam_temp", false)
        val showSkinTemp = prefs.getBoolean("m_skin_temp", false)
        val showRam = prefs.getBoolean("m_ram", false)

        val parts = ArrayList<String>(8)
        val isCompact = (sizeLevel == 0)

        if (showFps) {
            val fpsVal = if (lastFps >= 0f) String.format(Locale.US, "%.1f", lastFps) else "--.-"
            parts.add(if (isCompact) "${fpsVal}F" else if (isVertical) "⚡ FPS: $fpsVal" else "⚡$fpsVal FPS")
        }
        if (showWatt) {
            val prefix = if (isChargingNow) "⚡+" else ""
            val wVal = if (lastWatts > 0f) String.format(Locale.US, "%s%.1fW", prefix, lastWatts) else "--W"
            parts.add(if (isCompact) wVal else if (isVertical) Lang.str(this, R.string.hud_vert_power, wVal) else "🔌$wVal")
        }
        if (showBatTemp) {
            val tVal = if (cachedBatTemp > 0f) String.format(Locale.US, "%.1f°", cachedBatTemp) else "--°"
            parts.add(if (isCompact) "🔋$tVal" else if (isVertical) Lang.str(this, R.string.hud_vert_bat, tVal) else "🔋${tVal}C")
        }
        if (showSocTemp) {
            val stVal = if (lastSocTemp > 0f) String.format(Locale.US, "%.1f°", lastSocTemp) else "--°"
            parts.add(if (isCompact) "🌡️$stVal" else if (isVertical) "🌡️ SoC: ${stVal}C" else "🌡️${stVal}C")
        }
        if (showCpuTemp) {
            val ctVal = if (lastCpuTemp > 0f) String.format(Locale.US, "%.1f°", lastCpuTemp) else "--°"
            parts.add(if (isCompact) "🔥$ctVal" else if (isVertical) "🔥 CPU: ${ctVal}C" else "🔥${ctVal}C")
        }
        if (showGpuTemp) {
            val gtVal = if (lastGpuTemp > 0f) String.format(Locale.US, "%.1f°", lastGpuTemp) else "--°"
            parts.add(if (isCompact) "📟$gtVal" else if (isVertical) "📟 GPU: ${gtVal}C" else "📟${gtVal}C")
        }
        if (showDdrTemp) {
            val dtVal = if (lastDdrTemp > 0f) String.format(Locale.US, "%.1f°", lastDdrTemp) else "--°"
            parts.add(if (isCompact) "🧬$dtVal" else if (isVertical) "🧬 DDR: ${dtVal}C" else "🧬${dtVal}C")
        }
        if (showCamTemp) {
            val camVal = if (lastCamTemp > 0f) String.format(Locale.US, "%.1f°", lastCamTemp) else "--°"
            parts.add(if (isCompact) "📷$camVal" else if (isVertical) "📷 CAM: ${camVal}C" else "📷${camVal}C")
        }
        if (showSkinTemp) {
            val skVal = if (lastSkinTemp > 0f) String.format(Locale.US, "%.1f°", lastSkinTemp) else "--°"
            parts.add(if (isCompact) "📱$skVal" else if (isVertical) Lang.str(this, R.string.hud_vert_skin, skVal) else "📱${skVal}C")
        }
        if (showGpu) {
            val vramStr = if (lastGpuVramMb >= 0) "${lastGpuVramMb}MB" else "--MB"
            parts.add(if (isCompact) "V:${if (lastGpuVramMb >= 0) "${lastGpuVramMb}M" else "--"}" else if (isVertical) "🎮 VRAM: $vramStr" else "🎮$vramStr")
        }
        if (showCpuFreq) {
            val cStr = when {
                lastCpuKhz >= 1_000_000L -> String.format(Locale.US, "%.2fGHz", lastCpuKhz / 1_000_000.0)
                lastCpuKhz > 0L -> "${lastCpuKhz / 1000L}MHz"
                else -> "--GHz"
            }
            parts.add(if (isCompact) cStr else if (isVertical) "⚙️ CPU: $cStr" else "⚙️$cStr")
        }
        if (showRam) {
            val rStr = lastRamText.ifEmpty { "--GB" }
            parts.add(if (isCompact) rStr else if (isVertical) "💾 RAM: $rStr" else "💾$rStr")
        }

        if (parts.isEmpty()) {
            parts.add(Lang.str(this, R.string.hud_no_metrics))
        }

        val newText = if (isVertical) {
            parts.joinToString("\n")
        } else {
            parts.joinToString(if (isCompact) " · " else " | ")
        }

        if (newText != lastRenderedText) {
            lastRenderedText = newText
            tv.text = newText
        }

        val maxChipTemp = maxOf(lastCpuTemp, lastGpuTemp, lastSocTemp)
        val color = when {
            cachedBatTemp >= 43.0f || maxChipTemp >= 85.0f -> Color.parseColor("#FF5252")
            cachedBatTemp >= 40.0f || maxChipTemp >= 75.0f -> Color.parseColor("#FFD740")
            else -> Color.parseColor("#00E676")
        }
        if (color != lastRenderedColor) {
            lastRenderedColor = color
            tv.setTextColor(color)
        }
    }

    private fun readInstantWattage() {
        try {
            val bm = batteryManager ?: return
            val rawCurr = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val voltMv = cachedVoltMv
            if (rawCurr != Int.MIN_VALUE && rawCurr != 0 && voltMv > 0) {
                val absCurr = abs(rawCurr.toDouble())
                val amps = if (absCurr > 15_000.0) absCurr / 1_000_000.0 else absCurr / 1_000.0
                val volts = voltMv / 1_000.0
                val instWatts = (amps * volts).toFloat()
                if (instWatts in 0.1f..180f) {
                    lastWatts = instWatts
                }
            }
        } catch (_: Exception) {}
    }

    private fun isProcessAlive(proc: Process?): Boolean {
        if (proc == null) return false
        return try {
            proc.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun ensureShellSession(): Boolean {
        try {
            if (isProcessAlive(shellProcess) && shellWriter != null && shellReader != null) {
                return true
            }
            closeShellSession()
            if (!Shizuku.pingBinder()) return false

            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val proc = m.invoke(null, arrayOf("sh"), null, null) as Process
            val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8), 2048)
            val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8), 4096)

            val initCmd = "_rdz() { for f in \$1; do read -r v < \"\$f\" 2>/dev/null && printf \"%s \" \"\$v\"; done; printf \"\\n\"; }; " +
                    "CPU_ZONES=\"\"; GPU_ZONES=\"\"; DDR_ZONES=\"\"; CAM_ZONES=\"\"; SKIN_ZONES=\"\"; " +
                    "for z in /sys/class/thermal/thermal_zone*; do " +
                    "[ -r \"\$z/temp\" ] || continue; " +
                    "read -r t < \"\$z/type\" 2>/dev/null || continue; " +
                    "t=\$(printf \"%s\" \"\$t\" | tr 'A-Z' 'a-z'); " +
                    "case \"\$t\" in " +
                    "*gpu*) GPU_ZONES=\"\$GPU_ZONES \$z/temp\" ;; " +
                    "*ddr*|*llcc*|*dram*|*lpddr*) DDR_ZONES=\"\$DDR_ZONES \$z/temp\" ;; " +
                    "*cam*|*ois*|*actuator*|*tof*) CAM_ZONES=\"\$CAM_ZONES \$z/temp\" ;; " +
                    "*skin*|*quiet*|*xo-therm*|*sys-therm*|*board*|*msm-therm*|*shell*|*sub-therm*|*main-therm*|*vivo*) SKIN_ZONES=\"\$SKIN_ZONES \$z/temp\" ;; " +
                    "*npu*|*nspss*|*modem*|*pa*|*bat*|*wlan*|*usb*|*flash*|*chg*) ;; " +
                    "*cpu*|*cpuss*|*apc*|*tsens*|*ap*|*soc*) CPU_ZONES=\"\$CPU_ZONES \$z/temp\" ;; " +
                    "esac; done; " +
                    "FREQ_FILES=\"\"; for f in /sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq; do [ -r \"\$f\" ] && FREQ_FILES=\"\$FREQ_FILES \$f\"; done; " +
                    "DRM_NODE=\"\"; for f in /sys/class/drm/sde-crtc-0/measured_fps /sys/class/drm/card0/sde-crtc-0/measured_fps; do if [ -r \"\$f\" ]; then DRM_NODE=\"\$f\"; break; fi; done\n"
            writer.write(initCmd)
            writer.flush()

            shellProcess = proc
            shellWriter = writer
            shellReader = reader
            return true
        } catch (_: Exception) {
            closeShellSession()
            return false
        }
    }

    private fun closeShellSession() {
        val proc = shellProcess
        val w = shellWriter
        val r = shellReader
        shellProcess = null
        shellWriter = null
        shellReader = null
        try { proc?.destroy() } catch (_: Exception) {}
        try { w?.close() } catch (_: Exception) {}
        try { r?.close() } catch (_: Exception) {}
    }

    private fun execPersistentShizuku(cmd: String): String {
        synchronized(shellLock) {
            if (!ensureShellSession()) return ""
            return try {
                val writer = shellWriter ?: return ""
                val reader = shellReader ?: return ""
                val marker = "___HUD_EOF_${System.nanoTime()}___"

                writer.write("$cmd\nprintf \"$marker\\n\"\n")
                writer.flush()

                val out = StringBuilder(512)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == marker) break
                    out.append(line).append('\n')
                }
                out.toString()
            } catch (_: Exception) {
                closeShellSession()
                ""
            }
        }
    }

    private fun computeNextIntervalMs(): Long {
        return when (pollMode) {
            1 -> 300L
            2 -> 1000L
            3 -> 2000L
            else -> if (targetPkg.isNotEmpty() || isLandscapeMode()) 450L else 1000L
        }
    }

    private fun startTelemetryLoop() {
        telemetryThread = thread(name = "HudTelemetryLoop") {
            var graceTicks = 0
            val sb = StringBuilder(384)

            while (isRunning) {
                if (!isScreenOn) {
                    try { Thread.sleep(1500) } catch (_: InterruptedException) {}
                    continue
                }

                try {
                    tickCounter++
                    val currentPkg = targetPkg
                    val showFps = prefs.getBoolean("m_fps", true)
                    val showSocTemp = prefs.getBoolean("m_soc_temp", true)
                    val showCpuTemp = prefs.getBoolean("m_cpu_temp", false)
                    val showGpuTemp = prefs.getBoolean("m_gpu_temp", false)
                    val showDdrTemp = prefs.getBoolean("m_ddr_temp", false)
                    val showCamTemp = prefs.getBoolean("m_cam_temp", false)
                    val showSkinTemp = prefs.getBoolean("m_skin_temp", false)
                    val showWatt = prefs.getBoolean("m_watt", true)
                    val showGpu = prefs.getBoolean("m_gpu", true)
                    val showCpuFreq = prefs.getBoolean("m_cpu_freq", false)
                    val showRam = prefs.getBoolean("m_ram", false)

                    if (showWatt) {
                        readInstantWattage()
                    }

                    sb.setLength(0)
                    if (currentPkg.isNotEmpty() && tickCounter % 3 == 1) {
                        sb.append("printf \"PID=\"; pidof ").append(currentPkg).append("; ")
                    }
                    // Giãn nhịp dumpsys gpu lên ~3.5s/lần để triệt tiêu hoàn toàn các đỉnh 10% CPU
                    if (showGpu && (tickCounter % 9 == 1 || lastGpuVramMb < 0)) {
                        sb.append("dumpsys gpu | head -n 45; ")
                    }
                    if (showCpuFreq) {
                        sb.append("printf \"CPU_KHZ=\"; _rdz \"\$FREQ_FILES\"; ")
                    }
                    if (showCpuTemp || showSocTemp) {
                        sb.append("printf \"CPU_T=\"; _rdz \"\$CPU_ZONES\"; ")
                    }
                    if (showGpuTemp || showSocTemp) {
                        sb.append("printf \"GPU_T=\"; _rdz \"\$GPU_ZONES\"; ")
                    }
                    if (showDdrTemp) {
                        sb.append("printf \"DDR_T=\"; _rdz \"\$DDR_ZONES\"; ")
                    }
                    if (showCamTemp) {
                        sb.append("printf \"CAM_T=\"; _rdz \"\$CAM_ZONES\"; ")
                    }
                    if (showSkinTemp) {
                        sb.append("printf \"SKIN_T=\"; _rdz \"\$SKIN_ZONES\"; ")
                    }
                    if (showRam && (tickCounter % 6 == 1 || lastRamText.isEmpty())) {
                        if (currentPkg.isNotEmpty()) {
                            sb.append("printf \"APP_PSS=\"; dumpsys meminfo ").append(currentPkg).append(" 2>/dev/null | grep 'TOTAL PSS:' | awk '{print \$3}'; ")
                        }
                        sb.append("{ read -r m1; read -r m2; read -r m3; printf \"%s\\n%s\\n%s\\n\" \"\$m1\" \"\$m2\" \"\$m3\"; } < /proc/meminfo; ")
                    }
                    if (showFps) {
                        sb.append("if [ -n \"\$DRM_NODE\" ]; then read -r dv < \"\$DRM_NODE\" 2>/dev/null && printf \"DRM_FPS=%s\\n\" \"\$dv\"; fi; ")
                        // Chỉ gọi SurfaceFlinger nếu DRM_NODE chưa trả về FPS hợp lệ
                        if (!drmFpsAvailable || tickCounter % 15 == 1) {
                            if (initializedStatsForPkg != currentPkg) {
                                sb.append("dumpsys SurfaceFlinger --timestats -clear -enable >/dev/null 2>&1; ")
                                initializedStatsForPkg = currentPkg
                            }
                            sb.append("printf \"===TIMESTATS===\\n\"; ")
                            sb.append("dumpsys SurfaceFlinger --timestats -dump; ")
                        }
                    }

                    if (sb.isNotEmpty()) {
                        val rawOut = execPersistentShizuku(sb.toString())
                        val splitIdx = rawOut.indexOf("===TIMESTATS===")
                        val batchSection = if (splitIdx >= 0) rawOut.substring(0, splitIdx) else rawOut
                        val timeStatsSection = if (splitIdx >= 0) rawOut.substring(splitIdx + 15) else ""

                        val (activePids, drmFpsFallback) = parseBatchOutput(batchSection)

                        if (currentPkg.isNotEmpty() && batchSection.contains("PID=")) {
                            if (activePids.isEmpty()) {
                                graceTicks++
                                if (graceTicks > 6) {
                                    mainHandler.post { stopSelf() }
                                    break
                                }
                            } else {
                                graceTicks = 0
                            }
                        }

                        if (showFps) {
                            drmFpsAvailable = (drmFpsFallback > 1f)
                            lastFps = parseRealFps(currentPkg, timeStatsSection, drmFpsFallback)
                        }
                    }

                    mainHandler.post { renderTelemetryText() }
                } catch (_: Exception) {}

                try {
                    Thread.sleep(computeNextIntervalMs())
                } catch (_: InterruptedException) {}
            }
        }
    }

    private fun parseMaxThermalTokens(rawTokens: String): Float {
        var maxTemp = 0f
        for (tok in rawTokens.trim().split(WS_REGEX)) {
            val tRaw = tok.toFloatOrNull() ?: continue
            val tC = when {
                tRaw > 1000f -> tRaw / 1000f
                tRaw > 200f -> tRaw / 10f
                else -> tRaw
            }
            if (tC in 20f..125f && tC > maxTemp) {
                maxTemp = tC
            }
        }
        return maxTemp
    }

    private fun parseBatchOutput(raw: String): Pair<Set<String>, Float> {
        var memTotalKb = 0L
        var memAvailKb = 0L
        var appPssKb = 0L
        var globalGpuBytes = -1L
        val procGpuMap = HashMap<String, Long>(8)
        var pidSet = emptySet<String>()
        var drmFps = 0f

        var start = 0
        val len = raw.length
        while (start < len) {
            val end = raw.indexOf('\n', start).let { if (it < 0) len else it }
            val l = raw.substring(start, end).trim()
            start = end + 1
            if (l.isEmpty()) continue

            when {
                l.startsWith("PID=") -> {
                    val rawPids = l.substring(4).trim()
                    if (rawPids.isNotEmpty()) pidSet = rawPids.split(WS_REGEX).toSet()
                }
                l.startsWith("Global total:") -> {
                    globalGpuBytes = l.substring(13).trim().toLongOrNull() ?: -1L
                }
                l.startsWith("Proc ") && l.contains(" total:") -> {
                    val pidKey = l.substringAfter("Proc ").substringBefore(" total:").trim()
                    val bytesVal = l.substringAfter("total:").trim().toLongOrNull() ?: 0L
                    procGpuMap[pidKey] = bytesVal
                }
                l.startsWith("CPU_KHZ=") -> {
                    val maxKhz = l.substring(8).trim().split(WS_REGEX).mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
                    if (maxKhz > 0L) lastCpuKhz = maxKhz
                }
                l.startsWith("CPU_T=") -> {
                    val v = parseMaxThermalTokens(l.substring(6))
                    if (v > 0f) lastCpuTemp = v
                }
                l.startsWith("GPU_T=") -> {
                    val v = parseMaxThermalTokens(l.substring(6))
                    if (v > 0f) lastGpuTemp = v
                }
                l.startsWith("DDR_T=") -> {
                    val v = parseMaxThermalTokens(l.substring(6))
                    if (v > 0f) lastDdrTemp = v
                }
                l.startsWith("CAM_T=") -> {
                    val v = parseMaxThermalTokens(l.substring(6))
                    if (v > 0f) lastCamTemp = v
                }
                l.startsWith("SKIN_T=") -> {
                    val v = parseMaxThermalTokens(l.substring(7))
                    if (v > 0f) lastSkinTemp = v
                }
                l.startsWith("APP_PSS=") -> {
                    appPssKb = l.substring(8).trim().toLongOrNull() ?: 0L
                }
                l.startsWith("MemTotal:") -> {
                    memTotalKb = NUM_REGEX.find(l)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                }
                l.startsWith("MemAvailable:") -> {
                    memAvailKb = NUM_REGEX.find(l)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                }
                l.startsWith("DRM_FPS=") && l.contains("fps:") -> {
                    val fpsVal = FPS_REGEX.find(l)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    if (fpsVal != null && fpsVal > 0f) drmFps = fpsVal
                }
            }
        }

        val socMax = maxOf(lastCpuTemp, lastGpuTemp)
        if (socMax > 0f) lastSocTemp = socMax

        if (pidSet.isNotEmpty()) {
            var gameGpuBytes = 0L
            for (p in pidSet) gameGpuBytes += procGpuMap[p] ?: 0L
            if (gameGpuBytes > 0L) {
                lastGpuVramMb = (gameGpuBytes / 1_048_576L).toInt()
            } else if (globalGpuBytes > 0L) {
                lastGpuVramMb = (globalGpuBytes / 1_048_576L).toInt()
            }
        } else if (globalGpuBytes > 0L) {
            lastGpuVramMb = (globalGpuBytes / 1_048_576L).toInt()
        }

        if (appPssKb > 0L) {
            lastRamText = String.format(Locale.US, "%.1fGB", appPssKb / 1048576.0)
        } else if (memTotalKb > 0L && memAvailKb > 0L) {
            val usedGb = (memTotalKb - memAvailKb) / 1048576.0
            val totalGb = memTotalKb / 1048576.0
            lastRamText = String.format(Locale.US, "%.1f/%.0fG", usedGb, totalGb)
        }

        return pidSet to drmFps
    }

    private fun isIgnoredLayer(layer: String): Boolean {
        return layer.contains("com.nho.upscaler") ||
                layer.contains("Wallpaper") ||
                layer.contains("StatusBar") ||
                layer.contains("NavigationBar") ||
                layer.contains("NotificationShade") ||
                layer.contains("ScreenDecor") ||
                layer.contains("Splash") ||
                layer.contains("Taskbar")
    }

    private fun parseRealFps(pkg: String, dump: String, drmFpsFallback: Float): Float {
        if (drmFpsFallback > 1f) {
            hasValidFpsSample = true
            return drmFpsFallback.coerceIn(1f, 144f)
        }

        val nowNs = System.nanoTime()
        if (dump.isNotEmpty()) {
            var statsEnabled = true
            var globalFrames = -1L
            var currentLayer = ""
            val currentLayerMap = HashMap<String, Long>(12)

            var start = 0
            val len = dump.length
            while (start < len) {
                val end = dump.indexOf('\n', start).let { if (it < 0) len else it }
                val line = dump.substring(start, end).trim()
                start = end + 1

                if (line.startsWith("statsEnabled = 0")) {
                    statsEnabled = false
                } else if (line.startsWith("layerName =")) {
                    currentLayer = line.substring(11).trim()
                } else if (line.startsWith("totalFrames =")) {
                    val frames = line.substring(13).trim().toLongOrNull() ?: -1L
                    if (frames >= 0L) {
                        if (currentLayer.isEmpty()) {
                            if (globalFrames < 0L) globalFrames = frames
                        } else if (!isIgnoredLayer(currentLayer)) {
                            if (pkg.isEmpty() || currentLayer.contains(pkg)) {
                                val existing = currentLayerMap[currentLayer] ?: -1L
                                if (frames > existing) currentLayerMap[currentLayer] = frames
                            }
                        }
                    }
                }
            }

            if (!statsEnabled) initializedStatsForPkg = null

            if (lastFpsSampleNs > 0L) {
                val dtSec = (nowNs - lastFpsSampleNs) / 1_000_000_000.0
                if (dtSec >= 0.15) {
                    var maxLayerDelta = 0L
                    for ((layer, frames) in currentLayerMap) {
                        val prev = prevLayerFrames[layer]
                        if (prev != null && frames >= prev) {
                            val delta = frames - prev
                            if (delta > maxLayerDelta) maxLayerDelta = delta
                        }
                    }

                    val globalDelta = if (prevGlobalFrames >= 0L && globalFrames >= prevGlobalFrames) {
                        globalFrames - prevGlobalFrames
                    } else 0L

                    prevLayerFrames = currentLayerMap
                    prevGlobalFrames = globalFrames
                    lastFpsSampleNs = nowNs

                    val bestDelta = if (pkg.isNotEmpty() && maxLayerDelta > 0L) maxLayerDelta else maxOf(maxLayerDelta, globalDelta)

                    if (bestDelta > 0L) {
                        emptyFpsTicks = 0
                        hasValidFpsSample = true
                        val computed = (bestDelta / dtSec).toFloat()
                        return computed.coerceIn(1f, 144f)
                    } else {
                        emptyFpsTicks++
                        if (emptyFpsTicks >= 5) initializedStatsForPkg = null
                        if (hasValidFpsSample) {
                            return if (emptyFpsTicks <= 2 && lastFps > 0f) lastFps else 0f
                        }
                    }
                } else if (lastFps >= 0f) {
                    return lastFps
                }
            } else {
                prevLayerFrames = currentLayerMap
                prevGlobalFrames = globalFrames
                lastFpsSampleNs = nowNs
            }
        }

        return if (hasValidFpsSample && lastFps >= 0f) lastFps else -1f
    }

    override fun onDestroy() {
        isRunning = false
        isServiceRunning = false
        syncQuickSettingsTile()
        telemetryThread?.interrupt()
        closeShellSession()
        prevLayerFrames.clear()
        try { unregisterReceiver(systemReceiver) } catch (_: Exception) {}
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (_: Exception) {}
        try {
            if (closeTargetView != null) {
                windowManager?.removeView(closeTargetView)
                closeTargetView = null
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
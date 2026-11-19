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
import kotlin.math.roundToInt

class FpsOverlayService : Service() {
    companion object {
        @Volatile
        var isServiceRunning = false
    }

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var closeTargetView: TextView? = null
    private lateinit var prefs: SharedPreferences

    @Volatile private var isRunning = false
    @Volatile private var isScreenOn = true
    @Volatile private var telemetryThread: Thread? = null

    private var targetPkg = ""
    private var gameName = "Game"
    private var scaleLabel = ""
    private var sizeLevel = 1
    private var pollMode = 0 // 0: Auto, 1: 1s, 2: 2s, 3: 3s
    private var isVertical = false
    private var isOverCloseZone = false

    // Persistent Shizuku Shell Stream
    private val shellLock = Any()
    @Volatile private var shellProcess: Process? = null
    @Volatile private var shellWriter: BufferedWriter? = null
    @Volatile private var shellReader: BufferedReader? = null

    private var lastFps = 0
    private var lastBatTemp = 0f
    private var lastCpuTemp = 0f
    private var lastWatts = 0f
    private var isChargingNow = false
    private var lastGpuVramMb = -1
    private var lastCpuKhz = 0L
    private var lastRamText = ""

    private var lastRenderedText = ""
    private var lastRenderedColor = 0

    private var lastFramesCount = -1L
    private var lastSampleTimeMs = 0L
    private var lastTrackedSource = ""
    private var initializedStatsForPkg: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    telemetryThread?.interrupt()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
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

        val rawX = if (isLand) {
            prefs.getInt("overlay_land_x", 160)
        } else {
            prefs.getInt("overlay_port_x", 60)
        }
        val rawY = if (isLand) {
            prefs.getInt("overlay_land_y", 36)
        } else {
            prefs.getInt("overlay_port_y", 120)
        }

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
                    applyOverlayStyle()
                    renderTelemetryText()
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
            Toast.makeText(this, "⚠️ Thiếu quyền Hiển thị trên ứng dụng khác!", Toast.LENGTH_LONG).show()
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
                "Dịch vụ HUD & Upscale",
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
            "⚡ Đang kích SSAA $scaleLabel cho $gameName"
        } else {
            "📊 Đang chạy HUD đo thông số"
        }

        val contentText = if (isUpscaleActive) {
            "Chạm để Khôi phục màn hình gốc & tắt HUD"
        } else {
            "Chạm để tắt nhanh thanh đo (Không reset màn hình)"
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
                                    Toast.makeText(this@FpsOverlayService, "Đã ẩn thanh đo thông số", Toast.LENGTH_SHORT).show()
                                    stopSelf()
                                    return true
                                }
                                if (isLandscapeMode()) {
                                    prefs.edit()
                                        .putInt("overlay_land_x", params.x)
                                        .putInt("overlay_land_y", params.y)
                                        .apply()
                                } else {
                                    prefs.edit()
                                        .putInt("overlay_port_x", params.x)
                                        .putInt("overlay_port_y", params.y)
                                        .apply()
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
            Toast.makeText(this, "Lỗi bật cửa sổ nổi: ${e.message}", Toast.LENGTH_LONG).show()
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
        if (isVertical) {
            tv.setLineSpacing(4f, 1.08f)
        } else {
            tv.setLineSpacing(0f, 1.0f)
        }

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
        val showBatTemp = prefs.getBoolean("m_bat_temp", true)
        val showCpuTemp = prefs.getBoolean("m_cpu_temp", false)
        val showWatt = prefs.getBoolean("m_watt", true)
        val showGpu = prefs.getBoolean("m_gpu", true)
        val showCpuFreq = prefs.getBoolean("m_cpu_freq", false)
        val showRam = prefs.getBoolean("m_ram", false)

        val parts = mutableListOf<String>()
        val isCompact = (sizeLevel == 0)

        if (showFps) {
            val fpsVal = if (lastFps > 0) "$lastFps" else "--"
            parts.add(if (isCompact) "${fpsVal}F" else if (isVertical) "⚡ FPS: $fpsVal" else "⚡$fpsVal FPS")
        }
        if (showBatTemp) {
            val tVal = if (lastBatTemp > 0f) String.format(Locale.US, "%.1f°", lastBatTemp) else "--°"
            parts.add(if (isCompact) "🔋$tVal" else if (isVertical) "🔋 Pin: ${tVal}C" else "🔋${tVal}C")
        }
        if (showCpuTemp) {
            val ctVal = if (lastCpuTemp > 0f) String.format(Locale.US, "%.0f°", lastCpuTemp) else "--°"
            parts.add(if (isCompact) "🔥$ctVal" else if (isVertical) "🔥 CPU: ${ctVal}C" else "🔥${ctVal}C")
        }
        if (showWatt) {
            val prefix = if (isChargingNow) "⚡+" else ""
            val wVal = if (lastWatts > 0f) String.format(Locale.US, "%s%.1fW", prefix, lastWatts) else "--W"
            parts.add(if (isCompact) wVal else if (isVertical) "🔌 Điện: $wVal" else "🔌$wVal")
        }
        if (showGpu) {
            val vramStr = if (lastGpuVramMb >= 0) "${lastGpuVramMb}MB" else "--MB"
            parts.add(if (isCompact) "G:${if (lastGpuVramMb >= 0) "${lastGpuVramMb}M" else "--"}" else if (isVertical) "🎮 VRAM: $vramStr" else "🎮$vramStr")
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
            parts.add("⚡ Chưa chọn thông số")
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

        val color = when {
            lastBatTemp >= 43.0f || lastCpuTemp >= 85.0f -> Color.parseColor("#FF5252")
            lastBatTemp >= 40.0f || lastCpuTemp >= 75.0f -> Color.parseColor("#FFD740")
            else -> Color.parseColor("#00E676")
        }
        if (color != lastRenderedColor) {
            lastRenderedColor = color
            tv.setTextColor(color)
        }
    }

    private fun readNativeBatteryPower() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val rawCurr = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val batIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawVoltMv = batIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val rawTempTenths = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val status = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            isChargingNow = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)

            if (rawTempTenths > 0) {
                lastBatTemp = rawTempTenths / 10f
            }

            if (rawCurr != Int.MIN_VALUE && rawCurr != 0 && rawVoltMv > 0) {
                val absCurr = abs(rawCurr.toDouble())
                val amps = if (absCurr > 15_000.0) absCurr / 1_000_000.0 else absCurr / 1_000.0
                val volts = rawVoltMv / 1_000.0
                val instWatts = (amps * volts).toFloat()
                if (instWatts in 0.1f..180f) {
                    lastWatts = if (lastWatts <= 0f || abs(instWatts - lastWatts) > 4.0f) {
                        instWatts
                    } else {
                        lastWatts * 0.55f + instWatts * 0.45f
                    }
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
            val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))

            val initCmd = "CPU_ZONES=\"\"; for z in /sys/class/thermal/thermal_zone*; do " +
                    "t=\$(cat \"\$z/type\" 2>/dev/null | tr 'A-Z' 'a-z'); " +
                    "case \"\$t\" in *gpu*|*npu*|*modem*|*pa*|*skin*|*bat*|*wlan*|*usb*|*cam*|*flash*|*chg*) ;; " +
                    "*cpu*|*cpuss*|*apc*|*tsens*|*ap*|*soc*) [ -r \"\$z/temp\" ] && CPU_ZONES=\"\$CPU_ZONES \$z/temp\";; esac; " +
                    "done\n"
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

                writer.write("$cmd\necho $marker\n")
                writer.flush()

                val out = StringBuilder()
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
            1 -> 1000L
            2 -> 2000L
            3 -> 3000L
            else -> {
                if (targetPkg.isNotEmpty() || isLandscapeMode() || lastFps >= 45) 1000L else 2500L
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryThread = thread(name = "HudTelemetryLoop") {
            var graceTicks = 0
            while (isRunning) {
                if (!isScreenOn) {
                    try { Thread.sleep(1500) } catch (_: InterruptedException) {}
                    continue
                }

                try {
                    val currentPkg = targetPkg
                    val showFps = prefs.getBoolean("m_fps", true)
                    val showBatTemp = prefs.getBoolean("m_bat_temp", true)
                    val showCpuTemp = prefs.getBoolean("m_cpu_temp", false)
                    val showWatt = prefs.getBoolean("m_watt", true)
                    val showGpu = prefs.getBoolean("m_gpu", true)
                    val showCpuFreq = prefs.getBoolean("m_cpu_freq", false)
                    val showRam = prefs.getBoolean("m_ram", false)

                    if (showBatTemp || showWatt) {
                        readNativeBatteryPower()
                    }

                    val sb = StringBuilder()
                    if (showFps && initializedStatsForPkg != currentPkg) {
                        sb.append("dumpsys SurfaceFlinger --timestats -clear -enable >/dev/null 2>&1; ")
                        initializedStatsForPkg = currentPkg
                    }

                    if (currentPkg.isNotEmpty()) {
                        sb.append("echo \"PID=\$(pidof $currentPkg)\"; ")
                    }
                    if (showGpu) {
                        sb.append("dumpsys gpu | head -n 45 | grep -E 'Global total:|Proc [0-9]+ total:'; ")
                    }
                    if (showCpuFreq) {
                        sb.append("echo \"CPU_KHZ=\$(cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq 2>/dev/null | sort -nr | head -n 1)\"; ")
                    }
                    if (showCpuTemp) {
                        sb.append("echo \"CPU_T=\$(cat \$CPU_ZONES 2>/dev/null)\"; ")
                    }
                    if (showRam) {
                        if (currentPkg.isNotEmpty()) {
                            sb.append("echo \"APP_PSS=\$(dumpsys meminfo $currentPkg 2>/dev/null | grep 'TOTAL PSS:' | awk '{print \$3}')\"; ")
                        }
                        sb.append("grep -E 'MemTotal:|MemAvailable:' /proc/meminfo; ")
                    }
                    if (showFps) {
                        sb.append("echo \"DRM_FPS=\$(cat /sys/class/drm/sde-crtc-0/measured_fps /sys/class/drm/card0/sde-crtc-0/measured_fps 2>/dev/null | head -n 1)\"; ")
                        sb.append("echo \"===TIMESTATS===\"; ")
                        sb.append("dumpsys SurfaceFlinger --timestats -dump; ")
                    }

                    val cmdStr = sb.toString()
                    if (cmdStr.isNotEmpty()) {
                        val rawOut = execPersistentShizuku(cmdStr)
                        val splitIdx = rawOut.indexOf("===TIMESTATS===")
                        val batchSection = if (splitIdx >= 0) rawOut.substring(0, splitIdx) else rawOut
                        val timeStatsSection = if (splitIdx >= 0) rawOut.substring(splitIdx + "===TIMESTATS===".length) else ""

                        val (activePids, drmFpsFallback) = parseBatchOutput(batchSection)

                        if (currentPkg.isNotEmpty() && batchSection.contains("PID=")) {
                            if (activePids.isEmpty()) {
                                graceTicks++
                                if (graceTicks > 8) {
                                    mainHandler.post { stopSelf() }
                                    break
                                }
                            } else {
                                graceTicks = 0
                            }
                        }

                        if (showFps) {
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

    private fun parseBatchOutput(raw: String): Pair<Set<String>, Int> {
        var memTotalKb = 0L
        var memAvailKb = 0L
        var appPssKb = 0L
        var globalGpuBytes = -1L
        val procGpuMap = mutableMapOf<String, Long>()
        var pidSet = emptySet<String>()
        var drmFps = 0

        for (line in raw.lines()) {
            val l = line.trim()
            when {
                l.startsWith("PID=") -> {
                    val rawPids = l.substringAfter("PID=").trim()
                    if (rawPids.isNotEmpty()) {
                        pidSet = rawPids.split(Regex("\\s+")).toSet()
                    }
                }
                l.startsWith("Global total:") -> {
                    globalGpuBytes = l.substringAfter(":").trim().toLongOrNull() ?: -1L
                }
                l.startsWith("Proc ") && l.contains(" total:") -> {
                    val pidKey = l.substringAfter("Proc ").substringBefore(" total:").trim()
                    val bytesVal = l.substringAfter("total:").trim().toLongOrNull() ?: 0L
                    procGpuMap[pidKey] = bytesVal
                }
                l.startsWith("CPU_KHZ=") -> {
                    val khz = l.substringAfter("=").trim().toLongOrNull() ?: 0L
                    if (khz > 0L) lastCpuKhz = khz
                }
                l.startsWith("CPU_T=") -> {
                    val tokens = l.substringAfter("=").trim().split(Regex("\\s+"))
                    val validTemps = tokens.mapNotNull { tok ->
                        val tRaw = tok.toFloatOrNull() ?: return@mapNotNull null
                        val tC = when {
                            tRaw > 1000f -> tRaw / 1000f
                            tRaw > 200f -> tRaw / 10f
                            else -> tRaw
                        }
                        if (tC in 20f..120f) tC else null
                    }.sorted()

                    if (validTemps.isNotEmpty()) {
                        val trimmed = if (validTemps.size >= 3) validTemps.dropLast(1) else validTemps
                        lastCpuTemp = trimmed.average().toFloat()
                    }
                }
                l.startsWith("APP_PSS=") -> {
                    appPssKb = l.substringAfter("=").trim().toLongOrNull() ?: 0L
                }
                l.startsWith("MemTotal:") -> {
                    memTotalKb = Regex("([0-9]+)").find(l)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                }
                l.startsWith("MemAvailable:") -> {
                    memAvailKb = Regex("([0-9]+)").find(l)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                }
                l.startsWith("DRM_FPS=") && l.contains("fps:") -> {
                    val fpsVal = Regex("fps:\\s*([0-9.]+)").find(l)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    if (fpsVal != null && fpsVal > 0f) {
                        drmFps = fpsVal.roundToInt()
                    }
                }
            }
        }

        if (pidSet.isNotEmpty()) {
            var gameGpuBytes = 0L
            for (p in pidSet) {
                gameGpuBytes += procGpuMap[p] ?: 0L
            }
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

    private fun parseRealFps(pkg: String, dump: String, drmFpsFallback: Int): Int {
        if (dump.isNotEmpty()) {
            var statsEnabled = true
            var globalFrames = -1L
            var currentLayer = ""
            val matchingLayers = mutableListOf<Pair<String, Long>>()

            for (rawLine in dump.lines()) {
                val line = rawLine.trim()
                if (line.startsWith("statsEnabled = 0")) {
                    statsEnabled = false
                } else if (line.startsWith("layerName =")) {
                    currentLayer = line.substringAfter("=").trim()
                } else if (line.startsWith("totalFrames =")) {
                    val frames = line.substringAfter("=").trim().toLongOrNull() ?: -1L
                    if (currentLayer.isEmpty()) {
                        globalFrames = frames
                    } else if (pkg.isNotEmpty() && currentLayer.contains(pkg) && !currentLayer.contains("Splash")) {
                        matchingLayers.add(currentLayer to frames)
                    }
                }
            }

            if (!statsEnabled) {
                initializedStatsForPkg = null
            }

            val bestLayer = matchingLayers.maxByOrNull { it.second }
            if (pkg.isNotEmpty() && bestLayer == null) {
                initializedStatsForPkg = null
            }

            val sourceKey = bestLayer?.first ?: "GLOBAL"
            val currentFrames = bestLayer?.second ?: globalFrames
            val nowMs = System.currentTimeMillis()

            if (currentFrames >= 0L) {
                if (sourceKey == lastTrackedSource && lastFramesCount >= 0L && currentFrames >= lastFramesCount) {
                    val dt = (nowMs - lastSampleTimeMs) / 1000.0
                    val df = currentFrames - lastFramesCount
                    lastFramesCount = currentFrames
                    lastSampleTimeMs = nowMs
                    if (dt > 0.25) {
                        val fps = (df / dt).roundToInt()
                        if (fps in 1..165) return fps
                        if (fps == 0) return lastFps
                    }
                } else {
                    lastTrackedSource = sourceKey
                    lastFramesCount = currentFrames
                    lastSampleTimeMs = nowMs
                    if (lastFps > 0) return lastFps
                }
            }
        }

        if (drmFpsFallback > 0) return drmFpsFallback
        return 0
    }

    override fun onDestroy() {
        isRunning = false
        isServiceRunning = false
        syncQuickSettingsTile()
        telemetryThread?.interrupt()
        closeShellSession()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
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
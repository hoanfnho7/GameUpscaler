package com.nho.upscaler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class FpsOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var closeTargetView: TextView? = null
    private lateinit var prefs: SharedPreferences
    private var isRunning = false
    private var targetPkg = ""
    private var gameName = "Game"
    private var scaleLabel = ""
    private var sizeLevel = 1
    private var isVertical = false
    private var isOverCloseZone = false

    // Giá trị đo thực tế
    private var lastFps = 0
    private var lastBatTemp = 0f
    private var lastCpuTemp = 0f
    private var lastWatts = 0f
    private var lastGpuLoad = -1
    private var lastGpuMhz = 0
    private var lastCpuKhz = 0L
    private var lastHz = 0
    private var lastRamText = ""
    private var lastPingMs = -1

    // Biến đếm TimeStats & chu kỳ Ping
    private var lastFramesCount = -1L
    private var lastSampleTimeMs = 0L
    private var lastTrackedSource = ""
    private var clearedForPkg = ""
    private var tickCounter = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)
        sizeLevel = prefs.getInt("overlay_size_level", 1)
        isVertical = prefs.getBoolean("overlay_vertical", false)

        when (intent?.action) {
            "STOP_OVERLAY" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "UPDATE_STYLE" -> {
                if (overlayView != null) {
                    applyOverlayStyle()
                    renderTelemetryText()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        targetPkg = intent?.getStringExtra("target_pkg") ?: ""
        gameName = intent?.getStringExtra("game_name") ?: "Chế độ xem thử"
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
            applyOverlayStyle()
            renderTelemetryText()
        }

        if (!isRunning) {
            isRunning = true
            startTelemetryLoop()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "upscaler_active_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Trạng thái Upscale & FPS", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }

        val resetIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("action_type", "RESET_NOW")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 99, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = if (scaleLabel.isNotEmpty()) {
            "⚡ Đang kích SSAA $scaleLabel cho $gameName"
        } else {
            "📊 Đang chạy HUD đo thông số phần cứng"
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
            .setContentText("Chạm vào đây để Khôi phục màn hình & tắt HUD")
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
                x = prefs.getInt("overlay_x", 60)
                y = prefs.getInt("overlay_y", 120)
            }

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
                                prefs.edit()
                                    .putInt("overlay_x", params.x)
                                    .putInt("overlay_y", params.y)
                                    .apply()
                            } else {
                                sizeLevel = (sizeLevel + 1) % 4
                                prefs.edit().putInt("overlay_size_level", sizeLevel).apply()
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
        val showHz = prefs.getBoolean("m_hz", false)
        val showRam = prefs.getBoolean("m_ram", false)
        val showPing = prefs.getBoolean("m_ping", false)

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
            val wVal = if (lastWatts > 0f) String.format(Locale.US, "%.1fW", lastWatts) else "--W"
            parts.add(if (isCompact) wVal else if (isVertical) "🔌 Điện: $wVal" else "🔌$wVal")
        }
        if (showGpu) {
            val gPct = if (lastGpuLoad >= 0) "$lastGpuLoad%" else "--%"
            val gClk = if (lastGpuMhz > 0) " (${lastGpuMhz}MHz)" else ""
            parts.add(if (isCompact) "G:$gPct" else if (isVertical) "🎮 GPU: $gPct$gClk" else "🎮$gPct")
        }
        if (showCpuFreq) {
            val cStr = when {
                lastCpuKhz >= 1_000_000L -> String.format(Locale.US, "%.2fGHz", lastCpuKhz / 1_000_000.0)
                lastCpuKhz > 0L -> "${lastCpuKhz / 1000L}MHz"
                else -> "--GHz"
            }
            parts.add(if (isCompact) cStr else if (isVertical) "⚙️ CPU: $cStr" else "⚙️$cStr")
        }
        if (showHz) {
            val hStr = if (lastHz > 0) "${lastHz}Hz" else "--Hz"
            parts.add(if (isCompact) hStr else if (isVertical) "🖥️ Màn: $hStr" else "🖥️$hStr")
        }
        if (showRam) {
            val rStr = lastRamText.ifEmpty { "--GB" }
            parts.add(if (isCompact) rStr else if (isVertical) "💾 RAM: $rStr" else "💾$rStr")
        }
        if (showPing) {
            val pStr = if (lastPingMs >= 0) "${lastPingMs}ms" else "--ms"
            parts.add(if (isCompact) pStr else if (isVertical) "🌐 Ping: $pStr" else "🌐$pStr")
        }

        if (parts.isEmpty()) {
            parts.add("⚡ Chưa chọn thông số")
        }

        tv.text = if (isVertical) {
            parts.joinToString("\n")
        } else {
            parts.joinToString(if (isCompact) " · " else " | ")
        }

        val color = when {
            lastBatTemp >= 43.0f || lastCpuTemp >= 82.0f -> Color.parseColor("#FF5252")
            lastBatTemp >= 40.0f || lastCpuTemp >= 72.0f -> Color.parseColor("#FFD740")
            else -> Color.parseColor("#00E676")
        }
        tv.setTextColor(color)
    }

    private fun startTelemetryLoop() {
        thread {
            execShizuku("dumpsys SurfaceFlinger --timestats -clear -enable")

            var graceTicks = 0
            while (isRunning) {
                try {
                    tickCounter++
                    val showFps = prefs.getBoolean("m_fps", true)
                    val showBatTemp = prefs.getBoolean("m_bat_temp", true)
                    val showCpuTemp = prefs.getBoolean("m_cpu_temp", false)
                    val showWatt = prefs.getBoolean("m_watt", true)
                    val showGpu = prefs.getBoolean("m_gpu", true)
                    val showCpuFreq = prefs.getBoolean("m_cpu_freq", false)
                    val showHz = prefs.getBoolean("m_hz", false)
                    val showRam = prefs.getBoolean("m_ram", false)
                    val showPing = prefs.getBoolean("m_ping", false)

                    val sb = StringBuilder()
                    if (targetPkg.isNotEmpty()) {
                        sb.append("echo \"PID=\$(pidof $targetPkg)\"; ")
                    }
                    if (showBatTemp || showWatt) {
                        sb.append("dumpsys battery | grep -E 'temperature:|current now:|voltage:' | sed 's/^ *//'; ")
                    }
                    if (showWatt) {
                        sb.append("echo \"SYS_I=\$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)\"; ")
                        sb.append("echo \"SYS_V=\$(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)\"; ")
                    }
                    if (showGpu) {
                        sb.append("echo \"GPU_PCT=\$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null)\"; ")
                        sb.append("echo \"GPU_BUSY=\$(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null)\"; ")
                        sb.append("echo \"GPU_CLK=\$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null)\"; ")
                    }
                    if (showCpuFreq) {
                        sb.append("echo \"CPU_KHZ=\$(cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq 2>/dev/null | sort -nr | head -n 1)\"; ")
                    }
                    if (showCpuTemp) {
                        sb.append("echo \"CPU_T=\$(for z in /sys/class/thermal/thermal_zone*; do t=\$(cat \"\$z/type\" 2>/dev/null); case \"\$t\" in *cpu*|*CPU*|*soc*|*SOC*|*tsens*|*ap*|*nspss*) cat \"\$z/temp\" 2>/dev/null;; esac; done | sort -nr | head -n 1)\"; ")
                    }
                    if (showHz) {
                        sb.append("echo \"DISP_HZ=\$(dumpsys display | grep -oE '(renderFrameRate|fps|refreshRate)[=:]\\s*[0-9.]+' | head -n 1 | grep -oE '[0-9.]+')\"; ")
                    }
                    if (showRam) {
                        if (targetPkg.isNotEmpty()) {
                            sb.append("echo \"APP_PSS=\$(dumpsys meminfo $targetPkg 2>/dev/null | grep 'TOTAL PSS:' | awk '{print \$3}')\"; ")
                        }
                        sb.append("grep -E 'MemTotal:|MemAvailable:' /proc/meminfo; ")
                    }
                    if (showPing && (tickCounter % 3 == 1)) {
                        sb.append("echo \"PING_MS=\$(ping -c 1 -W 1 8.8.8.8 2>/dev/null | grep -oE 'time=[0-9.]+' | cut -d= -f2)\"; ")
                    }

                    val batchOut = execShizuku(sb.toString())
                    parseBatchOutput(batchOut)

                    if (targetPkg.isNotEmpty() && batchOut.contains("PID=")) {
                        val pidVal = batchOut.lines().firstOrNull { it.startsWith("PID=") }?.substringAfter("PID=")?.trim() ?: ""
                        if (pidVal.isEmpty()) {
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
                        lastFps = measureRealFps(targetPkg)
                    }

                    mainHandler.post { renderTelemetryText() }
                } catch (_: Exception) {}

                Thread.sleep(1000)
            }
        }
    }

    private fun parseBatchOutput(raw: String) {
        var dumpTemp = 0f
        var dumpCurr = 0.0
        var dumpVolt = 0.0
        var sysCurr = 0.0
        var sysVolt = 0.0
        var memTotalKb = 0L
        var memAvailKb = 0L
        var appPssKb = 0L

        for (line in raw.lines()) {
            val l = line.trim()
            when {
                l.startsWith("temperature:") -> {
                    dumpTemp = (l.substringAfter(":").trim().toFloatOrNull() ?: 0f) / 10f
                }
                l.startsWith("current now:") -> {
                    dumpCurr = l.substringAfter(":").trim().toDoubleOrNull() ?: 0.0
                }
                l.startsWith("voltage:") -> {
                    dumpVolt = l.substringAfter(":").trim().toDoubleOrNull() ?: 0.0
                }
                l.startsWith("SYS_I=") -> {
                    sysCurr = l.substringAfter("=").trim().toDoubleOrNull() ?: 0.0
                }
                l.startsWith("SYS_V=") -> {
                    sysVolt = l.substringAfter("=").trim().toDoubleOrNull() ?: 0.0
                }
                l.startsWith("GPU_PCT=") -> {
                    val pctNum = Regex("([0-9]+)").find(l.substringAfter("="))?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (pctNum != null) lastGpuLoad = pctNum.coerceIn(0, 100)
                }
                l.startsWith("GPU_BUSY=") -> {
                    val cols = l.substringAfter("=").trim().split(Regex("\\s+"))
                    if (cols.size >= 2) {
                        val busy = cols[0].toLongOrNull() ?: 0L
                        val total = cols[1].toLongOrNull() ?: 0L
                        if (total > 0L) {
                            lastGpuLoad = ((busy * 100L) / total).toInt().coerceIn(0, 100)
                        }
                    }
                }
                l.startsWith("GPU_CLK=") -> {
                    val hz = l.substringAfter("=").trim().toLongOrNull() ?: 0L
                    if (hz > 0L) lastGpuMhz = (hz / 1_000_000L).toInt()
                }
                l.startsWith("CPU_KHZ=") -> {
                    val khz = l.substringAfter("=").trim().toLongOrNull() ?: 0L
                    if (khz > 0L) lastCpuKhz = khz
                }
                l.startsWith("CPU_T=") -> {
                    val tRaw = l.substringAfter("=").trim().toFloatOrNull() ?: 0f
                    val tC = when {
                        tRaw > 1000f -> tRaw / 1000f
                        tRaw > 200f -> tRaw / 10f
                        else -> tRaw
                    }
                    if (tC in 20f..125f) lastCpuTemp = tC
                }
                l.startsWith("DISP_HZ=") -> {
                    val h = l.substringAfter("=").trim().toFloatOrNull() ?: 0f
                    if (h > 0f) lastHz = h.roundToInt()
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
                l.startsWith("PING_MS=") -> {
                    val p = l.substringAfter("=").trim().toFloatOrNull()
                    if (p != null && p >= 0f) lastPingMs = p.roundToInt()
                }
            }
        }

        if (dumpTemp > 0f) lastBatTemp = dumpTemp

        val rawI = if (abs(sysCurr) > 0.0) abs(sysCurr) else abs(dumpCurr)
        val rawV = if (abs(sysVolt) > 0.0) abs(sysVolt) else abs(dumpVolt)
        if (rawI > 0.0 && rawV > 0.0) {
            val amps = if (rawI > 10_000.0) rawI / 1_000_000.0 else rawI / 1_000.0
            val volts = if (rawV > 100_000.0) rawV / 1_000_000.0 else rawV / 1_000.0
            val w = (amps * volts).toFloat()
            if (w in 0.1f..160f) lastWatts = w
        }

        if (appPssKb > 0L) {
            lastRamText = String.format(Locale.US, "%.1fGB", appPssKb / 1048576.0)
        } else if (memTotalKb > 0L && memAvailKb > 0L) {
            val usedGb = (memTotalKb - memAvailKb) / 1048576.0
            val totalGb = memTotalKb / 1048576.0
            lastRamText = String.format(Locale.US, "%.1f/%.0fG", usedGb, totalGb)
        }
    }

    private fun measureRealFps(pkg: String): Int {
        val dump = execShizuku("dumpsys SurfaceFlinger --timestats -dump")
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
                execShizuku("dumpsys SurfaceFlinger --timestats -clear -enable")
            }

            val bestLayer = matchingLayers.maxByOrNull { it.second }
            if (pkg.isNotEmpty() && bestLayer == null && clearedForPkg != pkg) {
                execShizuku("dumpsys SurfaceFlinger --timestats -clear -enable")
                clearedForPkg = pkg
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

        val drmOut = execShizuku("cat /sys/class/drm/sde-crtc-0/measured_fps /sys/class/drm/card0/sde-crtc-0/measured_fps 2>/dev/null").trim()
        if (drmOut.contains("fps:")) {
            val fpsVal = Regex("fps:\\s*([0-9.]+)").find(drmOut)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            if (fpsVal != null && fpsVal > 0f) {
                return fpsVal.roundToInt()
            }
        }

        return 0
    }

    private fun execShizuku(cmd: String): String {
        return try {
            if (!Shizuku.pingBinder()) return ""
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            proc.waitFor()
            out
        } catch (_: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        isRunning = false
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
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
    private var lastFps = 0
    private var lastTemp = 0f
    private var isOverCloseZone = false

    // Biến theo dõi bộ đếm khung hình SurfaceFlinger TimeStats
    private var lastFramesCount = -1L
    private var lastSampleTimeMs = 0L
    private var lastTrackedSource = ""
    private var clearedForPkg = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)
        sizeLevel = prefs.getInt("overlay_size_level", 1)

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
            "📊 Đang chạy khung đo FPS & Nhiệt độ"
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
            .setContentText("Chạm vào đây để Khôi phục màn hình & tắt đo FPS")
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
                                    Toast.makeText(this@FpsOverlayService, "Đã ẩn thanh đo FPS", Toast.LENGTH_SHORT).show()
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
        when (sizeLevel) {
            0 -> {
                tv.textSize = 9.5f
                tv.setPadding(14, 6, 14, 6)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#88101216"))
                    cornerRadius = 16f
                    setStroke(1, Color.parseColor("#4400E676"))
                }
            }
            1 -> {
                tv.textSize = 11f
                tv.setPadding(18, 8, 18, 8)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B3101216"))
                    cornerRadius = 20f
                    setStroke(1, Color.parseColor("#5500E676"))
                }
            }
            2 -> {
                tv.textSize = 12.5f
                tv.setPadding(24, 12, 24, 12)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC101216"))
                    cornerRadius = 24f
                    setStroke(2, Color.parseColor("#6600E676"))
                }
            }
            else -> {
                tv.textSize = 14.5f
                tv.setPadding(28, 14, 28, 14)
                tv.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DD101216"))
                    cornerRadius = 28f
                    setStroke(2, Color.parseColor("#7700E676"))
                }
            }
        }
    }

    private fun renderTelemetryText() {
        val tv = overlayView ?: return
        val fpsNum = if (lastFps > 0) "$lastFps" else "--"
        val tempNum = if (lastTemp > 0f) String.format(Locale.US, "%.1f", lastTemp) else "--.-"

        tv.text = when (sizeLevel) {
            0 -> "$fpsNum | ${tempNum}°"
            1 -> "⚡$fpsNum | 🌡️${tempNum}°C"
            else -> "⚡ $fpsNum FPS  |  🌡️ ${tempNum}°C"
        }

        val color = when {
            lastTemp >= 43.0f -> Color.parseColor("#FF5252")
            lastTemp >= 40.0f -> Color.parseColor("#FFD740")
            else -> Color.parseColor("#00E676")
        }
        tv.setTextColor(color)
    }

    private fun startTelemetryLoop() {
        thread {
            // Kích hoạt & làm sạch bảng thống kê TimeStats của SurfaceFlinger
            execShizuku("dumpsys SurfaceFlinger --timestats -clear -enable")

            var graceTicks = 0
            while (isRunning) {
                try {
                    if (targetPkg.isNotEmpty()) {
                        val pidOut = execShizuku("pidof $targetPkg").trim()
                        if (pidOut.isEmpty()) {
                            graceTicks++
                            if (graceTicks > 8) {
                                mainHandler.post { stopSelf() }
                                break
                            }
                        } else {
                            graceTicks = 0
                        }
                    }

                    val batRaw = execShizuku("dumpsys battery | grep temperature | cut -d: -f2 | tr -d ' \\r'").trim()
                    lastTemp = (batRaw.toFloatOrNull() ?: 0f) / 10f
                    lastFps = measureRealFps(targetPkg)

                    mainHandler.post { renderTelemetryText() }
                } catch (_: Exception) {}

                Thread.sleep(1000)
            }
        }
    }

    private fun measureRealFps(pkg: String): Int {
        // Phương pháp 1 (Chuẩn Android 14-17): Đọc bộ đếm khung hình trực tiếp từ SurfaceFlinger TimeStats
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
                // Dọn chỗ trống trong bảng 200 layer của TimeStats để nhận diện game mới mở
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

        // Phương pháp 2 (Dự phòng): Đọc trực tiếp driver hiển thị Qualcomm
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
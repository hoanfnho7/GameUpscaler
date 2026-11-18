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

class FpsOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private lateinit var prefs: SharedPreferences
    private var isRunning = false
    private var targetPkg = ""
    private var gameName = "Game"
    private var scaleLabel = ""
    private var sizeLevel = 1
    private var lastFps = 0
    private var lastTemp = 0f
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
            createOverlayView()
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

    private fun createOverlayView() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val tv = TextView(this).apply {
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#00E676"))
            }
            overlayView = tv
            applyOverlayStyle()
            renderTelemetryText()

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

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
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (abs(dx) > 8 || abs(dy) > 8) {
                                isDragging = true
                                params.x = initialX + dx
                                params.y = initialY + dy
                                windowManager?.updateViewLayout(overlayView, params)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isDragging) {
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
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(tv, params)
        } catch (e: Exception) {
            overlayView = null
            Toast.makeText(this, "Lỗi bật cửa sổ nổi (Hãy bật quyền Pop-up nền của OriginOS): ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
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
        if (pkg.isEmpty()) return 0
        var winName = execShizuku("dumpsys SurfaceFlinger --list | grep 'SurfaceView' | grep '$pkg' | head -n 1").trim()
        if (winName.isEmpty()) {
            winName = execShizuku("dumpsys SurfaceFlinger --list | grep '$pkg' | grep -v 'Splash' | head -n 1").trim()
        }
        if (winName.isEmpty()) return 0

        val latencyRaw = execShizuku("dumpsys SurfaceFlinger --latency \"$winName\"")
        val lines = latencyRaw.lines().filter { it.trim().isNotEmpty() }
        if (lines.size < 5) return 0

        val timestamps = mutableListOf<Long>()
        for (i in 1 until lines.size) {
            val cols = lines[i].trim().split(Regex("\\s+"))
            if (cols.size >= 2) {
                val presentTime = cols[1].toLongOrNull() ?: 0L
                if (presentTime > 0L && presentTime != Long.MAX_VALUE) {
                    timestamps.add(presentTime)
                }
            }
        }

        if (timestamps.size < 3) return 0
        val latest = timestamps.last()
        val oneSecAgo = latest - 1_000_000_000L
        val framesInLastSec = timestamps.count { it >= oneSecAgo }
        return if (framesInLastSec in 1..144) framesInLastSec else 0
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
        super.onDestroy()
    }
}
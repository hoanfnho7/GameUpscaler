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
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
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
        private val HAL_TEMP_REGEX = Regex("mValue=([-0-9.]+),\\s*mType=(\\d+)")
    }

    private data class HudMetricEntry(
        val vLabel: String,
        val hLabel: String,
        val isSuffixLabel: Boolean = false,
        val fullValue: String,
        val conciseValue: String,
        val valueColor: Int,
        val isProminent: Boolean = false,
        val isWarningPill: Boolean = false
    )

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var closeTargetView: TextView? = null
    private lateinit var prefsMgr: PrefsManager
    private var batteryManager: BatteryManager? = null

    @Volatile private var isRunning = false
    @Volatile private var isScreenOn = true
    @Volatile private var telemetryThread: Thread? = null

    private var targetPkg = ""
    private var gameName = "Game"
    private var scaleLabel = ""

    @Volatile private var detectedPkg = ""
    @Volatile private var detectedAppName = ""

    private var sizeLevel = 1
    private var hudScale = 0.70f
    private var bgMode = 1
    private var pollMode = 0
    private var isVertical = false
    private var isOverCloseZone = false

    private val shellLock = Any()
    @Volatile private var shellProcess: Process? = null
    @Volatile private var shellWriter: BufferedWriter? = null
    @Volatile private var shellReader: BufferedReader? = null

    private var lastFps = -1f
    private val frameTimeWindow = ArrayDeque<Float>(10)
    private var lastFrameTimeMs = -1f
    @Volatile private var cachedBatTemp = 0f
    @Volatile private var cachedBatPct = 100
    @Volatile private var cachedVoltMv = 0
    @Volatile private var isChargingNow = false

    // Idle polling & Thermal Guard states (inspired by Scene)
    private var idleStartTimeMs = 0L
    private var isIdleMode = false
    @Volatile private var thermalAlertState = 0 // 0 = OK, 1 = Overheat, 2 = Low Battery
    private var lastGuardToastMs = 0L
    private var guardAutoResetDone = false

    private var lastSocTemp = 0f
    private var lastCpuTemp = 0f
    private var lastGpuTemp = 0f
    private var lastDdrTemp = 0f
    private var lastCamTemp = 0f
    private var lastSkinTemp = 0f
    private var lastWatts = 0f
    private var lastGpuLoadPct = -1
    private var lastGpuVramMb = -1
    private var lastCpuLoadPct = -1
    private var prevCpuTotal = -1L
    private var prevCpuIdle = -1L
    private var lastCpuKhz = 0L
    private var lastRamText = ""

    private var lastRenderedText = ""
    private var lastRenderedColor = 0

    private var prevLayerFrames = HashMap<String, Long>(16)
    private var prevLayerJanks = HashMap<String, Long>(16)
    private var prevGlobalFrames = -1L
    private var prevGlobalJanks = -1L
    private var lastFpsSampleNs = 0L
    private var emptyFpsTicks = 0
    private var hasValidFpsSample = false
    private var initializedStatsForPkg: String? = null
    private var lastDrmFps = -1f
    private var frozenDrmTicks = 0
    private var emptyThermalTicks = 0
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
        if (t > -100 && t < 1200 && t != 0) cachedBatTemp = t / 10f
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) cachedBatPct = (level * 100) / scale
        val st = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        isChargingNow = (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
    }

    // Active sticky poll bypasses OriginOS / Android 14+ background BroadcastReceiver throttling
    private fun pollBatterySticky() {
        try {
            val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            updateBatteryFromIntent(sticky)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefsMgr = PrefsManager(this)
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

    private var lastKnownIsLand: Boolean? = null

    private fun isLandscapeMode(config: Configuration? = null): Boolean {
        if (config != null) {
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) return true
            if (config.orientation == Configuration.ORIENTATION_PORTRAIT) return false
        }
        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            try {
                @Suppress("DEPRECATION")
                val rot = wm.defaultDisplay?.rotation
                if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) return true
                if (rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180) return false
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val bounds = wm.currentWindowMetrics.bounds
                    if (bounds.width() > bounds.height()) return true
                    if (bounds.height() > bounds.width()) return false
                } catch (_: Exception) {}
            }

            try {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.getRealMetrics(dm)
                if (dm.widthPixels > dm.heightPixels) return true
                if (dm.heightPixels > dm.widthPixels) return false
            } catch (_: Exception) {}
        }
        val dm = resources.displayMetrics
        if (dm.widthPixels > dm.heightPixels) return true
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun getScreenDimensions(config: Configuration? = null): Pair<Int, Int> {
        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val dm = resources.displayMetrics
        var rawW = dm.widthPixels
        var rawH = dm.heightPixels

        if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val bounds = wm.currentWindowMetrics.bounds
                rawW = bounds.width()
                rawH = bounds.height()
            } catch (_: Exception) {}
        }

        val isLand = isLandscapeMode(config)
        val w = if (isLand) maxOf(rawW, rawH) else minOf(rawW, rawH)
        val h = if (isLand) minOf(rawW, rawH) else maxOf(rawW, rawH)
        return Pair(w, h)
    }

    private fun ensureOverlayWithinBounds(config: Configuration? = null) {
        val tv = overlayView ?: return
        val params = overlayParams ?: return
        val (screenW, screenH) = getScreenDimensions(config)
        val safeMargin = 32

        val specW = View.MeasureSpec.makeMeasureSpec((screenW - safeMargin).coerceAtLeast(100), View.MeasureSpec.AT_MOST)
        val specH = View.MeasureSpec.makeMeasureSpec((screenH - 16).coerceAtLeast(100), View.MeasureSpec.AT_MOST)
        tv.measure(specW, specH)
        val measuredW = tv.measuredWidth
        val measuredH = tv.measuredHeight

        var needsUpdate = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (params.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                needsUpdate = true
            }
        }

        // Only adjust params.x if using Gravity.START (custom position)
        if ((params.gravity and Gravity.CENTER_HORIZONTAL) != Gravity.CENTER_HORIZONTAL) {
            if (params.x + measuredW > screenW - 16) {
                val targetX = (screenW - measuredW - 16).coerceAtLeast(4)
                if (params.x != targetX) {
                    params.x = targetX
                    needsUpdate = true
                }
            }
            if (params.y + measuredH > screenH - 8) {
                val targetY = (screenH - measuredH - 8).coerceAtLeast(2)
                if (params.y != targetY) {
                    params.y = targetY
                    needsUpdate = true
                }
            }
        }

        if (params.width != WindowManager.LayoutParams.WRAP_CONTENT ||
            params.height != WindowManager.LayoutParams.WRAP_CONTENT) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            needsUpdate = true
        }

        if (needsUpdate) {
            tv.requestLayout()
            try {
                windowManager?.updateViewLayout(tv, params)
            } catch (_: Exception) {}
        }
    }

    private fun applyPositionForCurrentOrientation(config: Configuration? = null) {
        val params = overlayParams ?: return
        val isLand = isLandscapeMode(config)
        val (screenW, screenH) = getScreenDimensions(config)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val hasCustomLand = prefsMgr.hasCustomHudPosition(true)
        val layoutMode = prefsMgr.hudLayoutMode
        if (isLand && !hasCustomLand && layoutMode != 1) {
            // Default auto-center in landscape for Horizontal & Mini Dot:
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
            params.y = UiTheme.dp(this, 4)
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else if (layoutMode == 1 && !prefsMgr.hasCustomHudPosition(isLand)) {
            // Default Left Border Dock for Vertical Column (8dp margin per mockup spec)
            params.gravity = Gravity.TOP or Gravity.START
            params.x = UiTheme.dp(this, 8)
            params.y = UiTheme.dp(this, if (isLand) 24 else 96)
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            // Custom dragged or portrait:
            params.gravity = Gravity.TOP or Gravity.START
            val (rawX, rawY) = prefsMgr.getHudPosition(isLand)
            val tv = overlayView
            val specW = View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST)
            val specH = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
            tv?.measure(specW, specH)
            val viewW = tv?.measuredWidth?.takeIf { it > 0 } ?: (if (isLand) 400 else 200)
            val viewH = tv?.measuredHeight?.takeIf { it > 0 } ?: 60

            val maxX = (screenW - viewW - 16).coerceAtLeast(4)
            val maxY = (screenH - viewH - 8).coerceAtLeast(2)

            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = rawX.coerceIn(4, maxX)
            params.y = rawY.coerceIn(2, maxY)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayView != null && overlayParams != null) {
            lastRenderedText = ""
            applyOverlayStyle(newConfig)
            applyPositionForCurrentOrientation(newConfig)
            renderTelemetryText(newConfig)
            ensureOverlayWithinBounds(newConfig)
            overlayView?.requestLayout()
            try {
                windowManager?.updateViewLayout(overlayView, overlayParams)
            } catch (_: Exception) {}
        }
        telemetryThread?.interrupt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sizeLevel = prefsMgr.overlaySizeLevel
        hudScale = prefsMgr.hudScaleFloat
        bgMode = prefsMgr.overlayBgMode
        pollMode = prefsMgr.hudPollMode
        isVertical = prefsMgr.overlayVertical

        when (intent?.action) {
            "STOP_OVERLAY" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "RESET_POS" -> {
                prefsMgr.resetHudPosition()
                if (overlayView != null && overlayParams != null) {
                    applyPositionForCurrentOrientation()
                    try {
                        windowManager?.updateViewLayout(overlayView, overlayParams)
                    } catch (_: Exception) {}
                }
                return START_NOT_STICKY
            }
            "UPDATE_STYLE", "STYLE_CHANGED" -> {
                if (overlayView != null) {
                    sizeLevel = prefsMgr.overlaySizeLevel
                    hudScale = prefsMgr.hudScaleFloat
                    bgMode = prefsMgr.overlayBgMode
                    isVertical = (prefsMgr.hudLayoutMode == 1)
                    lastRenderedText = ""
                    lastRenderedColor = 0
                    tickCounter = 0
                    startAsForeground()
                    applyOverlayStyle()
                    renderTelemetryText()
                    ensureOverlayWithinBounds()
                    overlayView?.requestLayout()
                    try {
                        overlayParams?.width = WindowManager.LayoutParams.WRAP_CONTENT
                        overlayParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
                        windowManager?.updateViewLayout(overlayView, overlayParams)
                    } catch (_: Exception) {}
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
            tickCounter = 0
            applyOverlayStyle()
            applyPositionForCurrentOrientation()
            try {
                windowManager?.updateViewLayout(overlayView, overlayParams)
            } catch (_: Exception) {}
            renderTelemetryText()
        }

        isServiceRunning = true
        ShizukuHelper.ensureAppKeepAlive(packageName)
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

    private fun resolveEffectiveSessionInfo(): Triple<String, String, String> {
        val effPkg = targetPkg.ifBlank { detectedPkg }
        val effName = when {
            targetPkg.isNotBlank() -> gameName
            detectedAppName.isNotBlank() -> detectedAppName
            detectedPkg.isNotBlank() -> detectedPkg
            else -> "Screen Session"
        }
        val effScale = if (scaleLabel.isNotBlank()) {
            scaleLabel
        } else {
            val savedSc = if (effPkg.isNotBlank()) prefsMgr.getGameScale(effPkg) else 0
            if (savedSc > 0) "${savedSc / 100}.${savedSc % 100}x" else "Native"
        }
        return Triple(effPkg, effName, effScale)
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
                setTextColor(UiTheme.TEXT_WHITE)
                gravity = Gravity.CENTER
                visibility = View.GONE
                background = UiTheme.ovalBox(UiTheme.CLOSE_ZONE_IDLE, 3, UiTheme.ACCENT_RED_BRIGHT)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            windowManager?.addView(closeTv, closeParams)

            val tv = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            overlayParams = params
            applyPositionForCurrentOrientation()
            ensureOverlayWithinBounds()

            val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.coerceAtLeast(36)
            tv.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var downTimeMs = 0L
                private var isDragging = false
                private var wasCentered = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            wasCentered = (params.gravity and Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            downTimeMs = System.currentTimeMillis()
                            isDragging = false
                            isOverCloseZone = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            val dist = hypot(dx.toDouble(), dy.toDouble())

                            if (dist > touchSlop) {
                                if (!isDragging) {
                                    isDragging = true
                                    if (wasCentered) {
                                        val loc = IntArray(2)
                                        v.getLocationOnScreen(loc)
                                        params.gravity = Gravity.TOP or Gravity.START
                                        initialX = loc[0]
                                        initialY = loc[1]
                                        wasCentered = false
                                    }
                                    closeTargetView?.visibility = View.VISIBLE
                                }
                                params.x = initialX + dx
                                params.y = initialY + dy
                                windowManager?.updateViewLayout(overlayView, params)

                                val (sw, sh) = getScreenDimensions()
                                val targetCenterX = sw / 2f
                                val targetCenterY = sh - 90f - 68f
                                val distToZone = hypot(event.rawX - targetCenterX, event.rawY - targetCenterY)
                                val insideZone = distToZone < 145f

                                if (insideZone != isOverCloseZone) {
                                    isOverCloseZone = insideZone
                                    updateCloseTargetVisual(insideZone)
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            closeTargetView?.visibility = View.GONE
                            val totalDist = hypot((event.rawX - initialTouchX).toDouble(), (event.rawY - initialTouchY).toDouble())
                            val duration = System.currentTimeMillis() - downTimeMs
                            val isClick = (!isDragging || totalDist <= touchSlop * 1.5) && duration < 450L

                            if (isClick) {
                                try {
                                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                } catch (_: Exception) {}

                                if (!PerfLogManager.isRecording) {
                                    val (recPkg, recName, recScale) = resolveEffectiveSessionInfo()
                                    PerfLogManager.startSession(recPkg, recName, recScale)
                                    Toast.makeText(
                                        this@FpsOverlayService,
                                        Lang.str(this@FpsOverlayService, R.string.toast_rec_start, recName, recScale),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    val saved = PerfLogManager.stopAndSave(this@FpsOverlayService)
                                    val msg = if (saved != null) {
                                        Lang.str(this@FpsOverlayService, R.string.toast_rec_saved, saved.gameName, saved.avgFps, saved.avgWatts)
                                    } else {
                                        Lang.str(this@FpsOverlayService, R.string.toast_rec_short)
                                    }
                                    Toast.makeText(this@FpsOverlayService, msg, Toast.LENGTH_LONG).show()
                                }
                                lastRenderedText = ""
                                renderTelemetryText()
                            } else if (isDragging) {
                                if (isOverCloseZone) {
                                    Toast.makeText(this@FpsOverlayService, Lang.str(this@FpsOverlayService, R.string.toast_hud_closed), Toast.LENGTH_SHORT).show()
                                    stopSelf()
                                    return true
                                }
                                val (screenW, screenH) = getScreenDimensions()
                                val viewW = overlayView?.width ?: 0
                                val viewH = overlayView?.height ?: 0
                                val clampedX = params.x.coerceIn(4, (screenW - viewW - 16).coerceAtLeast(4))
                                val clampedY = params.y.coerceIn(2, (screenH - viewH - 8).coerceAtLeast(2))
                                params.gravity = Gravity.TOP or Gravity.START
                                params.x = clampedX
                                params.y = clampedY
                                try {
                                    windowManager?.updateViewLayout(overlayView, params)
                                } catch (_: Exception) {}
                                prefsMgr.saveHudPosition(isLandscapeMode(), params.x, params.y)
                            }
                            isDragging = false
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
            cView.background = UiTheme.ovalBox(UiTheme.ACCENT_RED_ALERT, 4, UiTheme.TEXT_WHITE)
        } else {
            cView.scaleX = 1.0f
            cView.scaleY = 1.0f
            cView.background = UiTheme.ovalBox(UiTheme.CLOSE_ZONE_IDLE, 3, UiTheme.ACCENT_RED_BRIGHT)
        }
    }

    private fun resolveAccentColor(): Int {
        return when (prefsMgr.hudColorAccent) {
            0 -> Color.parseColor("#D4F469") // Volt Lime
            1 -> Color.WHITE                 // White
            2 -> Color.parseColor("#38BDF8") // Blue / Cyan
            else -> Color.parseColor("#EEC14A") // Amber / Yellow
        }
    }

    private fun resolveBaseTextSizeSp(isLand: Boolean): Float {
        val s = hudScale.coerceIn(0.30f, 1.50f)
        val baseAt1x = if (isLand) 10.0f else 10.5f
        return (baseAt1x * s).coerceIn(3.5f, 16.0f)
    }

    private fun scaledPx(baseDpAt1x: Float, minPx: Int = 1): Int {
        val s = hudScale.coerceIn(0.30f, 1.50f)
        return UiTheme.dp(this, baseDpAt1x * s).toInt().coerceAtLeast(minPx)
    }

    private fun scaledDpF(baseDpAt1x: Float, minDp: Float = 2f): Float {
        val s = hudScale.coerceIn(0.30f, 1.50f)
        return (baseDpAt1x * s).coerceAtLeast(minDp)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun makeCircleDot(sizeDp: Float, color: Int): View {
        val px = scaledPx(sizeDp, minPx = 3)
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(px, px)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    private fun makeMonoText(
        text: CharSequence,
        textSizeSp: Float,
        color: Int,
        bold: Boolean = true
    ): TextView {
        val bIdx = bgMode.coerceIn(0, 3)
        return TextView(this).apply {
            this.text = text
            this.textSize = textSizeSp
            this.typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(color)
            includeFontPadding = false
            isSingleLine = true
            if (bIdx >= 2) {
                setShadowLayer(3.0f, 1.0f, 1.0f, UiTheme.TEXT_SHADOW)
            } else {
                setShadowLayer(1.2f, 0.4f, 0.4f, UiTheme.TEXT_SHADOW)
            }
        }
    }

    private fun buildRecPillView(textSizeSp: Float, elapsedSec: Int, compact: Boolean): View {
        val mm = elapsedSec / 60
        val ss = elapsedSec % 60
        val timeStr = String.format(Locale.US, "%02d:%02d", mm, ss)
        val dotAlpha = if (elapsedSec % 2 == 0) 1.0f else 0.35f

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = UiTheme.dp(this@FpsOverlayService, if (compact) 4 else 6)
            val pV = UiTheme.dp(this@FpsOverlayService, 2)
            setPadding(pH, pV, pH, pV)
            background = UiTheme.roundBoxDp(
                this@FpsOverlayService,
                UiTheme.HUD_REC_BG,
                6f,
                1f,
                UiTheme.HUD_REC_STROKE
            )

            val dot = makeCircleDot(5.5f, UiTheme.TECH_RED).apply { alpha = dotAlpha }
            addView(dot)

            val recTv = makeMonoText(if (compact) "REC" else "REC", textSizeSp * 0.9f, UiTheme.TECH_RED, true).apply {
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.marginStart = UiTheme.dp(this@FpsOverlayService, 4)
                layoutParams = lp
            }
            addView(recTv)

            if (!compact) {
                val timeTv = makeMonoText(timeStr, textSizeSp * 0.82f, UiTheme.HUD_SLATE_300, false).apply {
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = UiTheme.dp(this@FpsOverlayService, 4)
                    layoutParams = lp
                }
                addView(timeTv)
            }
        }
    }

    private fun buildHorizontalItemView(entry: HudMetricEntry, textSizeSp: Float, isConcise: Boolean): View {
        if (!isConcise && entry.isWarningPill) {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pH = UiTheme.dp(this@FpsOverlayService, 5)
                val pV = UiTheme.dp(this@FpsOverlayService, 1)
                setPadding(pH, pV, pH, pV)
                background = UiTheme.roundBoxDp(
                    this@FpsOverlayService,
                    UiTheme.HUD_REC_BG,
                    5f,
                    1f,
                    UiTheme.HUD_REC_STROKE
                )
                val labelText = if (entry.hLabel.isNotEmpty()) "⚠ ${entry.hLabel} ${entry.fullValue}" else "⚠ ${entry.fullValue}"
                addView(makeMonoText(labelText, textSizeSp * 0.96f, UiTheme.TECH_RED, true))
            }
        }

        val ssb = SpannableStringBuilder()
        if (isConcise) {
            ssb.append(entry.conciseValue)
            ssb.setSpan(ForegroundColorSpan(entry.valueColor), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (entry.isProminent) {
                ssb.setSpan(RelativeSizeSpan(1.10f), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else if (entry.isSuffixLabel && entry.hLabel.isNotEmpty()) {
            // e.g. "59.8 FPS" -> bold prominent Volt value + muted smaller "FPS"
            val valEnd = entry.fullValue.length
            ssb.append(entry.fullValue)
            ssb.setSpan(ForegroundColorSpan(entry.valueColor), 0, valEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (entry.isProminent) {
                ssb.setSpan(RelativeSizeSpan(1.10f), 0, valEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            ssb.append(" ")
            val lblStart = ssb.length
            ssb.append(entry.hLabel)
            ssb.setSpan(ForegroundColorSpan(UiTheme.HUD_LABEL_MUTED), lblStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(RelativeSizeSpan(0.82f), lblStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (entry.hLabel.isNotEmpty()) {
            // e.g. "Pin 36.2°C", "SoC 41.5°C", "GPU 65%", "RAM 4.2G"
            ssb.append(entry.hLabel)
            ssb.setSpan(ForegroundColorSpan(UiTheme.HUD_LABEL_MUTED), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(RelativeSizeSpan(0.84f), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.append(" ")
            val valStart = ssb.length
            ssb.append(entry.fullValue)
            ssb.setSpan(ForegroundColorSpan(entry.valueColor), valStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            // e.g. "4.8W", "16.7ms"
            ssb.append(entry.fullValue)
            ssb.setSpan(ForegroundColorSpan(entry.valueColor), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return makeMonoText(ssb, textSizeSp, entry.valueColor, true)
    }

    private fun applyOverlayStyle(config: Configuration? = null) {
        val root = overlayView ?: return
        val isLand = isLandscapeMode(config)
        val idx = sizeLevel.coerceIn(0, 4)
        val bIdx = bgMode.coerceIn(0, 3)
        val layoutMode = prefsMgr.hudLayoutMode // 0: Horizontal Pill, 1: Vertical Column, 2: Mini Dot
        val isConcise = prefsMgr.hudHideLabels
        val accent = resolveAccentColor()

        val maxChipTemp = maxOf(lastCpuTemp, lastGpuTemp, lastSocTemp)
        val guardRedLimit = if (prefsMgr.thermalGuardEnabled) prefsMgr.thermalGuardTempLimit.toFloat() else 43.0f
        val isOverheat = (thermalAlertState == 1) || (cachedBatTemp >= guardRedLimit) || (maxChipTemp >= 85.0f)
        val isRec = PerfLogManager.isRecording

        val strokeColor = when {
            bIdx == 3 -> Color.TRANSPARENT
            isOverheat -> Color.parseColor("#E6FF5252")
            isRec -> Color.parseColor("#B3FF5252")
            layoutMode == 1 && !isConcise -> Color.parseColor("#2EFFFFFF")
            else -> withAlpha(accent, if (bIdx == 2) 0x45 else 0x73)
        }

        val padH = scaledPx(if (isLand) 11f else 12f, minPx = 4)
        val padV = scaledPx(if (isLand) 5.5f else 6.5f, minPx = 2)

        when (layoutMode) {
            2 -> { // Mini Dot Capsule
                root.orientation = LinearLayout.HORIZONTAL
                root.gravity = Gravity.CENTER_VERTICAL
                root.setPadding(padH, padV, padH, padV)
                root.background = UiTheme.roundBoxDp(this, UiTheme.HUD_BG_OPACITY[bIdx], 999f, 1f, strokeColor)
            }
            1 -> { // Vertical Monospace Column (scaled corner radius)
                root.orientation = LinearLayout.VERTICAL
                root.gravity = if (isConcise) Gravity.CENTER_HORIZONTAL else Gravity.START
                val vPadH = scaledPx(if (isConcise) 8f else 11f, minPx = 4)
                val vPadV = scaledPx(8f, minPx = 3)
                root.setPadding(vPadH, vPadV, vPadH, vPadV)
                root.background = UiTheme.roundBoxDp(this, UiTheme.HUD_BG_OPACITY[bIdx], scaledDpF(14f, 6f), 1f, strokeColor)
            }
            else -> { // Horizontal HUD Pill (scaled corner radius)
                root.orientation = LinearLayout.VERTICAL
                root.gravity = if (isLand) Gravity.CENTER else Gravity.START
                root.setPadding(padH, padV, padH, padV)
                root.background = UiTheme.roundBoxDp(this, UiTheme.HUD_BG_OPACITY[bIdx], scaledDpF(12f, 5f), 1f, strokeColor)
            }
        }

        root.elevation = if (bIdx < 3) UiTheme.dpF(this, 6f) else 0f
    }

    private fun renderTelemetryText(config: Configuration? = null) {
        val root = overlayView ?: return
        val isLand = isLandscapeMode(config)
        if (lastKnownIsLand != null && lastKnownIsLand != isLand) {
            lastKnownIsLand = isLand
            lastRenderedText = ""
            applyOverlayStyle(config)
            applyPositionForCurrentOrientation(config)
        } else {
            lastKnownIsLand = isLand
        }

        val layoutMode = prefsMgr.hudLayoutMode
        val isConcise = prefsMgr.hudHideLabels
        val accent = resolveAccentColor()
        val textSizeSp = resolveBaseTextSizeSp(isLand)

        val showFps = prefsMgr.getMetric("m_fps", true)
        val showFrameTime = prefsMgr.getMetric("m_frametime", false)
        val showWatt = prefsMgr.getMetric("m_watt", true)
        val showGpu = prefsMgr.getMetric("m_gpu", true)
        val showVram = prefsMgr.getMetric("m_vram", false)
        val showCpuLoad = prefsMgr.getMetric("m_cpu_load", false)
        val showCpuFreq = prefsMgr.getMetric("m_cpu_freq", false)
        val showBatTemp = prefsMgr.getMetric("m_bat_temp", true)
        val showSocTemp = prefsMgr.getMetric("m_soc_temp", true)
        val showCpuTemp = prefsMgr.getMetric("m_cpu_temp", false)
        val showGpuTemp = prefsMgr.getMetric("m_gpu_temp", false)
        val showDdrTemp = prefsMgr.getMetric("m_ddr_temp", false)
        val showCamTemp = prefsMgr.getMetric("m_cam_temp", false)
        val showSkinTemp = prefsMgr.getMetric("m_skin_temp", false)
        val showRam = prefsMgr.getMetric("m_ram", false)

        val fpsVal = if (lastFps >= 0f) String.format(Locale.US, "%.1f", lastFps) else "--.-"
        val ftVal = if (lastFrameTimeMs > 0f) String.format(Locale.US, "%.1fms", lastFrameTimeMs) else "--ms"
        val prefix = if (isChargingNow) "+" else ""
        val wVal = if (lastWatts > 0f) String.format(Locale.US, "%s%.1fW", prefix, lastWatts) else if (cachedVoltMv > 0) "${(cachedVoltMv / 1000f).toInt()}V" else "--W"
        val hasValidGpu = showGpu && (lastGpuLoadPct in 0..100)
        val gpuVal = if (hasValidGpu) "${lastGpuLoadPct}%" else ""
        val hasValidVram = showVram && (lastGpuVramMb >= 0)
        val vramVal = if (lastGpuVramMb >= 0) "${lastGpuVramMb}MB" else "--MB"
        val vramConciseVal = if (lastGpuVramMb >= 0) "${lastGpuVramMb}M" else "--M"
        val hasValidCpuLoad = showCpuLoad && (lastCpuLoadPct in 0..100)
        val cpuLoadVal = if (hasValidCpuLoad) "${lastCpuLoadPct}%" else ""
        val cpuFreqVal = if (lastCpuKhz > 0) String.format(Locale.US, "%.2fGHz", lastCpuKhz / 1_000_000f) else "--GHz"
        val cpuFreqConciseVal = if (lastCpuKhz > 0) String.format(Locale.US, "%.1fG", lastCpuKhz / 1_000_000f) else "--G"

        val batVal = if (cachedBatTemp > 0f) String.format(Locale.US, "%.1f°C", cachedBatTemp) else "--°C"
        val socVal = if (lastSocTemp > 0f) String.format(Locale.US, "%.1f°C", lastSocTemp) else "--°C"
        val cpuTempVal = if (lastCpuTemp > 0f) String.format(Locale.US, "%.1f°C", lastCpuTemp) else "--°C"
        val gpuTempVal = if (lastGpuTemp > 0f) String.format(Locale.US, "%.1f°C", lastGpuTemp) else "--°C"
        val ddrTempVal = if (lastDdrTemp > 0f) String.format(Locale.US, "%.1f°C", lastDdrTemp) else "--°C"
        val camTempVal = if (lastCamTemp > 0f) String.format(Locale.US, "%.1f°C", lastCamTemp) else "--°C"
        val skinTempVal = if (lastSkinTemp > 0f) String.format(Locale.US, "%.1f°C", lastSkinTemp) else "--°C"

        val batConcise = if (cachedBatTemp > 0f) "${cachedBatTemp.toInt()}°" else "--°"
        val socConcise = if (lastSocTemp > 0f) "${lastSocTemp.toInt()}°" else "--°"
        val cpuTempConcise = if (lastCpuTemp > 0f) "${lastCpuTemp.toInt()}°" else "--°"
        val gpuTempConcise = if (lastGpuTemp > 0f) "${lastGpuTemp.toInt()}°" else "--°"
        val ddrTempConcise = if (lastDdrTemp > 0f) "${lastDdrTemp.toInt()}°" else "--°"
        val camTempConcise = if (lastCamTemp > 0f) "${lastCamTemp.toInt()}°" else "--°"
        val skinTempConcise = if (lastSkinTemp > 0f) "${lastSkinTemp.toInt()}°" else "--°"

        val ramVal = lastRamText.ifEmpty { "--%" }
        val ramConcise = lastRamText.replace("RAM:", "").replace("RAM", "").trim().ifEmpty { "--" }

        val guardRedLimit = if (prefsMgr.thermalGuardEnabled) prefsMgr.thermalGuardTempLimit.toFloat() else 43.0f
        val batHot = cachedBatTemp >= guardRedLimit
        val batWarm = cachedBatTemp >= (guardRedLimit - 2.0f)
        val batColor = when {
            batHot -> UiTheme.TECH_RED
            batWarm -> UiTheme.TECH_AMBER
            else -> UiTheme.TECH_EMERALD
        }
        fun chipTempColor(t: Float, normalColor: Int): Int = when {
            t >= 85.0f -> UiTheme.TECH_RED
            t >= 75.0f -> UiTheme.TECH_AMBER
            else -> normalColor
        }

        val maxChipTemp = maxOf(lastCpuTemp, lastGpuTemp, lastSocTemp)
        val isOverheat = (thermalAlertState == 1) || batHot || (maxChipTemp >= 85.0f)
        val isRec = PerfLogManager.isRecording
        val recSec = if (isRec) PerfLogManager.getElapsedSec() else 0

        // Build semantic metric list (ordering matches mockup for both full & concise)
        val entries = mutableListOf<HudMetricEntry>()
        if (showFps) {
            entries.add(HudMetricEntry("FPS", "FPS", isSuffixLabel = true, fullValue = fpsVal, conciseValue = fpsVal, valueColor = accent, isProminent = true))
        }
        if (showFrameTime) {
            entries.add(HudMetricEntry("FT", "", fullValue = ftVal, conciseValue = ftVal, valueColor = UiTheme.HUD_SLATE_300))
        }
        if (isConcise && layoutMode == 0) {
            // Mockup 1B concise horizontal order: FPS · W · GPU% · Bat° · SoC° · RAM
            if (showWatt) entries.add(HudMetricEntry("PWR", "", fullValue = wVal, conciseValue = wVal, valueColor = UiTheme.TECH_AMBER))
            if (hasValidCpuLoad) entries.add(HudMetricEntry("CPU%", "CPU", fullValue = cpuLoadVal, conciseValue = cpuLoadVal, valueColor = UiTheme.TECH_BLUE))
            if (hasValidGpu) entries.add(HudMetricEntry("GPU", "GPU", fullValue = gpuVal, conciseValue = gpuVal, valueColor = UiTheme.HUD_CYAN))
            if (hasValidVram) entries.add(HudMetricEntry("VRM", "VRAM", fullValue = vramVal, conciseValue = vramConciseVal, valueColor = UiTheme.HUD_SLATE_300))
            if (showCpuFreq) entries.add(HudMetricEntry("CLK", "", fullValue = cpuFreqVal, conciseValue = cpuFreqConciseVal, valueColor = UiTheme.TECH_BLUE))
            if (showBatTemp) entries.add(HudMetricEntry("PIN", "Pin", fullValue = batVal, conciseValue = batConcise, valueColor = batColor, isWarningPill = batHot))
            if (showSocTemp) entries.add(HudMetricEntry("SOC", "SoC", fullValue = socVal, conciseValue = socConcise, valueColor = chipTempColor(lastSocTemp, accent)))
            if (showCpuTemp) entries.add(HudMetricEntry("CPU", "CPU", fullValue = cpuTempVal, conciseValue = cpuTempConcise, valueColor = chipTempColor(lastCpuTemp, accent)))
            if (showGpuTemp) entries.add(HudMetricEntry("GPUT", "GPU", fullValue = gpuTempVal, conciseValue = gpuTempConcise, valueColor = chipTempColor(lastGpuTemp, UiTheme.HUD_CYAN)))
            if (showDdrTemp) entries.add(HudMetricEntry("DDR", "DDR", fullValue = ddrTempVal, conciseValue = ddrTempConcise, valueColor = chipTempColor(lastDdrTemp, UiTheme.HUD_SLATE_300)))
            if (showCamTemp) entries.add(HudMetricEntry("CAM", "CAM", fullValue = camTempVal, conciseValue = camTempConcise, valueColor = chipTempColor(lastCamTemp, UiTheme.HUD_SLATE_300)))
            if (showSkinTemp) entries.add(HudMetricEntry("SKN", "SKN", fullValue = skinTempVal, conciseValue = skinTempConcise, valueColor = chipTempColor(lastSkinTemp, UiTheme.TECH_EMERALD)))
            if (showRam) entries.add(HudMetricEntry("RAM", "RAM", fullValue = ramVal, conciseValue = ramConcise, valueColor = Color.WHITE))
        } else {
            // Mockup 1A / 2A / 2B order: FPS, Pin, SoC, ..., PWR, GPU, VRAM, CLK, RAM
            if (showBatTemp) entries.add(HudMetricEntry("PIN", "Pin", fullValue = batVal, conciseValue = batConcise, valueColor = batColor, isWarningPill = batHot))
            if (showSocTemp) entries.add(HudMetricEntry("SOC", "SoC", fullValue = socVal, conciseValue = socConcise, valueColor = chipTempColor(lastSocTemp, accent)))
            if (showCpuTemp) entries.add(HudMetricEntry("CPU", "CPU", fullValue = cpuTempVal, conciseValue = cpuTempConcise, valueColor = chipTempColor(lastCpuTemp, accent)))
            if (showGpuTemp) entries.add(HudMetricEntry("GPUT", "GPU", fullValue = gpuTempVal, conciseValue = gpuTempConcise, valueColor = chipTempColor(lastGpuTemp, UiTheme.HUD_CYAN)))
            if (showDdrTemp) entries.add(HudMetricEntry("DDR", "DDR", fullValue = ddrTempVal, conciseValue = ddrTempConcise, valueColor = chipTempColor(lastDdrTemp, UiTheme.HUD_SLATE_300)))
            if (showCamTemp) entries.add(HudMetricEntry("CAM", "CAM", fullValue = camTempVal, conciseValue = camTempConcise, valueColor = chipTempColor(lastCamTemp, UiTheme.HUD_SLATE_300)))
            if (showSkinTemp) entries.add(HudMetricEntry("SKN", "SKN", fullValue = skinTempVal, conciseValue = skinTempConcise, valueColor = chipTempColor(lastSkinTemp, UiTheme.TECH_EMERALD)))
            if (showWatt) entries.add(HudMetricEntry("PWR", "", fullValue = wVal, conciseValue = wVal, valueColor = UiTheme.TECH_AMBER))
            if (hasValidCpuLoad) entries.add(HudMetricEntry("CPU%", "CPU", fullValue = cpuLoadVal, conciseValue = cpuLoadVal, valueColor = UiTheme.TECH_BLUE))
            if (hasValidGpu) entries.add(HudMetricEntry("GPU", "GPU", fullValue = gpuVal, conciseValue = gpuVal, valueColor = UiTheme.HUD_CYAN))
            if (hasValidVram) entries.add(HudMetricEntry("VRM", "VRAM", fullValue = vramVal, conciseValue = vramConciseVal, valueColor = UiTheme.HUD_SLATE_300))
            if (showCpuFreq) entries.add(HudMetricEntry("CLK", "", fullValue = cpuFreqVal, conciseValue = cpuFreqConciseVal, valueColor = UiTheme.TECH_BLUE))
            if (showRam) entries.add(HudMetricEntry("RAM", "RAM", fullValue = ramVal, conciseValue = ramConcise, valueColor = Color.WHITE))
        }
        if (entries.isEmpty()) {
            entries.add(HudMetricEntry("FPS", "FPS", isSuffixLabel = true, fullValue = fpsVal, conciseValue = fpsVal, valueColor = accent, isProminent = true))
        }

        val scaleInt = (hudScale * 100f).toInt()
        // Compute compact signature to avoid unnecessary view rebuilds
        val sig = buildString(128) {
            append(layoutMode).append('|').append(isConcise).append('|').append(isLand).append('|')
            append(scaleInt).append('|').append(bgMode).append('|').append(accent).append('|')
            append(isRec).append(':').append(recSec).append('|').append(isOverheat).append('|').append(thermalAlertState).append('|')
            for (e in entries) {
                append(e.vLabel).append('=').append(if (isConcise) e.conciseValue else e.fullValue).append(':').append(e.valueColor).append(';')
            }
        }
        if (sig == lastRenderedText) return
        lastRenderedText = sig

        // Update border/alert tint if state or scale changed
        val stateCode = (if (isOverheat) 2 else if (isRec) 1 else 0) + (layoutMode * 10) + (if (isConcise) 100 else 0) + (scaleInt * 1000)
        if (stateCode != lastRenderedColor) {
            lastRenderedColor = stateCode
            applyOverlayStyle(config)
        }

        root.removeAllViews()

        when (layoutMode) {
            2 -> { // Mini Dot Capsule
                val dotColor = when {
                    isOverheat || isRec -> UiTheme.TECH_RED
                    thermalAlertState == 2 -> UiTheme.TECH_AMBER
                    else -> accent
                }
                root.addView(makeCircleDot(6f, dotColor))
                val label = when {
                    isOverheat -> "HOT $fpsVal"
                    thermalAlertState == 2 -> "LOW $fpsVal"
                    else -> fpsVal
                }
                val tv = makeMonoText(label, textSizeSp * 1.08f, if (isOverheat) UiTheme.TECH_RED else accent, true).apply {
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = UiTheme.dp(this@FpsOverlayService, 6)
                    layoutParams = lp
                }
                root.addView(tv)
            }

            1 -> { // Vertical Monospace Column (Variant 2A Labeled / Variant 2B Ultra-Compact)
                val rowGapPx = scaledPx(3f, minPx = 1)
                if (isConcise) {
                    // Variant 2B: Narrow centered column with top dot + prominent FPS + divider + values
                    val dotColor = if (isOverheat || isRec) UiTheme.TECH_RED else accent
                    val topDot = makeCircleDot(5.5f, dotColor).apply {
                        val lp = layoutParams as LinearLayout.LayoutParams
                        lp.bottomMargin = scaledPx(3.5f, minPx = 1)
                        if (isRec) alpha = if (recSec % 2 == 0) 1.0f else 0.35f
                    }
                    root.addView(topDot)

                    if (isRec) {
                        val recTv = makeMonoText("REC", textSizeSp * 0.78f, UiTheme.TECH_RED, true).apply {
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            lp.bottomMargin = scaledPx(2f, minPx = 1)
                            layoutParams = lp
                        }
                        root.addView(recTv)
                    }

                    entries.forEachIndexed { idxEntry, entry ->
                        val itemSize = if (idxEntry == 0 && entry.isProminent) textSizeSp * 1.14f else textSizeSp * 0.96f
                        val tv = makeMonoText(entry.conciseValue, itemSize, entry.valueColor, true).apply {
                            gravity = Gravity.CENTER
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            if (idxEntry > 0) lp.topMargin = rowGapPx
                            layoutParams = lp
                        }
                        root.addView(tv)

                        // Subtle divider right under FPS
                        if (idxEntry == 0 && entries.size > 1) {
                            val conciseDivW = scaledPx(38f, minPx = 14)
                            val div = View(this).apply {
                                val lp = LinearLayout.LayoutParams(conciseDivW, 1)
                                lp.topMargin = scaledPx(3f, minPx = 1)
                                lp.bottomMargin = scaledPx(1f, minPx = 1)
                                layoutParams = lp
                                setBackgroundColor(UiTheme.HUD_DIVIDER_LIGHT)
                            }
                            root.addView(div)
                        }
                    }
                } else {
                    // Variant 2A: Labeled Vertical Card — Fixed-width label column + tight gap + crisp aligned values
                    val labelColWidthPx = scaledPx(32f, minPx = 12)
                    val colGapPx = scaledPx(9f, minPx = 3)
                    val divWidthPx = scaledPx(86f, minPx = 30)

                    val headerRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val dotColor = if (isOverheat || isRec) UiTheme.TECH_RED else accent
                    headerRow.addView(makeCircleDot(5f, dotColor).apply {
                        if (isRec) alpha = if (recSec % 2 == 0) 1.0f else 0.35f
                    })
                    val headerLabel = when {
                        isRec -> {
                            val mm = recSec / 60
                            val ss = recSec % 60
                            String.format(Locale.US, "REC %02d:%02d", mm, ss)
                        }
                        isOverheat -> "HOT ALERT"
                        scaleLabel.isNotBlank() -> "HUD ${scaleLabel.uppercase(Locale.US)}"
                        else -> "HUD PRO"
                    }
                    val headerTv = makeMonoText(
                        headerLabel,
                        textSizeSp * 0.78f,
                        if (isRec || isOverheat) UiTheme.TECH_RED else UiTheme.HUD_LABEL_MUTED,
                        true
                    ).apply {
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp.marginStart = scaledPx(4.5f, minPx = 2)
                        lp.marginEnd = scaledPx(7f, minPx = 3)
                        layoutParams = lp
                    }
                    headerRow.addView(headerTv)
                    headerRow.addView(makeMonoText("••", textSizeSp * 0.75f, UiTheme.HUD_DOT_SEPARATOR, true))
                    root.addView(headerRow)

                    // Hairline divider
                    val divider = View(this).apply {
                        val lp = LinearLayout.LayoutParams(divWidthPx, 1)
                        lp.topMargin = scaledPx(3.5f, minPx = 1)
                        lp.bottomMargin = scaledPx(3.5f, minPx = 1)
                        layoutParams = lp
                        setBackgroundColor(UiTheme.HUD_DIVIDER_LIGHT)
                    }
                    root.addView(divider)

                    entries.forEachIndexed { idxEntry, entry ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            if (idxEntry > 0) lp.topMargin = rowGapPx
                            layoutParams = lp
                        }
                        val lblTv = makeMonoText(entry.vLabel, textSizeSp * 0.90f, UiTheme.HUD_LABEL_MUTED, true).apply {
                            gravity = Gravity.START
                            val lp = LinearLayout.LayoutParams(labelColWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
                            lp.marginEnd = colGapPx
                            layoutParams = lp
                        }
                        val valText = if (entry.isWarningPill) "⚠ ${entry.fullValue}" else entry.fullValue
                        val valSize = if (entry.isProminent) textSizeSp * 1.10f else textSizeSp * 0.96f
                        val valTv = makeMonoText(valText, valSize, entry.valueColor, true).apply {
                            gravity = Gravity.START
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        row.addView(lblTv)
                        row.addView(valTv)
                        root.addView(row)
                    }
                }
            }

            else -> { // Horizontal HUD Pill (Variant 1A Full / 1B Concise / 1C REC / 1D Thermal Alert)
                val sepChar = if (isConcise) "·" else "|"
                val sepColor = if (isConcise) UiTheme.HUD_DOT_SEPARATOR else UiTheme.HUD_SEPARATOR
                val sepMarginPx = scaledPx(if (isConcise) 4.5f else 6.5f, minPx = 2)

                val itemViews = mutableListOf<View>()
                if (isRec) {
                    itemViews.add(buildRecPillView(textSizeSp, recSec, compact = isConcise))
                }
                if (thermalAlertState == 2) {
                    itemViews.add(makeMonoText("🪫 LOW", textSizeSp * 0.92f, UiTheme.TECH_AMBER, true))
                }
                for (entry in entries) {
                    itemViews.add(buildHorizontalItemView(entry, textSizeSp, isConcise))
                }

                val rows: List<List<View>> = if (isLand) {
                    listOf(itemViews)
                } else {
                    val perRow = if (isConcise) 4 else 3
                    itemViews.chunked(perRow)
                }

                rows.forEachIndexed { rowIdx, rowItems ->
                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        if (rowIdx > 0) lp.topMargin = UiTheme.dp(this@FpsOverlayService, 3)
                        layoutParams = lp
                    }
                    rowItems.forEachIndexed { colIdx, child ->
                        if (colIdx > 0) {
                            val sepTv = makeMonoText(sepChar, textSizeSp * 0.92f, sepColor, true).apply {
                                val lp = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                lp.marginStart = sepMarginPx
                                lp.marginEnd = sepMarginPx
                                layoutParams = lp
                            }
                            rowLayout.addView(sepTv)
                        }
                        rowLayout.addView(child)
                    }
                    root.addView(rowLayout)
                }
            }
        }

        ensureOverlayWithinBounds(config)
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
                val cellMultiplier = if (prefsMgr.dualCellBattery) 2.0 else 1.0
                val instWatts = (amps * volts * cellMultiplier).toFloat()
                if (instWatts in 0.1f..240f) {
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

            val proc = ShizukuHelper.newShellProcess(arrayOf("sh"))
            // Drain stderr on a daemon thread so the 64KB kernel pipe can NEVER fill up & stall after ~5 minutes
            thread(isDaemon = true, name = "HudShStderrDrain") {
                try {
                    val buf = ByteArray(1024)
                    val err = proc.errorStream
                    while (err.read(buf) != -1) { /* discard */ }
                } catch (_: Exception) {}
            }
            val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8), 2048)
            val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8), 4096)

            val initMarker = "___HUD_INIT_${System.nanoTime()}___"
            val initCmd = "exec 2>/dev/null; " +
                    "_rdz() { for f in \$1; do v=\"\"; read -r v < \"\$f\" 2>/dev/null; [ -n \"\$v\" ] && printf \"%s \" \"\$v\"; done; printf \"\\n\"; }; " +
                    "CPU_ZONES=\"\"; GPU_ZONES=\"\"; DDR_ZONES=\"\"; CAM_ZONES=\"\"; SKIN_ZONES=\"\"; BAT_NODE=\"\"; " +
                    "for bf in /sys/class/power_supply/battery/temp /sys/class/power_supply/bms/temp; do if [ -r \"\$bf\" ]; then BAT_NODE=\"\$bf\"; break; fi; done; " +
                    "for z in /sys/class/thermal/thermal_zone*; do " +
                    "[ -r \"\$z/temp\" ] || continue; " +
                    "t=\"\"; read -r t < \"\$z/type\" 2>/dev/null; [ -n \"\$t\" ] || continue; " +
                    "case \"\$t\" in " +
                    "*[Ss][Tt][Ee][Pp]*|*[Bb][Cc][Ll]*|*[Tt][Rr][Ii][Pp]*|*[Ll][Ii][Mm][Ii][Tt]*|*[Mm][Aa][Xx]*|*[Ss][Oo][Cc][Dd]*|*[Vv][Ii][Rr][Tt][Uu][Aa][Ll]*|*[Tt][Hh][Rr][Ee][Ss][Hh]*|*[Aa][Dd][Cc]*|*[Cc][Aa][Pp]*|*[Nn][Pp][Uu]*|*[Nn][Ss][Pp][Ss][Ss]*|*[Mm][Oo][Dd][Ee][Mm]*|*[Pp][Aa]*|*[Bb][Aa][Tt]*|*[Ww][Ll][Aa][Nn]*|*[Uu][Ss][Bb]*|*[Ff][Ll][Aa][Ss][Hh]*|*[Cc][Hh][Gg]*) ;; " +
                    "*[Gg][Pp][Uu]*|*[Kk][Gg][Ss][Ll]*|*[Mm][Aa][Ll][Ii]*) GPU_ZONES=\"\$GPU_ZONES \$z/temp\" ;; " +
                    "*[Dd][Dd][Rr]*|*[Ll][Ll][Cc][Cc]*|*[Dd][Rr][Aa][Mm]*|*[Ll][Pp][Dd][Dd][Rr]*) DDR_ZONES=\"\$DDR_ZONES \$z/temp\" ;; " +
                    "*[Cc][Aa][Mm]*|*[Oo][Ii][Ss]*|*[Aa][Cc][Tt][Uu][Aa][Tt][Oo][Rr]*|*[Tt][Oo][Ff]*) CAM_ZONES=\"\$CAM_ZONES \$z/temp\" ;; " +
                    "*[Ss][Kk][Ii][Nn]*|*[Qq][Uu][Ii][Ee][Tt]*|*[Xx][Oo]-[Tt][Hh][Ee][Rr][Mm]*|*[Ss][Yy][Ss]-[Tt][Hh][Ee][Rr][Mm]*|*[Bb][Oo][Aa][Rr][Dd]*|*[Mm][Ss][Mm]-[Tt][Hh][Ee][Rr][Mm]*|*[Ss][Hh][Ee][Ll][Ll]*|*[Ss][Uu][Bb]-[Tt][Hh][Ee][Rr][Mm]*|*[Mm][Aa][Ii][Nn]-[Tt][Hh][Ee][Rr][Mm]*|*[Vv][Ii][Vv][Oo]*) SKIN_ZONES=\"\$SKIN_ZONES \$z/temp\" ;; " +
                    "*[Cc][Pp][Uu]*|*[Ss][Oo][Cc]*|*[Aa][Pp][Cc]*|*[Tt][Ss][Ee][Nn][Ss]*|*[Cc][Oo][Rr][Ee]*|*[Cc][Ll][Uu][Ss][Tt][Ee][Rr]*|*[Ss][Ii][Ll][Vv][Ee][Rr]*|*[Gg][Oo][Ll][Dd]*|*[Pp][Rr][Ii][Mm][Ee]*|*[Tt][Ii][Tt][Aa][Nn][Ii][Uu][Mm]*|*[Kk][Rr][Yy][Oo]*|*[Oo][Rr][Yy][Oo][Nn]*) CPU_ZONES=\"\$CPU_ZONES \$z/temp\" ;; " +
                    "esac; done; " +
                    "FREQ_FILES=\"\"; for f in /sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq; do [ -r \"\$f\" ] && FREQ_FILES=\"\$FREQ_FILES \$f\"; done; " +
                    "DRM_NODE=\"\"; for f in /sys/class/drm/sde-crtc-0/measured_fps /sys/class/drm/card0/sde-crtc-0/measured_fps; do if [ -r \"\$f\" ]; then DRM_NODE=\"\$f\"; break; fi; done; " +
                    "printf \"$initMarker\\n\"\n"
            writer.write(initCmd)
            writer.flush()

            while (true) {
                val line = reader.readLine() ?: break
                if (line == initMarker) break
            }

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
            1 -> 150L
            2 -> 300L
            3 -> 1000L
            else -> when {
                isIdleMode -> 1200L
                targetPkg.isNotEmpty() || detectedPkg.isNotEmpty() || isLandscapeMode() || lastFps > 1f -> 250L
                else -> 500L
            }
        }
    }

    private fun isSystemOrLauncherPkg(pkg: String): Boolean {
        return pkg.isBlank() ||
                pkg == packageName ||
                pkg == "com.android.systemui" ||
                pkg.contains("launcher") ||
                pkg.contains("permissioncontroller") ||
                pkg.contains("gamecube") ||
                pkg.contains("inputmethod") ||
                pkg == "com.android.settings"
    }

    private fun resolveAppName(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString().ifBlank { pkg }
        } catch (_: Exception) {
            pkg
        }
    }

    private fun startTelemetryLoop() {
        telemetryThread = thread(name = "HudTelemetryLoop") {
            var graceTicks = 0
            val sb = StringBuilder(512)
            var last1sTickTime = 0L
            var last3sTickTime = 0L

            while (isRunning) {
                if (!isScreenOn) {
                    try { Thread.sleep(1500) } catch (_: InterruptedException) {}
                    continue
                }

                try {
                    tickCounter++
                    val nowMs = SystemClock.uptimeMillis()
                    val is1sTick = (nowMs - last1sTickTime >= 1000L)
                    val is3sTick = (nowMs - last3sTickTime >= 3000L)
                    if (is1sTick) last1sTickTime = nowMs
                    if (is3sTick) last3sTickTime = nowMs

                    val activePkg = targetPkg.ifBlank { detectedPkg }
                    val showFps = prefsMgr.getMetric("m_fps", true)
                    val showFrameTime = prefsMgr.getMetric("m_frametime", false)
                    val needFrameStats = showFps || showFrameTime
                    val showWatt = prefsMgr.getMetric("m_watt", true)
                    val showGpu = prefsMgr.getMetric("m_gpu", true)
                    val showVram = prefsMgr.getMetric("m_vram", false)
                    val showCpuFreq = prefsMgr.getMetric("m_cpu_freq", false)
                    val showSocTemp = prefsMgr.getMetric("m_soc_temp", true)
                    val showCpuTemp = prefsMgr.getMetric("m_cpu_temp", false)
                    val showGpuTemp = prefsMgr.getMetric("m_gpu_temp", false)
                    val showDdrTemp = prefsMgr.getMetric("m_ddr_temp", false)
                    val showCamTemp = prefsMgr.getMetric("m_cam_temp", false)
                    val showSkinTemp = prefsMgr.getMetric("m_skin_temp", false)
                    val showRam = prefsMgr.getMetric("m_ram", false)
                    val showCpuLoad = prefsMgr.getMetric("m_cpu_load", false)

                    // Chu kỳ nhanh (150ms-300ms, mỗi tick): Đo công suất điện tiêu thụ tức thời
                    if (showWatt) {
                        readInstantWattage()
                    }
                    // Chủ động poll pin mỗi 1s qua sticky intent để không bị OriginOS/Android 14+ bóp BroadcastReceiver
                    if (is1sTick) {
                        pollBatterySticky()
                    }

                    sb.setLength(0)

                    // Chu kỳ nhanh: Đọc trực tiếp mức tải GPU (%) từ đa node sysfs (Adreno + MediaTek + Mali)
                    if (showGpu) {
                        sb.append("gload=-1; if [ -r /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage ]; then read -r gload < /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null; elif [ -r /sys/module/ged/parameters/gpu_loading ]; then read -r gload < /sys/module/ged/parameters/gpu_loading 2>/dev/null; elif [ -r /sys/class/misc/mali0/device/utilization ]; then read -r gload < /sys/class/misc/mali0/device/utilization 2>/dev/null; fi; printf \"GPU_LOAD=%s\\n\" \"\$gload\"; ")
                    }

                    // Chu kỳ trung bình (1 giây / 1000ms): Kiểm tra PID tiến trình, tải CPU (/proc/stat), xung nhịp CPU và nhiệt độ phần cứng
                    if (targetPkg.isNotEmpty() && is1sTick) {
                        sb.append("printf \"PID=%s\\n\" \"\$(pidof ").append(targetPkg).append(" 2>/dev/null)\"; ")
                    } else if (detectedPkg.isNotEmpty() && is1sTick) {
                        sb.append("printf \"DPID=%s\\n\" \"\$(pidof ").append(detectedPkg).append(" 2>/dev/null)\"; ")
                    }
                    if (showCpuLoad && is1sTick) {
                        sb.append("read -r cstat < /proc/stat 2>/dev/null && printf \"CPU_STAT=%s\\n\" \"\$cstat\"; ")
                    }
                    if (showCpuFreq && is1sTick) {
                        sb.append("printf \"CPU_KHZ=\"; _rdz \"\$FREQ_FILES\"; ")
                    }
                    if (is1sTick) {
                        sb.append("if [ -n \"\$BAT_NODE\" ]; then bv=\"\"; read -r bv < \"\$BAT_NODE\" 2>/dev/null; [ -n \"\$bv\" ] && printf \"BAT_T=%s\\n\" \"\$bv\"; fi; ")
                    }
                    val wantChipThermal = (showCpuTemp || showSocTemp || showGpuTemp || showDdrTemp || showCamTemp || showSkinTemp)
                    if ((showCpuTemp || showSocTemp) && is1sTick) {
                        sb.append("printf \"CPU_T=\"; _rdz \"\$CPU_ZONES\"; ")
                    }
                    if ((showGpuTemp || showSocTemp) && is1sTick) {
                        sb.append("printf \"GPU_T=\"; _rdz \"\$GPU_ZONES\"; ")
                    }
                    if (showDdrTemp && is1sTick) {
                        sb.append("printf \"DDR_T=\"; _rdz \"\$DDR_ZONES\"; ")
                    }
                    if (showCamTemp && is1sTick) {
                        sb.append("printf \"CAM_T=\"; _rdz \"\$CAM_ZONES\"; ")
                    }
                    if (showSkinTemp && is1sTick) {
                        sb.append("printf \"SKIN_T=\"; _rdz \"\$SKIN_ZONES\"; ")
                    }
                    if (wantChipThermal && is1sTick) {
                        // Fallback đọc trực tiếp từ Android Thermal HAL nếu sysfs thermal_zone trống hoặc bị khóa
                        sb.append("if [ -z \"\$CPU_ZONES\" ] && [ -z \"\$GPU_ZONES\" ]; then dumpsys thermalservice 2>/dev/null | grep 'Temperature{'; fi; ")
                    }

                    // Chu kỳ chậm (3 giây / 3000ms): Dò app foreground, VRAM và RAM siêu nhẹ qua /proc (không dùng dumpsys meminfo gây khựng)
                    if (targetPkg.isEmpty() && (is3sTick || detectedPkg.isEmpty())) {
                        sb.append("printf \"FG_ACT=%s\\n\" \"\$(dumpsys activity activities 2>/dev/null | grep -m 1 -E 'topResumedActivity|mResumedActivity')\"; ")
                    }
                    if (showVram && (is3sTick || lastGpuVramMb < 0)) {
                        sb.append("dumpsys gpu 2>/dev/null | grep -E 'Global total|Proc '; ")
                    }
                    if (showRam && (is3sTick || lastRamText.isEmpty())) {
                        if (activePkg.isNotEmpty()) {
                            sb.append("p1=\$(pidof ").append(activePkg).append(" 2>/dev/null | awk '{print \$1}'); if [ -n \"\$p1\" ] && [ -r \"/proc/\$p1/status\" ]; then grep -m 1 '^VmRSS:' \"/proc/\$p1/status\" 2>/dev/null; fi; ")
                        }
                        sb.append("{ read -r m1; read -r m2; read -r m3; printf \"%s\\n%s\\n%s\\n\" \"\$m1\" \"\$m2\" \"\$m3\"; } < /proc/meminfo; ")
                    }

                    // Chu kỳ nhanh: SurfaceFlinger Timestats và DRM FPS node
                    if (needFrameStats) {
                        if (initializedStatsForPkg != activePkg) {
                            sb.append("dumpsys SurfaceFlinger --timestats -clear -enable >/dev/null 2>&1; ")
                            initializedStatsForPkg = activePkg
                        }
                        sb.append("if [ -n \"\$DRM_NODE\" ] && [ ").append(frozenDrmTicks).append(" -lt 4 ]; then read -r dv < \"\$DRM_NODE\" 2>/dev/null && printf \"DRM_FPS=%s\\n\" \"\$dv\"; fi; ")
                        sb.append("printf \"===TIMESTATS===\\n\"; ")
                        sb.append("dumpsys SurfaceFlinger --timestats -dump; ")
                    }

                    if (sb.isNotEmpty()) {
                        val rawOut = execPersistentShizuku(sb.toString())
                        val splitIdx = rawOut.indexOf("===TIMESTATS===")
                        val batchSection = if (splitIdx >= 0) rawOut.substring(0, splitIdx) else rawOut
                        val timeStatsSection = if (splitIdx >= 0) rawOut.substring(splitIdx + 15) else ""

                        val (activePids, drmFpsFallback) = parseBatchOutput(batchSection)

                        if (targetPkg.isNotEmpty() && batchSection.contains("PID=")) {
                            if (activePids.isEmpty()) {
                                graceTicks++
                                if (graceTicks > 5) {
                                    mainHandler.post { stopSelf() }
                                    break
                                }
                            } else {
                                graceTicks = 0
                            }
                        }

                        if (needFrameStats) {
                            val currentMeasurePkg = targetPkg.ifBlank { detectedPkg }
                            lastFps = parseRealFps(currentMeasurePkg, timeStatsSection, drmFpsFallback)
                            // Rolling 8-sample frame-time window (Scene recent_frames = 6~12)
                            if (lastFps > 1f) {
                                val instFt = (1000f / lastFps).coerceIn(4f, 500f)
                                if (frameTimeWindow.size >= 8) frameTimeWindow.removeFirst()
                                frameTimeWindow.addLast(instFt)
                                lastFrameTimeMs = frameTimeWindow.average().toFloat()
                            } else if (lastFps <= 0f) {
                                frameTimeWindow.clear()
                                lastFrameTimeMs = -1f
                            }
                        }
                    }

                    // Idle Detection (Scene idle_delay = 5s): slow polling when screen is static & not gaming
                    val staticIdleCandidate = (pollMode == 0 && targetPkg.isEmpty() && !PerfLogManager.isRecording && !isLandscapeMode() && lastFps <= 2f)
                    if (staticIdleCandidate) {
                        if (idleStartTimeMs == 0L) {
                            idleStartTimeMs = nowMs
                        } else if (nowMs - idleStartTimeMs >= 5000L) {
                            isIdleMode = true
                        }
                    } else {
                        idleStartTimeMs = 0L
                        isIdleMode = false
                    }

                    // Thermal Guard Rules (evaluated every 1s)
                    if (is1sTick) {
                        evaluateThermalGuard(nowMs)
                    }

                    if (PerfLogManager.isRecording && is1sTick) {
                        PerfLogManager.addSample(lastFps, lastWatts, cachedBatTemp, lastSocTemp, lastGpuVramMb, lastCpuKhz)
                    }
                    mainHandler.post { renderTelemetryText() }
                } catch (_: Exception) {}

                try {
                    Thread.sleep(computeNextIntervalMs())
                } catch (_: InterruptedException) {}
            }
        }
    }

    private fun evaluateThermalGuard(nowMs: Long) {
        if (!prefsMgr.thermalGuardEnabled) {
            thermalAlertState = 0
            return
        }
        val tempLimit = prefsMgr.thermalGuardTempLimit.toFloat()
        val batMin = prefsMgr.thermalGuardBatMin
        val isOverheat = cachedBatTemp >= tempLimit
        val isLowBat = cachedBatPct in 1..batMin && !isChargingNow

        thermalAlertState = when {
            isOverheat -> 1
            isLowBat -> 2
            else -> 0
        }

        if (thermalAlertState != 0) {
            val shouldAutoReset = prefsMgr.thermalGuardAutoReset && !guardAutoResetDone && (targetPkg.isNotEmpty() || scaleLabel != "Native")
            if (shouldAutoReset) {
                guardAutoResetDone = true
                ShizukuHelper.resetDisplay()
                scaleLabel = "Native"
            }

            if (nowMs - lastGuardToastMs >= 60_000L || shouldAutoReset) {
                lastGuardToastMs = nowMs
                val isEn = prefsMgr.appLang == 2
                val msg = when {
                    isOverheat && shouldAutoReset -> if (isEn) {
                        String.format(Locale.US, "🔥 Thermal Guard: Battery %.1f°C! Auto-reset SSAA to Native.", cachedBatTemp)
                    } else {
                        String.format(Locale.US, "🔥 Thermal Guard: Pin %.1f°C quá nóng! Đã tự hạ SSAA về gốc.", cachedBatTemp)
                    }
                    isOverheat -> if (isEn) {
                        String.format(Locale.US, "🔥 Thermal Guard: Battery %.1f°C exceeded %d°C limit!", cachedBatTemp, prefsMgr.thermalGuardTempLimit)
                    } else {
                        String.format(Locale.US, "🔥 Thermal Guard: Pin %.1f°C vượt ngưỡng %d°C!", cachedBatTemp, prefsMgr.thermalGuardTempLimit)
                    }
                    isLowBat && shouldAutoReset -> if (isEn) {
                        "🪫 Thermal Guard: Low battery $cachedBatPct%! Auto-reset SSAA to Native."
                    } else {
                        "🪫 Thermal Guard: Pin yếu $cachedBatPct%! Đã tự hạ SSAA về gốc."
                    }
                    else -> if (isEn) {
                        "🪫 Thermal Guard: Battery low ($cachedBatPct% <= $batMin%)!"
                    } else {
                        "🪫 Thermal Guard: Pin yếu ($cachedBatPct% <= $batMin%)!"
                    }
                }
                mainHandler.post {
                    Toast.makeText(this@FpsOverlayService, msg, Toast.LENGTH_LONG).show()
                }
            }
        } else if (cachedBatTemp <= tempLimit - 2.0f && (cachedBatPct > batMin + 3 || isChargingNow)) {
            guardAutoResetDone = false
        }
    }

    private fun parseMaxThermalTokens(rawTokens: String): Float {
        var maxTemp = 0f
        for (tok in rawTokens.trim().split(WS_REGEX)) {
            val tRaw = tok.toFloatOrNull() ?: continue
            val tC = when {
                tRaw > 1000f -> tRaw / 1000f
                tRaw > 150f -> tRaw / 10f
                else -> tRaw
            }
            // Allow 0°C..125°C so peltier coolers (sò lạnh) cooling sensors below 20°C still update cleanly
            if (tC in 0.5f..125f && tC > maxTemp) {
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
        var sawThermalSection = false
        var gotAnyThermalValue = false
        var halCpuMax = 0f
        var halGpuMax = 0f
        var halSkinMax = 0f
        var halBatTemp = 0f

        var start = 0
        val len = raw.length
        while (start < len) {
            val end = raw.indexOf('\n', start).let { if (it < 0) len else it }
            val l = raw.substring(start, end).trim()
            start = end + 1
            if (l.isEmpty()) continue

            when {
                l.startsWith("FG_ACT=") -> {
                    val slashIdx = l.indexOf('/')
                    if (slashIdx > 7) {
                        val beforeSlash = l.substring(7, slashIdx)
                        val pkgCandidate = beforeSlash.substringAfterLast(' ').trim()
                        if (pkgCandidate.contains('.') && !isSystemOrLauncherPkg(pkgCandidate)) {
                            if (pkgCandidate != detectedPkg) {
                                detectedPkg = pkgCandidate
                                detectedAppName = resolveAppName(pkgCandidate)
                            }
                        }
                    }
                }
                l.startsWith("GPU_LOAD=") -> {
                    val rawVal = l.substring(9).replace("%", "").trim()
                    val pct = rawVal.toIntOrNull() ?: -1
                    lastGpuLoadPct = if (pct in 0..100) pct else -1
                }
                l.startsWith("PID=") -> {
                    val rawPids = l.substring(4).trim()
                    if (rawPids.isNotEmpty()) pidSet = rawPids.split(WS_REGEX).toSet()
                }
                l.startsWith("DPID=") -> {
                    val rawPids = l.substring(5).trim()
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
                l.startsWith("CPU_STAT=") -> {
                    // Scene cn0.java /proc/stat delta formula: total = sum(cols 1..7), idle = col4 + col5
                    val parts = l.substring(9).trim().split(WS_REGEX)
                    if (parts.size >= 8 && parts[0] == "cpu") {
                        var total = 0L
                        for (i in 1..7) {
                            total += parts[i].toLongOrNull() ?: 0L
                        }
                        val idle = (parts[4].toLongOrNull() ?: 0L) + (parts[5].toLongOrNull() ?: 0L)
                        if (prevCpuTotal >= 0L && total > prevCpuTotal) {
                            val dTotal = total - prevCpuTotal
                            val dIdle = (idle - prevCpuIdle).coerceAtLeast(0L)
                            lastCpuLoadPct = (((dTotal - dIdle) * 100L) / dTotal).toInt().coerceIn(0, 100)
                        }
                        prevCpuTotal = total
                        prevCpuIdle = idle
                    }
                }
                l.startsWith("CPU_KHZ=") -> {
                    val maxKhz = l.substring(8).trim().split(WS_REGEX).mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
                    if (maxKhz > 0L) lastCpuKhz = maxKhz
                }
                l.startsWith("BAT_T=") -> {
                    val raw = l.substring(6).trim().toFloatOrNull()
                    if (raw != null) {
                        val tC = when {
                            raw > 1000f -> raw / 1000f
                            raw > 100f -> raw / 10f
                            else -> raw
                        }
                        if (tC in 0.5f..80f) cachedBatTemp = tC
                    }
                }
                l.startsWith("CPU_T=") -> {
                    sawThermalSection = true
                    val v = parseMaxThermalTokens(l.substring(6))
                    if (v > 0f) {
                        lastCpuTemp = v
                        gotAnyThermalValue = true
                    }
                }
                l.startsWith("GPU_T=") -> {
                    sawThermalSection = true
                    val v = parseMaxThermalTokens(l.substring(6))
                    if (v > 0f) {
                        lastGpuTemp = v
                        gotAnyThermalValue = true
                    }
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
                l.contains("Temperature{") -> {
                    val m = HAL_TEMP_REGEX.find(l)
                    if (m != null) {
                        val tv = m.groupValues[1].toFloatOrNull() ?: 0f
                        val tp = m.groupValues[2].toIntOrNull() ?: -1
                        if (tv in 0.5f..125f) {
                            when (tp) {
                                0 -> if (tv > halCpuMax) halCpuMax = tv
                                1 -> if (tv > halGpuMax) halGpuMax = tv
                                2 -> if (tv in 0.5f..80f) halBatTemp = tv
                                3 -> if (tv > halSkinMax) halSkinMax = tv
                            }
                        }
                    }
                }
                l.startsWith("VmRSS:") -> {
                    appPssKb = NUM_REGEX.find(l)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
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

        if (halCpuMax > 0f && !gotAnyThermalValue) lastCpuTemp = halCpuMax
        if (halGpuMax > 0f && !gotAnyThermalValue) lastGpuTemp = halGpuMax
        if (halSkinMax > 0f && lastSkinTemp <= 0f) lastSkinTemp = halSkinMax
        if (halBatTemp > 0f) cachedBatTemp = halBatTemp

        // Self-heal persistent shell if thermal_zone reads ever go completely empty for 3 consecutive 1s ticks
        if (sawThermalSection) {
            if (gotAnyThermalValue || halCpuMax > 0f || halGpuMax > 0f) {
                emptyThermalTicks = 0
            } else {
                emptyThermalTicks++
                if (emptyThermalTicks >= 3) {
                    emptyThermalTicks = 0
                    closeShellSession()
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
            lastRamText = String.format(Locale.US, "%.1fG", appPssKb / 1048576.0)
        } else if (memTotalKb > 0L && memAvailKb > 0L) {
            val usedGb = (memTotalKb - memAvailKb) / 1048576.0
            val totalGb = memTotalKb / 1048576.0
            lastRamText = String.format(Locale.US, "%.1f/%.0fG", usedGb, totalGb)
        }

        return pidSet to drmFps
    }

    private fun isIgnoredLayer(layer: String): Boolean {
        return layer.contains("com.nho.upscaler") ||
                layer.contains("gamecube") ||
                layer.contains("Wallpaper") ||
                layer.contains("StatusBar") ||
                layer.contains("NavigationBar") ||
                layer.contains("NotificationShade") ||
                layer.contains("ScreenDecor") ||
                layer.contains("Splash") ||
                layer.contains("Taskbar")
    }

    private fun parseRealFps(pkg: String, dump: String, drmFpsFallback: Float): Float {
        val nowNs = System.nanoTime()
        if (dump.isNotEmpty()) {
            var statsEnabled = true
            var globalFrames = -1L
            var globalJanks = -1L
            var currentLayer = ""
            val currentLayerMap = HashMap<String, Long>(12)
            val currentJankMap = HashMap<String, Long>(12)

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
                } else if (line.startsWith("jankyFrames =")) {
                    val janks = line.substring(13).trim().toLongOrNull() ?: -1L
                    if (janks >= 0L) {
                        if (currentLayer.isEmpty()) {
                            if (globalJanks < 0L) globalJanks = janks
                        } else if (!isIgnoredLayer(currentLayer)) {
                            if (pkg.isEmpty() || currentLayer.contains(pkg)) {
                                val existing = currentJankMap[currentLayer] ?: -1L
                                if (janks > existing) currentJankMap[currentLayer] = janks
                            }
                        }
                    }
                }
            }

            if (!statsEnabled) initializedStatsForPkg = null

            if (lastFpsSampleNs > 0L) {
                val dtSec = (nowNs - lastFpsSampleNs) / 1_000_000_000.0
                if (dtSec >= 0.12) {
                    var maxLayerDelta = 0L
                    var maxLayerName = ""
                    for ((layer, frames) in currentLayerMap) {
                        val prev = prevLayerFrames[layer]
                        if (prev != null && frames >= prev) {
                            val delta = frames - prev
                            if (delta > maxLayerDelta) {
                                maxLayerDelta = delta
                                maxLayerName = layer
                            }
                        }
                    }

                    val layerJankDelta = if (maxLayerName.isNotEmpty()) {
                        val curJ = currentJankMap[maxLayerName] ?: -1L
                        val prevJ = prevLayerJanks[maxLayerName] ?: -1L
                        if (curJ >= 0L && prevJ >= 0L && curJ > prevJ) (curJ - prevJ) else 0L
                    } else 0L

                    val globalJankDelta = if (prevGlobalJanks >= 0L && globalJanks > prevGlobalJanks) {
                        globalJanks - prevGlobalJanks
                    } else 0L

                    val globalDelta = if (prevGlobalFrames >= 0L && globalFrames >= prevGlobalFrames) {
                        globalFrames - prevGlobalFrames
                    } else 0L

                    prevLayerFrames = currentLayerMap
                    prevLayerJanks = currentJankMap
                    prevGlobalFrames = globalFrames
                    prevGlobalJanks = globalJanks
                    lastFpsSampleNs = nowNs

                    val bestJankDelta = if (maxLayerName.isNotEmpty() && layerJankDelta > 0L) layerJankDelta else globalJankDelta
                    if (bestJankDelta > 0L) {
                        PerfLogManager.addJankDelta(bestJankDelta.toInt())
                    }

                    val bestDelta = if (pkg.isNotEmpty() && maxLayerDelta > 0L) maxLayerDelta else maxOf(maxLayerDelta, globalDelta)

                    if (bestDelta > 0L) {
                        emptyFpsTicks = 0
                        frozenDrmTicks = 0
                        hasValidFpsSample = true
                        val computed = (bestDelta / dtSec).toFloat()
                        return computed.coerceIn(1f, 240f)
                    } else {
                        emptyFpsTicks++
                        if (emptyFpsTicks >= 6) initializedStatsForPkg = null

                        if (drmFpsFallback > 1f) {
                            if (abs(drmFpsFallback - lastDrmFps) < 0.01f) {
                                frozenDrmTicks++
                            } else {
                                frozenDrmTicks = 0
                            }
                            lastDrmFps = drmFpsFallback
                            if (frozenDrmTicks < 4) {
                                hasValidFpsSample = true
                                return drmFpsFallback.coerceIn(1f, 240f)
                            }
                        }

                        if (hasValidFpsSample) {
                            return if (emptyFpsTicks <= 2 && lastFps > 0f) lastFps else 0f
                        }
                    }
                } else if (lastFps >= 0f) {
                    return lastFps
                }
            } else {
                prevLayerFrames = currentLayerMap
                prevLayerJanks = currentJankMap
                prevGlobalFrames = globalFrames
                prevGlobalJanks = globalJanks
                lastFpsSampleNs = nowNs
            }
        }

        return if (hasValidFpsSample && lastFps >= 0f) lastFps else -1f
    }

    override fun onDestroy() {
        if (PerfLogManager.isRecording) {
            val saved = PerfLogManager.stopAndSave(this)
            if (saved != null) {
                Toast.makeText(
                    this,
                    Lang.str(this, R.string.toast_rec_saved, saved.gameName, saved.avgFps, saved.avgWatts),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
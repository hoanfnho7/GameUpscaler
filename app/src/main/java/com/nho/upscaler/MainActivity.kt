package com.nho.upscaler

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var prefsMgr: PrefsManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var telemetryRunnable: Runnable? = null

    private var physW = 1080
    private var physH = 2400
    private var physDen = 480
    private var refreshRate = 120

    data class AppItem(
        val name: String,
        val pkg: String,
        val iconBase64: String,
        var customScale: Float,
        var isEnabled: Boolean
    )

    private val cachedApps = mutableListOf<AppItem>()
    private var isAppsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsMgr = PrefsManager(this)

        // Immersive Dark Status & Nav Bars (#111316)
        window.statusBarColor = android.graphics.Color.parseColor("#111316")
        window.navigationBarColor = android.graphics.Color.parseColor("#0C0E11")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#111316"))
        }

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#111316"))
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pushInitialData()
                }
            }
            addJavascriptInterface(WebAppInterface(), "Android")
        }

        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)

        // Shizuku physical display query
        if (ShizukuHelper.ensureReady(this)) {
            ShizukuHelper.fetchPhysicalDisplay { w, h, den ->
                physW = w
                physH = h
                physDen = den
                mainHandler.post {
                    webView.evaluateJavascript("if(window.updateResolutionLabels) window.updateResolutionLabels($physW, $physH);", null)
                }
            }
        }

        // Query refresh rate
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            refreshRate = display?.mode?.refreshRate?.toInt() ?: 120
        } catch (_: Exception) {}

        loadAppsInBackground()
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onResume() {
        super.onResume()
        startTelemetryLoop()
        pushInitialData()
    }

    override fun onPause() {
        super.onPause()
        stopTelemetryLoop()
    }

    private fun startTelemetryLoop() {
        stopTelemetryLoop()
        telemetryRunnable = object : Runnable {
            override fun run() {
                pushTelemetryUpdate()
                mainHandler.postDelayed(this, 1500)
            }
        }
        mainHandler.post(telemetryRunnable!!)
    }

    private fun stopTelemetryLoop() {
        telemetryRunnable?.let { mainHandler.removeCallbacks(it) }
        telemetryRunnable = null
    }

    private fun pushTelemetryUpdate() {
        val json = JSONObject().apply {
            // RAM
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
            val totalGb = mi.totalMem.toDouble() / (1024 * 1024 * 1024)
            val availGb = mi.availMem.toDouble() / (1024 * 1024 * 1024)
            val usedPct = (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem) * 100).toInt()
            put("ramUsedPct", usedPct)
            put("ramAvailGb", availGb)
            put("ramTotalGb", totalGb)

            // Battery
            val batFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batIntent = registerReceiver(null, batFilter)
            val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 88
            val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            val batPct = if (level >= 0 && scale > 0) (level * 100) / scale else 88
            val tempRaw = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 412
            val batTemp = tempRaw / 10.0f
            put("batteryPct", batPct)
            put("batTemp", batTemp)

            // CPU Temp
            val cpuTemp = readCpuTemp()
            put("cpuTemp", cpuTemp)

            // Overlay running state
            put("isOverlayRunning", FpsOverlayService.isServiceRunning)
        }

        webView.evaluateJavascript("if(window.onTelemetryUpdate) window.onTelemetryUpdate(${json.toString()});", null)
    }

    private fun pushInitialData() {
        mainHandler.post {
            val json = getInitialDataJson()
            webView.evaluateJavascript("if(window.onTelemetryUpdate) window.onTelemetryUpdate($json);", null)
        }
    }

    private fun getInitialDataJson(): String {
        return JSONObject().apply {
            put("soc", getDeviceSocName())
            put("deviceModel", "${Build.MANUFACTURER} • ${Build.MODEL}")
            put("physW", physW)
            put("physH", physH)
            put("physDen", physDen)
            put("refreshRate", refreshRate)
            put("selectedFactor", (prefsMgr.targetScaleFactor / 100f).toDouble())
            put("globalSsaaEnabled", prefsMgr.enableFpsOverlay)
            put("isHudRunning", FpsOverlayService.isServiceRunning)
            put("hudLayoutMode", prefsMgr.hudLayoutMode)
            put("hudColorAccent", prefsMgr.hudColorAccent)

            val appsArray = JSONArray()
            synchronized(cachedApps) {
                cachedApps.forEach { app ->
                    appsArray.put(JSONObject().apply {
                        put("name", app.name)
                        put("pkg", app.pkg)
                        put("icon", app.iconBase64)
                        put("customScale", app.customScale.toDouble())
                        put("isEnabled", app.isEnabled)
                    })
                }
            }
            put("apps", appsArray)
        }.toString()
    }

    private fun loadAppsInBackground() {
        thread(priority = Thread.NORM_PRIORITY) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            val items = mutableListOf<AppItem>()

            for (ri in resolved) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) continue

                // Check if likely a game or user app
                val appInfo = ri.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                } else false

                if (!isSystem || isGame || isCommonGame(pkg)) {
                    val label = ri.loadLabel(pm).toString()
                    val icon = ri.loadIcon(pm)
                    val base64 = drawableToBase64(icon)
                    val customScale = (prefsMgr.getAppCustomScale(pkg) / 100f)
                    val isEnabled = prefsMgr.isGameEnabled(pkg)
                    items.add(AppItem(label, pkg, base64, customScale, isEnabled))
                }
            }

            // Sort: games first, then alphabet
            items.sortBy { it.name.lowercase() }

            synchronized(cachedApps) {
                cachedApps.clear()
                cachedApps.addAll(items)
                isAppsLoaded = true
            }

            mainHandler.post {
                val json = getInitialDataJson()
                webView.evaluateJavascript("if(window.onTelemetryUpdate) window.onTelemetryUpdate($json);", null)
            }
        }
    }

    private fun isCommonGame(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("game") || p.contains("genshin") || p.contains("pubg") ||
               p.contains("arena") || p.contains("moba") || p.contains("mobile") ||
               p.contains("mihoyo") || p.contains("tencent") || p.contains("garena") ||
               p.contains("riot") || p.contains("unity") || p.contains("epic")
    }

    private fun drawableToBase64(drawable: Drawable): String {
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 85, baos)
            "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun readCpuTemp(): Float {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )
        for (p in paths) {
            try {
                val f = File(p)
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim().toFloatOrNull() ?: continue
                    val c = if (raw > 1000f) raw / 1000f else if (raw > 150f) raw / 10f else raw
                    if (c in 20f..100f) return c
                }
            } catch (_: Exception) {}
        }
        return 44.0f
    }

    private fun getDeviceSocName(): String {
        return try {
            val hardware = Build.HARDWARE
            val board = Build.BOARD
            when {
                hardware.contains("qcom", true) || board.contains("qcom", true) -> {
                    when {
                        board.contains("pineapple", true) -> "Snapdragon 8 Gen 3"
                        board.contains("kalama", true) -> "Snapdragon 8 Gen 2"
                        board.contains("taro", true) -> "Snapdragon 8 Gen 1"
                        else -> "Snapdragon Platform"
                    }
                }
                hardware.contains("mt", true) || board.contains("mt", true) -> "MediaTek Dimensity"
                else -> "${Build.MANUFACTURER} SoC"
            }
        } catch (_: Exception) {
            "Gaming SoC"
        }
    }

    // =========================================================================
    // JAVASCRIPT BRIDGE INTERFACE
    // =========================================================================
    inner class WebAppInterface {
        @JavascriptInterface
        fun getInitialData(): String {
            return getInitialDataJson()
        }

        @JavascriptInterface
        fun setSsaaFactor(factor: Float) {
            val factorInt = (factor * 100).toInt()
            prefsMgr.targetScaleFactor = factorInt
            if (prefsMgr.enableFpsOverlay && ShizukuHelper.isBinderAlive()) {
                val w = (physW.toFloat() * factor).toInt()
                val h = (physH.toFloat() * factor).toInt()
                val den = (physDen.toFloat() * factor).toInt()
                ShizukuHelper.runAsync("wm size ${w}x${h}; wm density $den")
            }
        }

        @JavascriptInterface
        fun setGlobalSsaa(enabled: Boolean) {
            prefsMgr.enableFpsOverlay = enabled
            if (enabled) {
                val f = prefsMgr.targetScaleFactor.toFloat() / 100f
                val w = (physW.toFloat() * f).toInt()
                val h = (physH.toFloat() * f).toInt()
                val den = (physDen.toFloat() * f).toInt()
                ShizukuHelper.runAsync("wm size ${w}x${h}; wm density $den")
            } else {
                ShizukuHelper.resetDisplay()
            }
        }

        @JavascriptInterface
        fun resetDisplay() {
            ShizukuHelper.resetDisplay()
            mainHandler.post {
                Toast.makeText(this@MainActivity, "Đã khôi phục màn hình gốc 1.0x", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun toggleOverlay() {
            mainHandler.post {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Vui lòng cấp quyền Vẽ trên ứng dụng khác", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@post
                }
                if (!FpsOverlayService.isServiceRunning) {
                    val svc = Intent(this@MainActivity, FpsOverlayService::class.java).apply {
                        putExtra("target_pkg", "")
                        putExtra("game_name", "HUD Preview")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc)
                    } else {
                        startService(svc)
                    }
                } else {
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                }
                mainHandler.postDelayed({ pushTelemetryUpdate() }, 300)
            }
        }

        @JavascriptInterface
        fun requestShizukuPermission() {
            mainHandler.post {
                if (!ShizukuHelper.ensureReady(this@MainActivity)) {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launchIntent != null) startActivity(launchIntent)
                    } catch (_: Exception) {}
                }
            }
        }

        @JavascriptInterface
        fun openOverlaySettings() {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        @JavascriptInterface
        fun openBatterySettings() {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun setGameEnabled(pkg: String, enabled: Boolean) {
            prefsMgr.setGameEnabled(pkg, enabled)
            synchronized(cachedApps) {
                cachedApps.find { it.pkg == pkg }?.isEnabled = enabled
            }
        }

        @JavascriptInterface
        fun setGameScale(pkg: String, scale: Float) {
            val scaleInt = (scale * 100).toInt()
            prefsMgr.setAppCustomScale(pkg, scaleInt)
            synchronized(cachedApps) {
                cachedApps.find { it.pkg == pkg }?.customScale = scale
            }
            mainHandler.post {
                Toast.makeText(this@MainActivity, if (scale > 0) "Đã khóa ${scale}x cho game này" else "Đã đặt về mặc định", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun launchGame(pkg: String) {
            mainHandler.post {
                val custom = prefsMgr.getAppCustomScale(pkg)
                val effectiveFactor = if (custom > 0) custom else prefsMgr.targetScaleFactor
                ShizukuHelper.launchGameWithUpscale(pkg, effectiveFactor)
                finish()
            }
        }

        @JavascriptInterface
        fun setHudMetric(key: String, enabled: Boolean) {
            prefsMgr.setMetric(key, enabled)
            notifyOverlayStyleUpdated()
        }

        @JavascriptInterface
        fun setHudLayoutMode(mode: Int) {
            prefsMgr.hudLayoutMode = mode
            prefsMgr.overlayVertical = (mode == 1)
            notifyOverlayStyleUpdated()
        }

        @JavascriptInterface
        fun setHudColorAccent(accentIdx: Int) {
            prefsMgr.hudColorAccent = accentIdx
            notifyOverlayStyleUpdated()
        }

        @JavascriptInterface
        fun setHudScale(scalePct: Int) {
            val level = when {
                scalePct <= 85 -> 0
                scalePct <= 110 -> 1
                scalePct <= 135 -> 2
                else -> 3
            }
            prefsMgr.overlaySizeLevel = level
            notifyOverlayStyleUpdated()
        }

        @JavascriptInterface
        fun setHudOpacity(opacityPct: Int) {
            val mode = when {
                opacityPct <= 35 -> 2
                opacityPct <= 75 -> 1
                opacityPct <= 95 -> 0
                else -> 0
            }
            prefsMgr.overlayBgMode = mode
            notifyOverlayStyleUpdated()
        }
    }

    private fun notifyOverlayStyleUpdated() {
        if (FpsOverlayService.isServiceRunning) {
            startService(Intent(this, FpsOverlayService::class.java).apply {
                action = "STYLE_CHANGED"
            })
        }
    }
}

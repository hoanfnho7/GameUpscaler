package com.nho.upscaler

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale
import kotlin.concurrent.thread

data class AppItem(
    val name: String,
    val pkg: String,
    val icon: Drawable,
    var isPinned: Boolean = false,
    var customScale: Int = 0
)

class MainActivity : Activity() {
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private lateinit var adapter: BaseAdapter
    private lateinit var prefsMgr: PrefsManager
    private val pinnedPkgs = mutableSetOf<String>()

    private lateinit var tabDashBtn: TextView
    private lateinit var tabHudBtn: TextView
    private lateinit var tabGamesBtn: TextView
    private lateinit var dashPage: ScrollView
    private lateinit var hudPage: ScrollView
    private lateinit var gamesPage: LinearLayout

    private lateinit var statusLabelTv: TextView
    private lateinit var resSummaryTv: TextView
    private lateinit var batTempTv: TextView
    private lateinit var batPctTv: TextView
    private lateinit var freeRamTv: TextView
    private lateinit var ramGaugeView: RamArcGaugeView
    private lateinit var sparklineView: LiveSparklineView
    private lateinit var pinnedStripRow: LinearLayout

    private var tileSsaaValTv: TextView? = null
    private var tileAutoHudBox: LinearLayout? = null
    private var tileAutoHudValTv: TextView? = null
    private var tileLiveHudBox: LinearLayout? = null
    private var tileLiveHudValTv: TextView? = null
    private var tileLogsValTv: TextView? = null
    private var liveHudMockTv: TextView? = null

    private var activeTab = 0
    private var selectedFactor = 125
    private var showPinnedOnly = false
    private var enableFpsOverlay = true
    private var overlaySizeLevel = 1
    private var overlayBgMode = 1
    private var hudPollMode = 0
    private var overlayVertical = false
    private var currentQuery = ""
    private var physW = 1440
    private var physH = 3168
    private var physDen = 624

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsMgr = PrefsManager(this)
        selectedFactor = prefsMgr.savedFactor
        showPinnedOnly = prefsMgr.showPinnedOnly
        enableFpsOverlay = prefsMgr.enableFpsOverlay
        overlaySizeLevel = prefsMgr.overlaySizeLevel
        overlayBgMode = prefsMgr.overlayBgMode
        hudPollMode = prefsMgr.hudPollMode
        overlayVertical = prefsMgr.overlayVertical

        if (handleIncomingIntent(intent)) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 102)
        }

        pinnedPkgs.addAll(prefsMgr.getPinnedPackages())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.bgRoot(this@MainActivity))
            setPadding(28, 34, 28, 12)
        }

        // Top Bar: App Title + Theme Switcher (Dark/Light/Auto) + Language Switcher
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "GAMEUPSCALER"
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        })
        statusLabelTv = TextView(this).apply {
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 2, 0, 0)
        }
        titleCol.addView(statusLabelTv)
        headerBar.addView(titleCol)

        // Theme Mode Toggle Button
        val themeBtn = TextView(this).apply {
            text = when (prefsMgr.themeMode) {
                1 -> Lang.str(this@MainActivity, R.string.theme_dark)
                2 -> Lang.str(this@MainActivity, R.string.theme_light)
                else -> Lang.str(this@MainActivity, R.string.theme_auto)
            }
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(14, 8, 14, 8)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 10f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.themeMode = (prefsMgr.themeMode + 1) % 3
                recreate()
            }
        }
        headerBar.addView(themeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })

        // Language Toggle Button
        headerBar.addView(TextView(this).apply {
            text = Lang.modeBadge(this@MainActivity)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(14, 8, 14, 8)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 10f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.appLang = (prefsMgr.appLang + 1) % 3
                notifyOverlayStyleUpdated()
                recreate()
            }
        })
        root.addView(headerBar)

        // 3-Tab Tactical Switcher (DASHBOARD | HUD CONFIG | ALL GAMES)
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(5, 5, 5, 5)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 14f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        tabDashBtn = makeTabButton(Lang.str(this, R.string.tab_dashboard)) { switchTab(0) }
        tabHudBtn = makeTabButton(Lang.str(this, R.string.tab_hud_cfg)) { switchTab(1) }
        tabGamesBtn = makeTabButton(Lang.str(this, R.string.tab_games)) { switchTab(2) }
        tabBar.addView(tabDashBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 })
        tabBar.addView(tabHudBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 })
        tabBar.addView(tabGamesBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12
            bottomMargin = 12
        })

        dashPage = buildDashboardPage()
        hudPage = buildHudConfigPage()
        gamesPage = buildAllGamesPage()

        root.addView(dashPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(hudPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(gamesPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        switchTab(0)
        refreshLiveDashboardStats()
        updateLiveHudMockup()

        if (ShizukuHelper.ensureReady(this)) {
            ShizukuHelper.fetchPhysicalDisplay { w, h, den ->
                physW = w
                physH = h
                physDen = den
                runOnUiThread { updateResolutionSummary() }
            }
        }
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        refreshLiveDashboardStats()
    }

    private fun makeTabButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(6, 11, 6, 11)
            setOnClickListener { onClick() }
        }
    }

    private fun switchTab(idx: Int) {
        activeTab = idx
        val accentCol = UiTheme.accent(this)
        val activeTxt = UiTheme.textOnAccent(this)
        val idleTxt = UiTheme.textSecondary(this)

        val tabs = listOf(tabDashBtn, tabHudBtn, tabGamesBtn)
        tabs.forEachIndexed { i, btn ->
            val active = (i == idx)
            btn.setTextColor(if (active) activeTxt else idleTxt)
            btn.background = if (active) UiTheme.roundBox(accentCol, 10f) else null
        }

        dashPage.visibility = if (idx == 0) View.VISIBLE else View.GONE
        hudPage.visibility = if (idx == 1) View.VISIBLE else View.GONE
        gamesPage.visibility = if (idx == 2) View.VISIBLE else View.GONE

        if (idx == 0) refreshLiveDashboardStats()
        if (idx == 1) updateLiveHudMockup()
    }

    // =========================================================================
    // TAB 1: DASHBOARD (Razer Tactical Layout from Reference Images 1 & 2)
    // =========================================================================
    private fun buildDashboardPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val accentCol = UiTheme.accent(this)

        // Outer green-accented dashboard frame (matching screenshot 1 & 2)
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 22)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 24f, 2, accentCol)
        }

        // 1. Hero Top Split: Circular RAM Arc Gauge + Battery Temp & Primary Actions
        val heroRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 18)
        }

        ramGaugeView = RamArcGaugeView(this)
        heroRow.addView(ramGaugeView, LinearLayout.LayoutParams(220, 220).apply { rightMargin = 20 })

        val heroRightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val batLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        batLine.addView(TextView(this).apply {
            text = "BATTERY"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 0, 12, 0)
        })
        batTempTv = TextView(this).apply {
            text = "29°C"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        }
        batLine.addView(batTempTv)
        heroRightCol.addView(batLine)

        resSummaryTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.reading_display)
            textSize = 10.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 2, 0, 10)
        }
        heroRightCol.addView(resSummaryTv)

        // Pill Action 1: GAME INSIGHTS (Opens PerfChartActivity)
        heroRightCol.addView(TextView(this).apply {
            text = "📈  ${Lang.str(this@MainActivity, R.string.dash_insights)}"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            gravity = Gravity.CENTER
            setPadding(12, 12, 12, 12)
            background = UiTheme.roundBox(UiTheme.bgTileActive(this@MainActivity), 12f, 1, accentCol)
            setOnClickListener { startActivity(Intent(this@MainActivity, PerfChartActivity::class.java)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })

        // Pill Action 2: RESET DISPLAY
        heroRightCol.addView(TextView(this).apply {
            text = "↺  ${Lang.str(this@MainActivity, R.string.dash_reset)}"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(12, 11, 12, 11)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                if (ShizukuHelper.ensureReady(this@MainActivity)) {
                    ShizukuHelper.resetDisplay()
                    cancelActiveNotification()
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                    Toast.makeText(context, Lang.str(this@MainActivity, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        heroRow.addView(heroRightCol)
        frame.addView(heroRow)

        // 2. 6-Tool Tactical Grid (2 rows x 3 columns, matching Screenshot 2)
        val gridRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }
        val (ssaaBox, ssaaVal) = makeToolTile("⚡", Lang.str(this, R.string.tool_ssaa), "1.25x", true) {
            val cycle = listOf(110, 115, 120, 125)
            selectedFactor = cycle[(cycle.indexOf(selectedFactor) + 1) % cycle.size]
            prefsMgr.savedFactor = selectedFactor
            updateResolutionSummary()
            refreshLiveDashboardStats()
            adapter.notifyDataSetChanged()
        }
        tileSsaaValTv = ssaaVal
        gridRow1.addView(ssaaBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        val (autoHudBox, autoHudVal) = makeToolTile("📊", Lang.str(this, R.string.tool_auto_hud), "ON", enableFpsOverlay) {
            enableFpsOverlay = !enableFpsOverlay
            prefsMgr.enableFpsOverlay = enableFpsOverlay
            if (enableFpsOverlay && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, Lang.str(this, R.string.toast_grant_overlay_long), Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else if (!enableFpsOverlay) {
                stopService(Intent(this, FpsOverlayService::class.java))
            }
            refreshLiveDashboardStats()
        }
        tileAutoHudBox = autoHudBox
        tileAutoHudValTv = autoHudVal
        gridRow1.addView(autoHudBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        val (liveHudBox, liveHudVal) = makeToolTile("👁️", Lang.str(this, R.string.tool_live_hud), "OFF", FpsOverlayService.isServiceRunning) {
            toggleHudPreviewService()
            frame.postDelayed({ refreshLiveDashboardStats() }, 250)
        }
        tileLiveHudBox = liveHudBox
        tileLiveHudValTv = liveHudVal
        gridRow1.addView(liveHudBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        frame.addView(gridRow1)

        val gridRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 18)
        }
        val (tileBox, _) = makeToolTile("🎛️", Lang.str(this, R.string.tool_tile), "QS TILE", false) {
            requestAddQuickTile()
        }
        gridRow2.addView(tileBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        val (sensorBox, _) = makeToolTile("🌡️", Lang.str(this, R.string.tool_sensors), "ZONES", false) {
            showThermalInspectorDialog()
        }
        gridRow2.addView(sensorBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        val (logsBox, logsVal) = makeToolTile("📈", Lang.str(this, R.string.tool_logs), "0", false) {
            startActivity(Intent(this, PerfChartActivity::class.java))
        }
        tileLogsValTv = logsVal
        gridRow2.addView(logsBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        frame.addView(gridRow2)

        // 3. MY GAMES Quick-Launch Icon Strip (Matching Screenshot 1 & 2)
        val gamesHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2, 0, 2, 10)
        }
        gamesHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_my_games)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        gamesHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_manage)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setOnClickListener { switchTab(2) }
        })
        frame.addView(gamesHeader)

        val stripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 16)
        }
        pinnedStripRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stripScroll.addView(pinnedStripRow)
        frame.addView(stripScroll)

        // 4. LIVE MONITOR Waveform Card (Matching Screenshot 1 & 2 bottom card)
        val monitorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 16f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener { startActivity(Intent(this@MainActivity, PerfChartActivity::class.java)) }
        }
        val monTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        monTop.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_live_monitor)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        monTop.addView(TextView(this).apply {
            text = "SSAA 1.${selectedFactor % 100}x"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        monitorCard.addView(monTop)

        sparklineView = LiveSparklineView(this)
        monitorCard.addView(sparklineView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 96).apply {
            topMargin = 8
            bottomMargin = 8
        })

        val monBottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        batPctTv = TextView(this).apply {
            text = "BAT 100%"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        freeRamTv = TextView(this).apply {
            text = "FREE 6.0 GB"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        }
        monBottom.addView(batPctTv)
        monBottom.addView(freeRamTv)
        monitorCard.addView(monBottom)

        frame.addView(monitorCard)
        scroll.addView(frame)
        return scroll
    }

    private fun makeToolTile(
        iconEmoji: String,
        title: String,
        subValue: String,
        active: Boolean,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val accentCol = UiTheme.accent(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 14)
            background = UiTheme.roundBox(
                if (active) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgTile(this@MainActivity),
                14f,
                1,
                if (active) accentCol else UiTheme.strokeSubtle(this@MainActivity)
            )
            setOnClickListener { onClick() }
        }
        box.addView(TextView(this).apply {
            text = iconEmoji
            textSize = 17f
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = title
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(0, 6, 0, 2)
        })
        val valTv = TextView(this).apply {
            text = subValue
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (active) accentCol else UiTheme.textSecondary(this@MainActivity))
            gravity = Gravity.CENTER
        }
        box.addView(valTv)
        return box to valTv
    }

    private fun rebuildPinnedGamesStrip() {
        if (!::pinnedStripRow.isInitialized) return
        pinnedStripRow.removeAllViews()
        val accentCol = UiTheme.accent(this)
        val pinnedList = allApps.filter { it.isPinned }

        for (item in pinnedList) {
            val effectiveScale = if (item.customScale > 0) item.customScale else selectedFactor
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(6, 4, 6, 4)
                setOnClickListener { launchGameByPkg(item.pkg, item.name, effectiveScale) }
                setOnLongClickListener {
                    createPinnedShortcut(item, effectiveScale)
                    true
                }
            }
            val img = ImageView(this).apply {
                setImageDrawable(item.icon)
                layoutParams = LinearLayout.LayoutParams(96, 96)
            }
            val lbl = TextView(this).apply {
                text = item.name
                textSize = 10.5f
                setTextColor(UiTheme.textPrimary(this@MainActivity))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(124, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 }
            }
            col.addView(img)
            col.addView(lbl)
            pinnedStripRow.addView(col, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 10 })
        }

        // "+ Add" slot at the end (matching Screenshot 1 & 2)
        val addCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(6, 4, 6, 4)
            setOnClickListener { switchTab(2) }
        }
        val plusBox = TextView(this).apply {
            text = "+"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            gravity = Gravity.CENTER
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 20f, 1, UiTheme.strokeSubtle(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(96, 96)
        }
        val addLbl = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_add_game)
            textSize = 10.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(110, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 6 }
        }
        addCol.addView(plusBox)
        addCol.addView(addLbl)
        pinnedStripRow.addView(addCol)
    }

    private fun refreshLiveDashboardStats() {
        val shizukuOk = ShizukuHelper.isBinderAlive()
        if (::statusLabelTv.isInitialized) {
            statusLabelTv.text = Lang.str(this, if (shizukuOk) R.string.status_shizuku_ok else R.string.status_shizuku_off)
            statusLabelTv.setTextColor(if (shizukuOk) UiTheme.accent(this) else UiTheme.ACCENT_RED_BRIGHT)
        }

        // Read RAM usage
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val usedPct = (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem.toDouble()) * 100).toInt().coerceIn(1, 99)
            val freeGb = mi.availMem / (1024.0 * 1024.0 * 1024.0)
            if (::ramGaugeView.isInitialized) ramGaugeView.setPercent(usedPct)
            if (::freeRamTv.isInitialized) {
                freeRamTv.text = Lang.str(this, R.string.dash_free_ram, String.format(Locale.US, "%.1f", freeGb))
            }
        } catch (_: Exception) {}

        // Read Battery temp & %
        try {
            val batIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (tempTenths > 0 && ::batTempTv.isInitialized) {
                batTempTv.text = String.format(Locale.US, "%.1f°C", tempTenths / 10f)
            }
            if (level >= 0 && scale > 0 && ::batPctTv.isInitialized) {
                batPctTv.text = "BAT ${(level * 100) / scale}%"
            }
        } catch (_: Exception) {}

        // Update 6-Tool Tile states
        val accentCol = UiTheme.accent(this)
        tileSsaaValTv?.text = "${selectedFactor / 100}.${selectedFactor % 100}x"

        tileAutoHudValTv?.text = if (enableFpsOverlay) "ON" else "OFF"
        tileAutoHudValTv?.setTextColor(if (enableFpsOverlay) accentCol else UiTheme.textSecondary(this))
        tileAutoHudBox?.background = UiTheme.roundBox(
            if (enableFpsOverlay) UiTheme.bgTileActive(this) else UiTheme.bgTile(this),
            14f,
            1,
            if (enableFpsOverlay) accentCol else UiTheme.strokeSubtle(this)
        )

        val hudRunning = FpsOverlayService.isServiceRunning
        tileLiveHudValTv?.text = if (hudRunning) "ON" else "OFF"
        tileLiveHudValTv?.setTextColor(if (hudRunning) accentCol else UiTheme.textSecondary(this))
        tileLiveHudBox?.background = UiTheme.roundBox(
            if (hudRunning) UiTheme.bgTileActive(this) else UiTheme.bgTile(this),
            14f,
            1,
            if (hudRunning) accentCol else UiTheme.strokeSubtle(this)
        )

        val logCount = try { PerfLogManager.listSessions(this).size } catch (_: Exception) { 0 }
        tileLogsValTv?.text = "$logCount LOGS"
    }

    // =========================================================================
    // TAB 2: HUD CONFIG (GFX GUIDE Clean List Layout from Reference Image 3)
    // =========================================================================
    private fun buildHudConfigPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val accentCol = UiTheme.accent(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        // 1. Live HUD Preview Card
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val prevTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prevTop.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_preview)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        prevTop.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_reset_pos)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(14, 7, 14, 7)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 10f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.resetHudPosition()
                if (FpsOverlayService.isServiceRunning) {
                    startService(Intent(this@MainActivity, FpsOverlayService::class.java).apply { action = "RESET_POS" })
                }
                Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_pos_reset), Toast.LENGTH_SHORT).show()
            }
        })
        previewCard.addView(prevTop)

        val stageFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 20)
            background = UiTheme.roundBox(Color.parseColor("#232926"), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val mockTv = TextView(this).apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_GREEN)
        }
        liveHudMockTv = mockTv
        stageFrame.addView(mockTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewCard.addView(stageFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })
        container.addView(previewCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        // 2. Appearance & Polling Card
        val styleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val styleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        styleHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_style)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        styleHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            setPadding(14, 7, 14, 7)
            background = UiTheme.roundBox(UiTheme.bgTileActive(this@MainActivity), 10f, 1, accentCol)
            setOnClickListener {
                overlayVertical = !overlayVertical
                prefsMgr.overlayVertical = overlayVertical
                text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
                updateLiveHudMockup()
                notifyOverlayStyleUpdated()
            }
        })
        styleCard.addView(styleHeader)

        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.size_tiny),
                1 to Lang.str(this, R.string.size_small),
                2 to Lang.str(this, R.string.size_medium),
                3 to Lang.str(this, R.string.size_large)
            ),
            selectedIndex = overlaySizeLevel,
            padTop = 10,
            padBottom = 8
        ) { lvl ->
            overlaySizeLevel = lvl
            prefsMgr.overlaySizeLevel = lvl
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.bg_solid),
                1 to Lang.str(this, R.string.bg_glass),
                2 to Lang.str(this, R.string.bg_sheer),
                3 to Lang.str(this, R.string.bg_clear)
            ),
            selectedIndex = overlayBgMode,
            padTop = 0,
            padBottom = 12
        ) { mode ->
            overlayBgMode = mode
            prefsMgr.overlayBgMode = mode
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_poll)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
        })
        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.poll_auto),
                1 to Lang.str(this, R.string.poll_300ms),
                2 to Lang.str(this, R.string.poll_1s),
                3 to Lang.str(this, R.string.poll_2s)
            ),
            selectedIndex = hudPollMode,
            padTop = 8,
            padBottom = 0
        ) { mode ->
            hudPollMode = mode
            prefsMgr.hudPollMode = mode
            notifyOverlayStyleUpdated()
        })
        container.addView(styleCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        // 3. Clean GFX-Guide Style Vertical Metric List (Matching Reference Image 3!)
        val metricsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 10)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        metricsCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_metrics)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            setPadding(0, 0, 0, 8)
        })

        data class MetricSpec(val key: String, val icon: String, val titleId: Int, val descId: Int, val defVal: Boolean)
        val specs = listOf(
            MetricSpec("m_fps", "⚡", R.string.metric_fps, R.string.desc_fps, true),
            MetricSpec("m_watt", "🔌", R.string.metric_watt, R.string.desc_watt, true),
            MetricSpec("m_gpu", "🎮", R.string.metric_vram, R.string.desc_vram, true),
            MetricSpec("m_cpu_freq", "⚙️", R.string.metric_cpu_freq, R.string.desc_cpu_freq, false),
            MetricSpec("m_bat_temp", "🔋", R.string.metric_bat_temp, R.string.desc_bat_temp, true),
            MetricSpec("m_soc_temp", "🌡️", R.string.metric_soc_temp, R.string.desc_soc_temp, true),
            MetricSpec("m_cpu_temp", "🔥", R.string.metric_cpu_temp, R.string.desc_cpu_temp, false),
            MetricSpec("m_gpu_temp", "📟", R.string.metric_gpu_temp, R.string.desc_gpu_temp, false),
            MetricSpec("m_ddr_temp", "🧬", R.string.metric_ddr_temp, R.string.desc_ddr_temp, false),
            MetricSpec("m_cam_temp", "📷", R.string.metric_cam_temp, R.string.desc_cam_temp, false),
            MetricSpec("m_skin_temp", "📱", R.string.metric_skin_temp, R.string.desc_skin_temp, false),
            MetricSpec("m_ram", "💾", R.string.metric_ram, R.string.desc_ram, false)
        )

        specs.forEachIndexed { idx, spec ->
            var enabled = prefsMgr.getMetric(spec.key, spec.defVal)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 14, 4, 14)
            }
            val iconTv = TextView(this).apply {
                text = spec.icon
                textSize = 16f
                setPadding(0, 0, 16, 0)
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 12 }
            }
            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, spec.titleId)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
            })
            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, spec.descId)
                textSize = 11f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                setPadding(0, 2, 0, 0)
            })

            val statusTv = TextView(this).apply {
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END
                fun updateState() {
                    text = if (enabled) "ON" else "OFF"
                    setTextColor(if (enabled) accentCol else UiTheme.textSecondary(this@MainActivity))
                }
                updateState()
                row.setOnClickListener {
                    enabled = !enabled
                    prefsMgr.setMetric(spec.key, enabled)
                    updateState()
                    updateLiveHudMockup()
                    notifyOverlayStyleUpdated()
                }
            }

            row.addView(iconTv)
            row.addView(textCol)
            row.addView(statusTv)
            metricsCard.addView(row)

            if (idx < specs.size - 1) {
                metricsCard.addView(View(this).apply {
                    setBackgroundColor(UiTheme.divider(this@MainActivity))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                })
            }
        }

        container.addView(metricsCard)
        scroll.addView(container)
        return scroll
    }

    // =========================================================================
    // TAB 3: ALL GAMES (Clean Searchable Game & App List)
    // =========================================================================
    private fun buildAllGamesPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val accentCol = UiTheme.accent(this)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchBox = EditText(this).apply {
            hint = Lang.str(this@MainActivity, R.string.hint_search)
            textSize = 12.5f
            setSingleLine(true)
            setHintTextColor(UiTheme.textSecondary(this@MainActivity))
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(20, 13, 20, 13)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    currentQuery = s?.toString() ?: ""
                    sortAndFilterApps()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        filterRow.addView(searchBox)

        val pinFilterBtn = TextView(this).apply {
            text = "★"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(18, 10, 18, 10)
            fun updateStyle() {
                setTextColor(if (showPinnedOnly) accentCol else UiTheme.textSecondary(this@MainActivity))
                background = UiTheme.roundBox(
                    if (showPinnedOnly) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgCard(this@MainActivity),
                    12f,
                    1,
                    if (showPinnedOnly) accentCol else UiTheme.strokeSubtle(this@MainActivity)
                )
            }
            updateStyle()
            setOnClickListener {
                showPinnedOnly = !showPinnedOnly
                prefsMgr.showPinnedOnly = showPinnedOnly
                updateStyle()
                sortAndFilterApps()
            }
        }
        filterRow.addView(pinFilterBtn)
        page.addView(filterRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })

        page.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.subtitle_hint)
            textSize = 10.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(4, 0, 4, 8)
        })

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 10
        }
        adapter = createAppAdapter()
        listView.adapter = adapter
        page.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private fun buildSegmentedRow(
        items: List<Pair<Int, String>>,
        selectedIndex: Int,
        padTop: Int,
        padBottom: Int,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, padTop, 0, padBottom)
        }
        var current = selectedIndex
        val chips = mutableListOf<TextView>()
        val accentCol = UiTheme.accent(this)

        fun refresh() {
            chips.forEachIndexed { idx, chip ->
                val active = (idx == current)
                chip.setTextColor(if (active) UiTheme.textOnAccent(this) else UiTheme.textSecondary(this))
                chip.background = UiTheme.roundBox(
                    if (active) accentCol else UiTheme.bgTile(this),
                    10f,
                    if (active) 0 else 1,
                    UiTheme.strokeSubtle(this)
                )
            }
        }

        items.forEach { (id, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(6, 10, 6, 10)
                setOnClickListener {
                    current = id
                    refresh()
                    onSelect(id)
                }
            }
            chips.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (id < items.size - 1) rightMargin = 8
            })
        }
        refresh()
        return row
    }

    private fun updateLiveHudMockup() {
        val tv = liveHudMockTv ?: return
        val isCompact = (overlaySizeLevel == 0) || !overlayVertical
        val parts = mutableListOf<String>()

        if (prefsMgr.getMetric("m_fps", true)) parts.add(if (isCompact) "120.0F" else "⚡ FPS: 120.0")
        if (prefsMgr.getMetric("m_watt", true)) parts.add(if (isCompact) "5.8W" else "🔌 Điện: 5.8W")
        if (prefsMgr.getMetric("m_bat_temp", true)) parts.add(if (isCompact) "🔋36.5°" else "🔋 Pin: 36.5°C")
        if (prefsMgr.getMetric("m_soc_temp", true)) parts.add(if (isCompact) "🌡58.2°" else "🌡️ SoC: 58.2°C")
        if (prefsMgr.getMetric("m_cpu_temp", false)) parts.add(if (isCompact) "🔥58.2°" else "🔥 CPU: 58.2°C")
        if (prefsMgr.getMetric("m_gpu_temp", false)) parts.add(if (isCompact) "📟54.0°" else "📟 GPU: 54.0°C")
        if (prefsMgr.getMetric("m_ddr_temp", false)) parts.add(if (isCompact) "🧬44.1°" else "🧬 DDR: 44.1°C")
        if (prefsMgr.getMetric("m_cam_temp", false)) parts.add(if (isCompact) "📷39.0°" else "📷 CAM: 39.0°C")
        if (prefsMgr.getMetric("m_skin_temp", false)) parts.add(if (isCompact) "📱37.2°" else "📱 Vỏ: 37.2°C")
        if (prefsMgr.getMetric("m_gpu", true)) parts.add(if (isCompact) "V:640M" else "🎮 VRAM: 640MB")
        if (prefsMgr.getMetric("m_cpu_freq", false)) parts.add(if (isCompact) "3.53G" else "⚙️ CPU: 3.53GHz")
        if (prefsMgr.getMetric("m_ram", false)) parts.add(if (isCompact) "4.2G" else "💾 RAM: 4.2G")

        if (parts.isEmpty()) parts.add(Lang.str(this, R.string.hud_no_metrics))

        tv.text = if (overlayVertical) parts.joinToString("\n") else parts.joinToString(" · ")

        val idx = overlaySizeLevel.coerceIn(0, 3)
        val bIdx = overlayBgMode.coerceIn(0, 3)
        val sizes = floatArrayOf(8.5f, 10.5f, 12f, 13.5f)
        val padH = intArrayOf(10, 14, 18, 22)
        val padV = intArrayOf(4, 7, 10, 12)
        tv.textSize = sizes[idx]
        tv.setPadding(padH[idx], padV[idx], padH[idx], padV[idx])
        tv.setShadowLayer(2.5f, 1f, 1f, UiTheme.TEXT_SHADOW)

        if (bIdx == 3) {
            tv.background = null
        } else {
            tv.background = UiTheme.roundBox(UiTheme.HUD_BG_OPACITY[bIdx], 12f, 1, UiTheme.HUD_STROKE_OPACITY[bIdx])
        }
    }

    private fun toggleHudPreviewService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, Lang.str(this, R.string.toast_grant_overlay_short), Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!FpsOverlayService.isServiceRunning) {
            val svc = Intent(this, FpsOverlayService::class.java).apply {
                putExtra("target_pkg", "")
                putExtra("game_name", "HUD Preview")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        } else {
            stopService(Intent(this, FpsOverlayService::class.java))
        }
    }

    private fun requestAddQuickTile() {
        val hintMsg = Lang.str(this, R.string.hint_quick_tile)
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val sbm = getSystemService(StatusBarManager::class.java)
                sbm?.requestAddTileService(
                    ComponentName(this, HudTileService::class.java),
                    Lang.tileLabel(this),
                    Icon.createWithResource(this, R.drawable.ic_hud_tile),
                    mainExecutor
                ) { result ->
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    ) {
                        Toast.makeText(this, Lang.str(this, R.string.toast_quick_tile_added), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this, hintMsg, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, hintMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun createAppAdapter(): BaseAdapter {
        class RowHolder(
            val row: LinearLayout,
            val img: ImageView,
            val nameTv: TextView,
            val pkgTv: TextView,
            val scaleBadge: TextView,
            val pinBtn: TextView
        )

        return object : BaseAdapter() {
            override fun getCount() = filteredApps.size
            override fun getItem(pos: Int) = filteredApps[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val item = filteredApps[pos]
                val effectiveScale = if (item.customScale > 0) item.customScale else selectedFactor
                val accentCol = UiTheme.accent(this@MainActivity)
                val holder: RowHolder

                if (convertView == null) {
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(18, 14, 18, 14)
                    }
                    val img = ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(84, 84).apply { rightMargin = 18 }
                    }
                    val textCol = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val nameTv = TextView(this@MainActivity).apply {
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                    }
                    val pkgTv = TextView(this@MainActivity).apply {
                        textSize = 10.5f
                        setTextColor(UiTheme.textSecondary(this@MainActivity))
                    }
                    textCol.addView(nameTv)
                    textCol.addView(pkgTv)

                    val scaleBadge = TextView(this@MainActivity).apply {
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(16, 9, 16, 9)
                        background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 10f, 1, UiTheme.strokeSubtle(this@MainActivity))
                    }
                    val pinBtn = TextView(this@MainActivity).apply {
                        textSize = 18f
                        setPadding(16, 8, 4, 8)
                    }

                    row.addView(img)
                    row.addView(textCol)
                    row.addView(scaleBadge)
                    row.addView(pinBtn)

                    holder = RowHolder(row, img, nameTv, pkgTv, scaleBadge, pinBtn)
                    row.tag = holder
                } else {
                    holder = convertView.tag as RowHolder
                }

                holder.row.background = UiTheme.roundBox(
                    if (item.isPinned) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgCard(this@MainActivity),
                    14f,
                    1,
                    if (item.isPinned) accentCol else UiTheme.strokeSubtle(this@MainActivity)
                )
                holder.img.setImageDrawable(item.icon)
                holder.nameTv.text = item.name
                holder.nameTv.setTextColor(UiTheme.textPrimary(this@MainActivity))
                holder.pkgTv.text = item.pkg

                val labelStr = "${effectiveScale / 100}.${effectiveScale % 100}x"
                holder.scaleBadge.text = if (item.customScale > 0) "🎯 $labelStr" else labelStr
                holder.scaleBadge.setTextColor(if (item.customScale > 0) accentCol else UiTheme.textSecondary(this@MainActivity))
                holder.scaleBadge.setOnClickListener { cyclePerGameScale(item) }

                holder.pinBtn.text = if (item.isPinned) "★" else "☆"
                holder.pinBtn.setTextColor(if (item.isPinned) accentCol else UiTheme.textSecondary(this@MainActivity))
                holder.pinBtn.setOnClickListener { togglePin(item) }

                holder.row.setOnClickListener { launchGameByPkg(item.pkg, item.name, effectiveScale) }
                holder.row.setOnLongClickListener {
                    createPinnedShortcut(item, effectiveScale)
                    true
                }
                return holder.row
            }
        }
    }

    override fun onDestroy() {
        allApps.clear()
        filteredApps.clear()
        super.onDestroy()
    }

    private fun showThermalInspectorDialog() {
        if (!ShizukuHelper.ensureReady(this)) return
        Toast.makeText(this, Lang.str(this, R.string.toast_scanning_sensors), Toast.LENGTH_SHORT).show()
        ShizukuHelper.scanThermalZones(
            onSuccess = { count, report ->
                val finalText = report.ifEmpty { Lang.str(this, R.string.no_sensors_found) }
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(Lang.str(this, R.string.dlg_sensors_title, count))
                        .setMessage(finalText)
                        .setPositiveButton(Lang.str(this, R.string.btn_close), null)
                        .show()
                }
            },
            onError = { err ->
                runOnUiThread { Toast.makeText(this, "Error: $err", Toast.LENGTH_SHORT).show() }
            }
        )
    }

    private fun notifyOverlayStyleUpdated() {
        if (FpsOverlayService.isServiceRunning) {
            startService(Intent(this, FpsOverlayService::class.java).apply { action = "UPDATE_STYLE" })
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && handleIncomingIntent(intent)) finish()
    }

    private fun handleIncomingIntent(intent: Intent): Boolean {
        val action = intent.getStringExtra("action_type") ?: return false
        if (action == "RESET_NOW") {
            if (ShizukuHelper.ensureReady(this)) {
                ShizukuHelper.resetDisplay()
                cancelActiveNotification()
                stopService(Intent(this, FpsOverlayService::class.java))
                Toast.makeText(this, Lang.str(this, R.string.toast_display_reset_short), Toast.LENGTH_SHORT).show()
            }
            return true
        } else if (action == "QUICK_LAUNCH") {
            val pkg = intent.getStringExtra("target_pkg") ?: return true
            val name = intent.getStringExtra("target_name") ?: pkg
            val savedCustom = prefsMgr.getGameScale(pkg)
            val factor = if (savedCustom > 0) savedCustom else prefsMgr.savedFactor
            launchGameByPkg(pkg, name, factor)
            return true
        }
        return false
    }

    private fun cyclePerGameScale(item: AppItem) {
        val cycle = listOf(0, 110, 115, 120, 125)
        item.customScale = cycle[(cycle.indexOf(item.customScale) + 1) % cycle.size]
        prefsMgr.setGameScale(item.pkg, item.customScale)

        val msg = if (item.customScale == 0) {
            Lang.str(this, R.string.toast_scale_global, item.name, selectedFactor)
        } else {
            val sc = "${item.customScale / 100}.${item.customScale % 100}x"
            Lang.str(this, R.string.toast_scale_custom, sc, item.name)
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        adapter.notifyDataSetChanged()
    }

    private fun createPinnedShortcut(item: AppItem, factor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = getSystemService(ShortcutManager::class.java)
            if (sm != null && sm.isRequestPinShortcutSupported) {
                val bmp = drawableToBitmap(item.icon, 96)
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("action_type", "QUICK_LAUNCH")
                    putExtra("target_pkg", item.pkg)
                    putExtra("target_name", item.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val scaleStr = "${factor / 100}.${factor % 100}x"
                val info = ShortcutInfo.Builder(this, "upscale_${item.pkg}")
                    .setShortLabel("⚡ ${item.name}")
                    .setLongLabel("${item.name} ($scaleStr)")
                    .setIcon(Icon.createWithBitmap(bmp))
                    .setIntent(launchIntent)
                    .build()
                sm.requestPinShortcut(info, null)
                Toast.makeText(this, Lang.str(this, R.string.toast_shortcut_req), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable, targetSize: Int = 64): Bitmap {
        val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bmp
    }

    private fun updateResolutionSummary() {
        if (!::resSummaryTv.isInitialized) return
        val newW = physW * selectedFactor / 100
        val newH = physH * selectedFactor / 100
        val newDen = physDen * selectedFactor / 100
        resSummaryTv.text = Lang.str(this, R.string.preview_display_fmt, physW, physH, physDen, newW, newH, newDen)
    }

    private fun showActiveNotification(gameName: String, factor: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "upscaler_active_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, Lang.str(this, R.string.notif_channel_upscale), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val resetIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("action_type", "RESET_NOW")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(this, 99, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val scaleStr = "${factor / 100}.${factor % 100}x"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(Lang.str(this, R.string.notif_upscale_title, scaleStr, gameName))
            .setContentText(Lang.str(this, R.string.notif_upscale_desc))
            .setContentIntent(pi)
            .setOngoing(true)
        nm.notify(1001, builder.build())
    }

    private fun cancelActiveNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1001)
    }

    private fun togglePin(item: AppItem) {
        if (pinnedPkgs.contains(item.pkg)) {
            pinnedPkgs.remove(item.pkg)
            item.isPinned = false
        } else {
            pinnedPkgs.add(item.pkg)
            item.isPinned = true
        }
        prefsMgr.savePinnedPackages(pinnedPkgs)
        sortAndFilterApps()
        rebuildPinnedGamesStrip()
    }

    private fun sortAndFilterApps() {
        allApps.forEach {
            it.isPinned = pinnedPkgs.contains(it.pkg)
            it.customScale = prefsMgr.getGameScale(it.pkg)
        }
        allApps.sortWith(compareByDescending<AppItem> { it.isPinned }.thenBy { it.name.lowercase() })

        val q = currentQuery.trim().lowercase()
        filteredApps.clear()
        val baseList = if (showPinnedOnly && q.isEmpty()) allApps.filter { it.isPinned } else allApps
        if (q.isEmpty()) {
            filteredApps.addAll(baseList)
        } else {
            filteredApps.addAll(baseList.filter { it.name.lowercase().contains(q) || it.pkg.lowercase().contains(q) })
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadInstalledApps() {
        thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolves = pm.queryIntentActivities(intent, 0)
            val temp = resolves.mapNotNull { r ->
                val pkg = r.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                val scaledIcon = BitmapDrawable(resources, drawableToBitmap(r.loadIcon(pm), 64))
                AppItem(
                    r.loadLabel(pm).toString(),
                    pkg,
                    scaledIcon,
                    pinnedPkgs.contains(pkg),
                    prefsMgr.getGameScale(pkg)
                )
            }.distinctBy { it.pkg }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    allApps.clear()
                    allApps.addAll(temp)
                    sortAndFilterApps()
                    rebuildPinnedGamesStrip()
                }
            }
        }
    }

    private fun launchGameByPkg(pkg: String, gameName: String, factor: Int) {
        if (!ShizukuHelper.ensureReady(this)) return
        val scaleStr = "${factor / 100}.${factor % 100}x"
        Toast.makeText(this, Lang.str(this, R.string.toast_boosting, gameName, scaleStr), Toast.LENGTH_SHORT).show()
        showActiveNotification(gameName, factor)

        if (enableFpsOverlay) {
            if (Settings.canDrawOverlays(this)) {
                val svcIntent = Intent(this, FpsOverlayService::class.java).apply {
                    putExtra("target_pkg", pkg)
                    putExtra("game_name", gameName)
                    putExtra("scale_label", scaleStr)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svcIntent) else startService(svcIntent)
            } else {
                Toast.makeText(this, Lang.str(this, R.string.toast_missing_overlay), Toast.LENGTH_LONG).show()
            }
        }

        ShizukuHelper.launchGameWithUpscale(pkg, factor)
        finish()
    }
}

// =========================================================================
// CUSTOM CANVAS VIEWS (RAM Arc Gauge & Live Monitor Waveform)
// =========================================================================
class RamArcGaugeView(context: Context) : View(context) {
    private var percent = 50
    private val accentCol = UiTheme.accent(context)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.gaugeTrack(context)
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentCol
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.textPrimary(context)
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.textSecondary(context)
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val oval = RectF()

    fun setPercent(pct: Int) {
        percent = pct.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 16f
        oval.set(pad, pad, width - pad, height - pad)
        // 260-degree open bottom arc like Razer Cortex dashboard
        canvas.drawArc(oval, 140f, 260f, false, trackPaint)
        val sweep = 260f * (percent / 100f)
        canvas.drawArc(oval, 140f, sweep, false, progressPaint)

        val cx = width / 2f
        val cy = height / 2f + 12f
        canvas.drawText("$percent%", cx, cy, valuePaint)
        canvas.drawText("RAM", cx, height - 14f, subPaint)
    }
}

class LiveSparklineView(context: Context) : View(context) {
    private val accentCol = UiTheme.accent(context)
    private val points = floatArrayOf(0.28f, 0.30f, 0.25f, 0.32f, 0.68f, 0.24f, 0.27f, 0.31f, 0.26f, 0.78f, 0.29f, 0.25f, 0.33f, 0.28f, 0.62f, 0.26f, 0.30f, 0.28f)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentCol
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val linePath = Path()
        val areaPath = Path()
        val count = points.size

        for (i in 0 until count) {
            val x = (i.toFloat() / (count - 1)) * w
            val y = h * (1f - points[i])
            if (i == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, h)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(w, h)
        areaPath.close()

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(65, Color.red(accentCol), Color.green(accentCol), Color.blue(accentCol)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(areaPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
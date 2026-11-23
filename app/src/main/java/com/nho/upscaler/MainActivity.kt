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
import android.graphics.Paint
import android.graphics.RectF
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
import java.io.File
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

    // Top Navigation Tabs
    private lateinit var tabSystemBtn: TextView
    private lateinit var tabResolutionBtn: TextView
    private lateinit var tabHudBtn: TextView

    // 3 Main Tab Pages
    private lateinit var systemPage: ScrollView
    private lateinit var resolutionPage: ScrollView
    private lateinit var hudPage: ScrollView

    // Tab 1: System Telemetry Views
    private lateinit var shizukuBannerCard: LinearLayout
    private lateinit var shizukuStatusTv: TextView
    private lateinit var shizukuActionBtn: TextView

    private lateinit var ramUsageTv: TextView
    private lateinit var ramBarView: ModernRamBarView
    private lateinit var batTempTv: TextView
    private lateinit var batPctTv: TextView
    private lateinit var batWattTv: TextView
    private lateinit var socTempTv: TextView
    private lateinit var cpuFreqTv: TextView
    private lateinit var resSummaryTv: TextView
    private lateinit var pinnedStripRow: LinearLayout

    // Tab 2: Resolution & SSAA Views
    private lateinit var ssaaPreviewNativeTv: TextView
    private lateinit var ssaaPreviewTargetTv: TextView
    private val ssaaCards = mutableListOf<LinearLayout>()
    private val ssaaRadios = mutableListOf<TextView>()
    private lateinit var autoHudToggleBtn: TextView

    // Tab 3: HUD Tuning Views
    private var liveHudMockTv: TextView? = null
    private var testHudToggleBtn: TextView? = null

    private var activeTab = 0
    private var selectedFactor = 125
    private var showPinnedOnly = false
    private var enableFpsOverlay = true
    private var overlaySizeLevel = 1
    private var overlayBgMode = 1
    private var hudPollMode = 0
    private var overlayVertical = false
    private var currentQuery = ""
    private var physW = 1080
    private var physH = 2400
    private var physDen = 420

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
            setPadding(24, 30, 24, 16)
        }

        // =====================================================================
        // TOP APP BAR: Brand Title + Theme Mode Switcher + Language Switcher
        // =====================================================================
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }

        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandCol.addView(TextView(this).apply {
            text = "GameUpscaler"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        brandCol.addView(TextView(this).apply {
            text = "PRO"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textOnAccent(this@MainActivity))
            background = UiTheme.pillBox(UiTheme.accent(this@MainActivity))
            setPadding(12, 3, 12, 3)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 12
            }
        })
        headerBar.addView(brandCol)

        // Theme Mode Toggle (Auto / Dark / Light)
        val themeBtn = TextView(this).apply {
            text = when (prefsMgr.themeMode) {
                1 -> Lang.str(this@MainActivity, R.string.theme_dark)
                2 -> Lang.str(this@MainActivity, R.string.theme_light)
                else -> Lang.str(this@MainActivity, R.string.theme_auto)
            }
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(14, 8, 14, 8)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.themeMode = (prefsMgr.themeMode + 1) % 3
                recreate()
            }
        }
        headerBar.addView(themeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = 8
        })

        // Language Toggle (Auto / VI / EN)
        val langBtn = TextView(this).apply {
            text = Lang.modeBadge(this@MainActivity)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(14, 8, 14, 8)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.appLang = (prefsMgr.appLang + 1) % 3
                notifyOverlayStyleUpdated()
                recreate()
            }
        }
        headerBar.addView(langBtn)
        root.addView(headerBar)

        // =====================================================================
        // MODERN 3-TAB SEGMENTED BAR: [📊 Hệ thống] [⚡ Độ phân giải] [🎛️ Thanh đo HUD]
        // =====================================================================
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 4, 4, 4)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 16f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        tabSystemBtn = makeTabButton(Lang.str(this, R.string.tab_system)) { switchTab(0) }
        tabResolutionBtn = makeTabButton(Lang.str(this, R.string.tab_resolution)) { switchTab(1) }
        tabHudBtn = makeTabButton(Lang.str(this, R.string.tab_hud)) { switchTab(2) }

        tabBar.addView(tabSystemBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 })
        tabBar.addView(tabResolutionBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 })
        tabBar.addView(tabHudBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // Build Pages
        systemPage = buildSystemMonitorPage()
        resolutionPage = buildResolutionPage()
        hudPage = buildHudTuningPage()

        root.addView(systemPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(resolutionPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(hudPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
            setOnClickListener { onClick() }
        }
    }

    private fun switchTab(idx: Int) {
        activeTab = idx
        val accentCol = UiTheme.accent(this)
        val activeTxt = UiTheme.textOnAccent(this)
        val idleTxt = UiTheme.textSecondary(this)

        val tabs = listOf(tabSystemBtn, tabResolutionBtn, tabHudBtn)
        tabs.forEachIndexed { i, btn ->
            val active = (i == idx)
            btn.setTextColor(if (active) activeTxt else idleTxt)
            btn.background = if (active) UiTheme.roundBox(accentCol, 12f) else null
        }

        systemPage.visibility = if (idx == 0) View.VISIBLE else View.GONE
        resolutionPage.visibility = if (idx == 1) View.VISIBLE else View.GONE
        hudPage.visibility = if (idx == 2) View.VISIBLE else View.GONE

        if (idx == 0) refreshLiveDashboardStats()
        if (idx == 1) updateResolutionSummary()
        if (idx == 2) updateLiveHudMockup()
    }

    // =========================================================================
    // TAB 1: THEO DÕI HỆ THỐNG (SYSTEM MONITOR / DASHBOARD)
    // =========================================================================
    private fun buildSystemMonitorPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val accentCol = UiTheme.accent(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        // 1. Shizuku Status / Quick Onboarding Banner
        shizukuBannerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 16f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        shizukuStatusTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.shizuku_banner_ok)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.statusOk(this@MainActivity))
        }
        shizukuBannerCard.addView(shizukuStatusTv)

        shizukuActionBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_launch_shizuku)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textOnAccent(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(16, 9, 16, 9)
            background = UiTheme.roundBox(accentCol, 10f)
            visibility = View.GONE
            setOnClickListener { launchShizukuApp() }
        }
        shizukuBannerCard.addView(shizukuActionBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 10
        })
        container.addView(shizukuBannerCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 2. Hardware Telemetry Card (RAM Progress + Battery + SoC)
        val hwCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 18)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }

        // Section A: RAM
        val ramHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ramHeader.addView(TextView(this).apply {
            text = "💾  " + Lang.str(this@MainActivity, R.string.card_ram_title)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        ramUsageTv = TextView(this).apply {
            text = "Loading..."
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        }
        ramHeader.addView(ramUsageTv)
        hwCard.addView(ramHeader)

        ramBarView = ModernRamBarView(this)
        hwCard.addView(ramBarView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16).apply {
            topMargin = 10
            bottomMargin = 18
        })

        // Section B: 2-Column Battery & SoC Status
        val teleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Battery Box
        val batBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 14f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        batBox.addView(TextView(this).apply {
            text = "🔋  " + Lang.str(this@MainActivity, R.string.card_battery_title)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        })
        val batValueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 0)
        }
        batTempTv = TextView(this).apply {
            text = "--°C"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        }
        batPctTv = TextView(this).apply {
            text = " • --%"
            textSize = 13f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        }
        batValueRow.addView(batTempTv)
        batValueRow.addView(batPctTv)
        batBox.addView(batValueRow)

        batWattTv = TextView(this).apply {
            text = "0.0W"
            textSize = 11f
            setTextColor(accentCol)
            setPadding(0, 2, 0, 0)
        }
        batBox.addView(batWattTv)
        teleRow.addView(batBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 10 })

        // SoC Box
        val socBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 14f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        socBox.addView(TextView(this).apply {
            text = "🌡️  " + Lang.str(this@MainActivity, R.string.card_soc_title)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        })
        socTempTv = TextView(this).apply {
            text = "--°C"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(0, 4, 0, 0)
        }
        socBox.addView(socTempTv)

        cpuFreqTv = TextView(this).apply {
            text = "CPU Clock Peak"
            textSize = 11f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 2, 0, 0)
        }
        socBox.addView(cpuFreqTv)
        teleRow.addView(socBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hwCard.addView(teleRow)

        container.addView(hwCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 3. Display Resolution & Active State Card
        val displayCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val dispTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dispTop.addView(TextView(this).apply {
            text = "🖥️  " + Lang.str(this@MainActivity, R.string.card_display_title)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        dispTop.addView(TextView(this).apply {
            text = "↺  " + Lang.str(this@MainActivity, R.string.dash_reset)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(12, 6, 12, 6)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 10f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                if (ShizukuHelper.ensureReady(this@MainActivity)) {
                    ShizukuHelper.resetDisplay()
                    cancelActiveNotification()
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                    Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
                }
            }
        })
        displayCard.addView(dispTop)

        resSummaryTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.reading_display)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            setPadding(0, 10, 0, 4)
        }
        displayCard.addView(resSummaryTv)
        container.addView(displayCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 4. Quick Actions / Utility Tools
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(makeActionCard("📈", Lang.str(this, R.string.dash_insights)) {
            startActivity(Intent(this, PerfChartActivity::class.java))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        actionRow.addView(makeActionCard("🌡️", Lang.str(this, R.string.btn_sensors)) {
            showThermalInspectorDialog()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        actionRow.addView(makeActionCard("🎛️", Lang.str(this, R.string.btn_quick_tile)) {
            requestAddQuickTile()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        container.addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 5. Pinned Quick-Launch Games Strip
        val gamesHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 10)
        }
        gamesHeader.addView(TextView(this).apply {
            text = "⭐  " + Lang.str(this@MainActivity, R.string.dash_my_games)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        gamesHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_manage) + " ➔"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            setOnClickListener { switchTab(1) }
        })
        container.addView(gamesHeader)

        val stripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, 10)
        }
        pinnedStripRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stripScroll.addView(pinnedStripRow)
        container.addView(stripScroll)

        scroll.addView(container)
        return scroll
    }

    private fun makeActionCard(icon: String, title: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 14)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 14f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
                gravity = Gravity.CENTER
                setSingleLine(true)
                setPadding(0, 6, 0, 0)
            })
        }
    }

    // =========================================================================
    // TAB 2: CHỌN ĐỘ PHÂN GIẢI & KHO GAME (RESOLUTION & GAME LIBRARY)
    // =========================================================================
    private fun buildResolutionPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val accentCol = UiTheme.accent(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        // 1. Beginner-Friendly SSAA Explanation Card
        val guideCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        guideCard.addView(TextView(this).apply {
            text = "⚡  " + Lang.str(this@MainActivity, R.string.ssaa_guide_title)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        guideCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_guide_desc)
            textSize = 11.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 6, 0, 12)
            setLineSpacing(3f, 1.1f)
        })

        // Display Resolution Comparison Badges
        val cmpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nativeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        nativeCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_native_label)
            textSize = 10f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        })
        ssaaPreviewNativeTv = TextView(this).apply {
            text = "${physW}x${physH} (${physDen} DPI)"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(0, 2, 0, 0)
        }
        nativeCol.addView(ssaaPreviewNativeTv)
        cmpRow.addView(nativeCol)

        cmpRow.addView(TextView(this).apply {
            text = "➔"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            setPadding(10, 0, 10, 0)
        })

        val targetCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = UiTheme.roundBox(UiTheme.bgTileActive(this@MainActivity), 12f, 1, accentCol)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        targetCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_target_label)
            textSize = 10f
            setTextColor(accentCol)
        })
        ssaaPreviewTargetTv = TextView(this).apply {
            text = "Calculating..."
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(0, 2, 0, 0)
        }
        targetCol.addView(ssaaPreviewTargetTv)
        cmpRow.addView(targetCol)
        guideCard.addView(cmpRow)

        container.addView(guideCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 2. Global SSAA Scaling Selector (4 Modern Preset Cards)
        val factorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        factorContainer.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_scale_select_title)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 0, 0, 12)
        })

        val presets = listOf(
            Triple(110, R.string.ssaa_preset_110_title, R.string.ssaa_preset_110_desc),
            Triple(115, R.string.ssaa_preset_115_title, R.string.ssaa_preset_115_desc),
            Triple(120, R.string.ssaa_preset_120_title, R.string.ssaa_preset_120_desc),
            Triple(125, R.string.ssaa_preset_125_title, R.string.ssaa_preset_125_desc)
        )

        ssaaCards.clear()
        ssaaRadios.clear()

        presets.forEach { (factor, titleRes, descRes) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 12, 14, 12)
                setOnClickListener { selectGlobalFactor(factor) }
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 10 }
            }
            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, titleRes)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
            })
            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, descRes)
                textSize = 11f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                setPadding(0, 2, 0, 0)
            })
            card.addView(textCol)

            val radioIndicator = TextView(this).apply {
                text = if (factor == selectedFactor) "●" else "○"
                textSize = 18f
                setTextColor(if (factor == selectedFactor) accentCol else UiTheme.textSecondary(this@MainActivity))
            }
            card.addView(radioIndicator)

            ssaaCards.add(card)
            ssaaRadios.add(radioIndicator)
            factorContainer.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8
            })
        }
        refreshSsaaCardStyles()
        container.addView(factorContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 3. Quick Options: Auto HUD Checkbox + Reset Display
        val optsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val autoHudRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                enableFpsOverlay = !enableFpsOverlay
                prefsMgr.enableFpsOverlay = enableFpsOverlay
                if (enableFpsOverlay && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_grant_overlay_long), Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                updateAutoHudToggleText()
            }
        }
        val autoHudCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 12 }
        }
        autoHudCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.opt_auto_hud_title)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        autoHudCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.opt_auto_hud_desc)
            textSize = 10.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 2, 0, 0)
        })
        autoHudRow.addView(autoHudCol)

        autoHudToggleBtn = TextView(this).apply {
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(14, 6, 14, 6)
        }
        updateAutoHudToggleText()
        autoHudRow.addView(autoHudToggleBtn)
        optsCard.addView(autoHudRow)
        container.addView(optsCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 4. Game & Application Library (Search + Filter + Embedded App List)
        val gameLibCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        gameLibCard.addView(TextView(this).apply {
            text = "🎮  " + Lang.str(this@MainActivity, R.string.sec_game_list)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 0, 0, 10)
        })

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchBox = EditText(this).apply {
            hint = Lang.str(this@MainActivity, R.string.hint_search)
            textSize = 12f
            setSingleLine(true)
            setHintTextColor(UiTheme.textSecondary(this@MainActivity))
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(16, 10, 16, 10)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 12f, 1, UiTheme.strokeSubtle(this@MainActivity))
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
            setPadding(16, 8, 16, 8)
            fun updateStyle() {
                setTextColor(if (showPinnedOnly) accentCol else UiTheme.textSecondary(this@MainActivity))
                background = UiTheme.roundBox(
                    if (showPinnedOnly) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgTile(this@MainActivity),
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
        gameLibCard.addView(filterRow)

        gameLibCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.subtitle_hint)
            textSize = 10f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(4, 8, 4, 10)
        })

        val embeddedAppContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        adapter = createEmbeddedAppAdapter(embeddedAppContainer)
        gameLibCard.addView(embeddedAppContainer)

        container.addView(gameLibCard)
        scroll.addView(container)
        return scroll
    }

    private fun selectGlobalFactor(factor: Int) {
        selectedFactor = factor
        prefsMgr.savedFactor = factor
        refreshSsaaCardStyles()
        updateResolutionSummary()
        adapter.notifyDataSetChanged()
    }

    private fun refreshSsaaCardStyles() {
        val accentCol = UiTheme.accent(this)
        val factors = listOf(110, 115, 120, 125)
        factors.forEachIndexed { idx, factor ->
            val isSelected = (factor == selectedFactor)
            if (idx < ssaaCards.size && idx < ssaaRadios.size) {
                ssaaCards[idx].background = UiTheme.roundBox(
                    if (isSelected) UiTheme.bgTileActive(this) else UiTheme.bgTile(this),
                    14f,
                    1,
                    if (isSelected) accentCol else UiTheme.strokeSubtle(this)
                )
                ssaaRadios[idx].text = if (isSelected) "●" else "○"
                ssaaRadios[idx].setTextColor(if (isSelected) accentCol else UiTheme.textSecondary(this))
            }
        }
    }

    private fun updateAutoHudToggleText() {
        val accentCol = UiTheme.accent(this)
        autoHudToggleBtn.text = if (enableFpsOverlay) "ON" else "OFF"
        autoHudToggleBtn.setTextColor(if (enableFpsOverlay) UiTheme.textOnAccent(this) else UiTheme.textSecondary(this))
        autoHudToggleBtn.background = UiTheme.roundBox(
            if (enableFpsOverlay) accentCol else UiTheme.bgTile(this),
            10f,
            if (enableFpsOverlay) 0 else 1,
            UiTheme.strokeSubtle(this)
        )
    }

    // =========================================================================
    // TAB 3: TINH CHỈNH THANH ĐO HUD (HUD OVERLAY TUNING)
    // =========================================================================
    private fun buildHudTuningPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val accentCol = UiTheme.accent(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        // 1. Live HUD Preview Stage Card
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val prevHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prevHeader.addView(TextView(this).apply {
            text = "👁️  " + Lang.str(this@MainActivity, R.string.sec_hud_preview)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        prevHeader.addView(TextView(this).apply {
            text = "↺  " + Lang.str(this@MainActivity, R.string.btn_reset_pos)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(12, 6, 12, 6)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 10f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.resetHudPosition()
                if (FpsOverlayService.isServiceRunning) {
                    startService(Intent(this@MainActivity, FpsOverlayService::class.java).apply { action = "RESET_POS" })
                }
                Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_pos_reset), Toast.LENGTH_SHORT).show()
            }
        })
        previewCard.addView(prevHeader)

        // Stage frame simulating game screen
        val stageFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 22, 16, 22)
            background = UiTheme.roundBox(Color.parseColor("#12161D"), 14f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val mockTv = TextView(this).apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_EMERALD)
        }
        liveHudMockTv = mockTv
        stageFrame.addView(mockTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewCard.addView(stageFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 })

        // Action: Toggle Floating Test HUD
        testHudToggleBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, if (FpsOverlayService.isServiceRunning) R.string.btn_test_hud_off else R.string.btn_test_hud_on)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (FpsOverlayService.isServiceRunning) UiTheme.ACCENT_RED else accentCol)
            gravity = Gravity.CENTER
            setPadding(16, 10, 16, 10)
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 12f, 1, if (FpsOverlayService.isServiceRunning) UiTheme.ACCENT_RED else accentCol)
            setOnClickListener {
                toggleHudPreviewService()
                postDelayed({ updateTestHudButtonText() }, 250)
            }
        }
        previewCard.addView(testHudToggleBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12
        })

        container.addView(previewCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 2. HUD Appearance & Layout Options Card
        val styleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        styleCard.addView(TextView(this).apply {
            text = "🎨  " + Lang.str(this@MainActivity, R.string.sec_hud_style)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 0, 0, 10)
        })

        // Option: Layout Orientation
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.lbl_layout_orientation)
            textSize = 11f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 4, 0, 6)
        })
        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.btn_orient_horiz),
                1 to Lang.str(this, R.string.btn_orient_vert)
            ),
            selectedIndex = if (overlayVertical) 1 else 0,
            padTop = 0,
            padBottom = 12
        ) { idx ->
            overlayVertical = (idx == 1)
            prefsMgr.overlayVertical = overlayVertical
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        // Option: Text Size
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.lbl_font_size)
            textSize = 11f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 4, 0, 6)
        })
        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.size_tiny),
                1 to Lang.str(this, R.string.size_small),
                2 to Lang.str(this, R.string.size_medium),
                3 to Lang.str(this, R.string.size_large)
            ),
            selectedIndex = overlaySizeLevel,
            padTop = 0,
            padBottom = 12
        ) { lvl ->
            overlaySizeLevel = lvl
            prefsMgr.overlaySizeLevel = lvl
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        // Option: Background Opacity
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.lbl_bg_style)
            textSize = 11f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 4, 0, 6)
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

        // Option: Polling Interval
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_poll)
            textSize = 11f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 4, 0, 6)
        })
        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.poll_auto),
                1 to Lang.str(this, R.string.poll_300ms),
                2 to Lang.str(this, R.string.poll_1s),
                3 to Lang.str(this, R.string.poll_2s)
            ),
            selectedIndex = hudPollMode,
            padTop = 0,
            padBottom = 4
        ) { mode ->
            hudPollMode = mode
            prefsMgr.hudPollMode = mode
            notifyOverlayStyleUpdated()
        })
        container.addView(styleCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 3. Telemetry Metrics Selection (Clean Modern Switch Rows)
        val metricsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 10)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 18f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        metricsCard.addView(TextView(this).apply {
            text = "📊  " + Lang.str(this@MainActivity, R.string.sec_hud_metrics)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 0, 0, 8)
        })

        data class MetricSpec(val key: String, val icon: String, val titleId: Int, val descId: Int, val defVal: Boolean)
        val specs = listOf(
            MetricSpec("m_fps", "⚡", R.string.metric_fps, R.string.desc_fps, true),
            MetricSpec("m_watt", "🔌", R.string.metric_watt, R.string.desc_watt, true),
            MetricSpec("m_gpu", "🎮", R.string.metric_vram, R.string.desc_vram, true),
            MetricSpec("m_bat_temp", "🔋", R.string.metric_bat_temp, R.string.desc_bat_temp, true),
            MetricSpec("m_soc_temp", "🌡️", R.string.metric_soc_temp, R.string.desc_soc_temp, true),
            MetricSpec("m_cpu_temp", "🔥", R.string.metric_cpu_temp, R.string.desc_cpu_temp, false),
            MetricSpec("m_gpu_temp", "📟", R.string.metric_gpu_temp, R.string.desc_gpu_temp, false),
            MetricSpec("m_cpu_freq", "⚙️", R.string.metric_cpu_freq, R.string.desc_cpu_freq, false),
            MetricSpec("m_ram", "💾", R.string.metric_ram, R.string.desc_ram, false),
            MetricSpec("m_ddr_temp", "🧬", R.string.metric_ddr_temp, R.string.desc_ddr_temp, false),
            MetricSpec("m_skin_temp", "📱", R.string.metric_skin_temp, R.string.desc_skin_temp, false),
            MetricSpec("m_cam_temp", "📷", R.string.metric_cam_temp, R.string.desc_cam_temp, false)
        )

        specs.forEachIndexed { idx, spec ->
            var enabled = prefsMgr.getMetric(spec.key, spec.defVal)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 12, 4, 12)
            }
            val iconTv = TextView(this).apply {
                text = spec.icon
                textSize = 16f
                setPadding(0, 0, 14, 0)
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
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(12, 5, 12, 5)
                fun updateState() {
                    text = if (enabled) "ON" else "OFF"
                    setTextColor(if (enabled) UiTheme.textOnAccent(this@MainActivity) else UiTheme.textSecondary(this@MainActivity))
                    background = UiTheme.roundBox(
                        if (enabled) accentCol else UiTheme.bgTile(this@MainActivity),
                        8f,
                        if (enabled) 0 else 1,
                        UiTheme.strokeSubtle(this@MainActivity)
                    )
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
        container.addView(metricsCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
        })

        // 4. Practical HUD Usage Tips Card
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 14f, 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        tipCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hud_guide_tips)
            textSize = 11f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setLineSpacing(3f, 1.15f)
        })
        container.addView(tipCard)

        scroll.addView(container)
        return scroll
    }

    private fun updateTestHudButtonText() {
        val btn = testHudToggleBtn ?: return
        val isRunning = FpsOverlayService.isServiceRunning
        val accentCol = UiTheme.accent(this)
        btn.text = Lang.str(this, if (isRunning) R.string.btn_test_hud_off else R.string.btn_test_hud_on)
        btn.setTextColor(if (isRunning) UiTheme.ACCENT_RED else accentCol)
        btn.background = UiTheme.roundBox(
            UiTheme.bgTile(this),
            12f,
            1,
            if (isRunning) UiTheme.ACCENT_RED else accentCol
        )
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

    // =========================================================================
    // EMBEDDED APP LIST & ADAPTER
    // =========================================================================
    private fun createEmbeddedAppAdapter(parentLayout: LinearLayout): BaseAdapter {
        return object : BaseAdapter() {
            override fun getCount() = filteredApps.size
            override fun getItem(pos: Int) = filteredApps[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun notifyDataSetChanged() {
                super.notifyDataSetChanged()
                rebuildEmbeddedAppList(parentLayout)
            }
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                return buildAppRow(filteredApps[pos])
            }
        }
    }

    private fun rebuildEmbeddedAppList(parentLayout: LinearLayout) {
        parentLayout.removeAllViews()
        val accentCol = UiTheme.accent(this)

        if (filteredApps.isEmpty()) {
            parentLayout.addView(TextView(this).apply {
                text = "No apps found"
                textSize = 12f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            })
            return
        }

        val limit = minOf(filteredApps.size, 50)
        for (i in 0 until limit) {
            val item = filteredApps[i]
            val row = buildAppRow(item)
            parentLayout.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8
            })
        }
    }

    private fun buildAppRow(item: AppItem): View {
        val accentCol = UiTheme.accent(this)
        val effectiveScale = if (item.customScale > 0) item.customScale else selectedFactor

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 10, 14, 10)
            background = UiTheme.roundBox(
                if (item.isPinned) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgTile(this@MainActivity),
                12f,
                1,
                if (item.isPinned) accentCol else UiTheme.strokeSubtle(this@MainActivity)
            )
            setOnClickListener { launchGameByPkg(item.pkg, item.name, effectiveScale) }
            setOnLongClickListener {
                createPinnedShortcut(item, effectiveScale)
                true
            }
        }

        val img = ImageView(this).apply {
            setImageDrawable(item.icon)
            layoutParams = LinearLayout.LayoutParams(76, 76).apply { rightMargin = 14 }
        }
        row.addView(img)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 }
        }
        textCol.addView(TextView(this).apply {
            text = item.name
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(this).apply {
            text = item.pkg
            textSize = 10f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(textCol)

        // Custom Scale Badge (Tap to cycle)
        val labelStr = "${effectiveScale / 100}.${effectiveScale % 100}x"
        val scaleBadge = TextView(this).apply {
            text = if (item.customScale > 0) "🎯 $labelStr" else labelStr
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (item.customScale > 0) accentCol else UiTheme.textSecondary(this@MainActivity))
            setPadding(12, 6, 12, 6)
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), 8f, 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener { cyclePerGameScale(item) }
        }
        row.addView(scaleBadge)

        // Pin Button (★)
        val pinBtn = TextView(this).apply {
            text = if (item.isPinned) "★" else "☆"
            textSize = 18f
            setTextColor(if (item.isPinned) accentCol else UiTheme.textSecondary(this@MainActivity))
            setPadding(12, 6, 4, 6)
            setOnClickListener { togglePin(item) }
        }
        row.addView(pinBtn)

        return row
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
                layoutParams = LinearLayout.LayoutParams(92, 92)
            }
            val lbl = TextView(this).apply {
                text = item.name
                textSize = 10.5f
                setTextColor(UiTheme.textPrimary(this@MainActivity))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 4 }
            }
            col.addView(img)
            col.addView(lbl)
            pinnedStripRow.addView(col, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })
        }

        // "+ Add" Slot
        val addCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(6, 4, 6, 4)
            setOnClickListener { switchTab(1) }
        }
        val plusBox = TextView(this).apply {
            text = "+"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            gravity = Gravity.CENTER
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), 16f, 1, UiTheme.strokeSubtle(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(92, 92)
        }
        val addLbl = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_add_game)
            textSize = 10.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 4 }
        }
        addCol.addView(plusBox)
        addCol.addView(addLbl)
        pinnedStripRow.addView(addCol)
    }

    private fun refreshLiveDashboardStats() {
        val shizukuOk = ShizukuHelper.isBinderAlive()
        if (::shizukuStatusTv.isInitialized && ::shizukuActionBtn.isInitialized) {
            if (shizukuOk) {
                shizukuStatusTv.text = Lang.str(this, R.string.shizuku_banner_ok)
                shizukuStatusTv.setTextColor(UiTheme.statusOk(this))
                shizukuActionBtn.visibility = View.GONE
            } else {
                shizukuStatusTv.text = Lang.str(this, R.string.shizuku_banner_off_title) + "\n" + Lang.str(this, R.string.shizuku_banner_off_desc)
                shizukuStatusTv.setTextColor(UiTheme.statusWarning(this))
                shizukuActionBtn.visibility = View.VISIBLE
            }
        }

        // RAM Usage
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val usedPct = (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem.toDouble()) * 100).toInt().coerceIn(1, 99)
            val usedGb = (mi.totalMem - mi.availMem) / (1024.0 * 1024.0 * 1024.0)
            val totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)

            if (::ramBarView.isInitialized) ramBarView.setPercent(usedPct)
            if (::ramUsageTv.isInitialized) {
                ramUsageTv.text = Lang.str(
                    this,
                    R.string.ram_usage_fmt,
                    usedPct,
                    String.format(Locale.US, "%.1f", usedGb),
                    String.format(Locale.US, "%.1f", totalGb)
                )
            }
        } catch (_: Exception) {}

        // Battery Temp, % and Power Draw
        try {
            val batIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (tempTenths > 0 && ::batTempTv.isInitialized) {
                batTempTv.text = String.format(Locale.US, "%.1f°C", tempTenths / 10f)
            }
            if (level >= 0 && scale > 0 && ::batPctTv.isInitialized) {
                batPctTv.text = " • ${(level * 100) / scale}%"
            }

            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null && ::batWattTv.isInitialized) {
                val microAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val status = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                if (microAmps != Int.MIN_VALUE && microAmps != 0) {
                    val watts = (Math.abs(microAmps) / 1_000_000.0) * 3.85
                    val prefix = if (isCharging) "⚡ +" else "🔋 "
                    batWattTv.text = String.format(Locale.US, "%s%.1fW", prefix, watts)
                } else {
                    batWattTv.text = if (isCharging) "⚡ Charging" else "🔋 Normal"
                }
            }
        } catch (_: Exception) {}

        // CPU / SoC Quick Stats
        try {
            if (::socTempTv.isInitialized) {
                // Try to read thermal zone 0 or battery temp as fallback
                val t = readQuickThermal()
                socTempTv.text = if (t > 0) String.format(Locale.US, "%.1f°C", t) else "Normal"
            }
            if (::cpuFreqTv.isInitialized) {
                val f = readMaxCpuFreq()
                cpuFreqTv.text = if (f > 0) String.format(Locale.US, "%.2f GHz Peak", f / 1_000_000.0) else "Multi-Cluster"
            }
        } catch (_: Exception) {}

        updateTestHudButtonText()
    }

    private fun readQuickThermal(): Float {
        return try {
            val f = File("/sys/class/thermal/thermal_zone0/temp")
            if (f.exists() && f.canRead()) {
                val raw = f.readText().trim().toFloatOrNull() ?: 0f
                if (raw > 1000) raw / 1000f else raw
            } else 0f
        } catch (_: Exception) { 0f }
    }

    private fun readMaxCpuFreq(): Long {
        return try {
            val f = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (f.exists() && f.canRead()) {
                f.readText().trim().toLongOrNull() ?: 0L
            } else 0L
        } catch (_: Exception) { 0L }
    }

    private fun updateResolutionSummary() {
        val newW = physW * selectedFactor / 100
        val newH = physH * selectedFactor / 100
        val newDen = physDen * selectedFactor / 100

        if (::resSummaryTv.isInitialized) {
            resSummaryTv.text = Lang.str(this, R.string.preview_display_fmt, physW, physH, physDen, newW, newH, newDen)
        }
        if (::ssaaPreviewNativeTv.isInitialized) {
            ssaaPreviewNativeTv.text = "${physW}x${physH} (${physDen} DPI)"
        }
        if (::ssaaPreviewTargetTv.isInitialized) {
            val scaleStr = "${selectedFactor / 100}.${selectedFactor % 100}x"
            ssaaPreviewTargetTv.text = "${newW}x${newH} ($scaleStr)"
        }
    }

    private fun updateLiveHudMockup() {
        val tv = liveHudMockTv ?: return
        val isCompact = (overlaySizeLevel == 0) || !overlayVertical
        val parts = mutableListOf<String>()

        if (prefsMgr.getMetric("m_fps", true)) parts.add(if (isCompact) "120.0F" else "⚡ FPS: 120.0")
        if (prefsMgr.getMetric("m_watt", true)) parts.add(if (isCompact) "5.8W" else "🔌 Watt: 5.8W")
        if (prefsMgr.getMetric("m_bat_temp", true)) parts.add(if (isCompact) "🔋36.5°" else "🔋 Pin: 36.5°C")
        if (prefsMgr.getMetric("m_soc_temp", true)) parts.add(if (isCompact) "🌡58.2°" else "🌡️ SoC: 58.2°C")
        if (prefsMgr.getMetric("m_cpu_temp", false)) parts.add(if (isCompact) "🔥58.2°" else "🔥 CPU: 58.2°C")
        if (prefsMgr.getMetric("m_gpu_temp", false)) parts.add(if (isCompact) "📟54.0°" else "📟 GPU: 54.0°C")
        if (prefsMgr.getMetric("m_gpu", true)) parts.add(if (isCompact) "V:640M" else "🎮 VRAM: 640MB")
        if (prefsMgr.getMetric("m_cpu_freq", false)) parts.add(if (isCompact) "3.53G" else "⚙️ CPU: 3.53GHz")
        if (prefsMgr.getMetric("m_ram", false)) parts.add(if (isCompact) "4.2G" else "💾 RAM: 4.2G")

        if (parts.isEmpty()) parts.add(Lang.str(this, R.string.hud_no_metrics))

        tv.text = if (overlayVertical) parts.joinToString("\n") else parts.joinToString(" · ")

        val idx = overlaySizeLevel.coerceIn(0, 3)
        val bIdx = overlayBgMode.coerceIn(0, 3)
        val sizes = floatArrayOf(9f, 10.5f, 12f, 13.5f)
        val padH = intArrayOf(10, 14, 18, 22)
        val padV = intArrayOf(5, 7, 10, 12)
        tv.textSize = sizes[idx]
        tv.setPadding(padH[idx], padV[idx], padH[idx], padV[idx])
        tv.setShadowLayer(2f, 1f, 1f, UiTheme.TEXT_SHADOW)

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

    private fun launchShizukuApp() {
        try {
            val pm = packageManager
            val launchIntent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app")))
            }
        } catch (_: Exception) {
            Toast.makeText(this, Lang.str(this, R.string.toast_start_shizuku), Toast.LENGTH_SHORT).show()
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

// =============================================================================
// MODERN CLEAN RAM PROGRESS BAR VIEW
// =============================================================================
class ModernRamBarView(context: Context) : View(context) {
    private var percent = 50
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    fun setPercent(pct: Int) {
        percent = pct.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        trackPaint.color = UiTheme.gaugeTrack(context)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, trackPaint)

        val fillW = w * (percent / 100f)
        if (fillW > 0) {
            fillPaint.color = when {
                percent >= 85 -> UiTheme.ACCENT_RED
                percent >= 70 -> UiTheme.ACCENT_AMBER
                else -> UiTheme.accent(context)
            }
            rect.set(0f, 0f, fillW, h)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
    }
}

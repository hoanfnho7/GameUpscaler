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
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
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

    // 3 Pages (Home, Anti-Aliasing, HUD Telemetry)
    private lateinit var homePage: ScrollView
    private lateinit var ssaaPage: ScrollView
    private lateinit var hudPage: ScrollView

    // Bottom Navigation Dock Buttons
    private lateinit var navHomeBtn: LinearLayout
    private lateinit var navSsaaBtn: LinearLayout
    private lateinit var navHudBtn: LinearLayout

    // Tab 1: Home Views
    private lateinit var heroCardView: LinearLayout
    private lateinit var heroTagTv: TextView
    private lateinit var heroTitleTv: TextView
    private lateinit var heroDescTv: TextView
    private lateinit var heroCtaBtn: TextView
    private lateinit var heroBatTempTv: TextView

    private lateinit var ramUsageTv: TextView
    private lateinit var ramBarView: ModernRamBarView
    private lateinit var storageUsageTv: TextView

    private lateinit var batTempTv: TextView
    private lateinit var batPctTv: TextView
    private lateinit var batModeTv: TextView
    private lateinit var cpuTempTv: TextView
    private lateinit var displaySubTv: TextView
    private lateinit var resSummaryTv: TextView

    private lateinit var step1Badge: TextView
    private lateinit var step2Badge: TextView
    private lateinit var step3Badge: TextView
    private lateinit var pinnedStripRow: LinearLayout

    // Tab 2: SSAA Resolution Views
    private lateinit var globalSsaaSwitch: ModernToggleSwitch
    private val ssaaCards = mutableListOf<LinearLayout>()
    private val ssaaRadios = mutableListOf<ModernRadioIndicator>()
    private val ssaaResolutions = mutableListOf<TextView>()

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(dp: Float): Float = dp * resources.displayMetrics.density

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
        }

        // =====================================================================
        // TOP APP BAR (Consistent across all tabs)
        // =====================================================================
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            setBackgroundColor(UiTheme.bgRoot(this@MainActivity))
        }

        // Brand + Model / Chipset Name
        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleLine.addView(TextView(this).apply {
            text = "SSAA TOOL"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        titleLine.addView(TextView(this).apply {
            text = "PRO"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            background = UiTheme.pillBox(UiTheme.accentSoft(this@MainActivity))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            }
        })
        brandCol.addView(titleLine)

        val socRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, 0)
        }
        socRow.addView(View(this).apply {
            background = UiTheme.ovalBox(UiTheme.TECH_EMERALD, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(6) }
        })
        socRow.addView(TextView(this).apply {
            text = getDeviceSocName()
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        })
        brandCol.addView(socRow)
        headerBar.addView(brandCol)

        // Theme Toggle (🌙 / ☀️ / 🌓)
        val themeBtn = TextView(this).apply {
            text = when (prefsMgr.themeMode) {
                1 -> Lang.str(this@MainActivity, R.string.theme_dark)
                2 -> Lang.str(this@MainActivity, R.string.theme_light)
                else -> Lang.str(this@MainActivity, R.string.theme_auto)
            }
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(12f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.themeMode = (prefsMgr.themeMode + 1) % 3
                recreate()
            }
        }
        headerBar.addView(themeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8)
        })

        // Language Toggle (Auto / VI / EN)
        val langBtn = TextView(this).apply {
            text = Lang.modeBadge(this@MainActivity)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(12f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.appLang = (prefsMgr.appLang + 1) % 3
                notifyOverlayStyleUpdated()
                recreate()
            }
        }
        headerBar.addView(langBtn)
        root.addView(headerBar)

        // =====================================================================
        // CONTENT CONTAINER (3 SCROLL VIEWS)
        // =====================================================================
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        homePage = buildHomePage()
        ssaaPage = buildSsaaPage()
        hudPage = buildHudPage()

        contentFrame.addView(homePage)
        contentFrame.addView(ssaaPage)
        contentFrame.addView(hudPage)
        root.addView(contentFrame)

        // =====================================================================
        // BOTTOM NAVIGATION DOCK (Inspired by GameCraft SSAA Stitch Design)
        // =====================================================================
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(UiTheme.bgNav(this@MainActivity))
            setPadding(dp(16), dp(8), dp(16), dp(12))
            elevation = dp(8f)
        }

        navHomeBtn = makeNavItem("⊞", Lang.str(this, R.string.tab_home)) { switchTab(0) }
        navSsaaBtn = makeNavItem("⚡", Lang.str(this, R.string.tab_ssaa)) { switchTab(1) }
        navHudBtn = makeNavItem("⏱", Lang.str(this, R.string.tab_hud)) { switchTab(2) }

        bottomNav.addView(navHomeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomNav.addView(navSsaaBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomNav.addView(navHudBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bottomNav)

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

    private fun makeNavItem(icon: String, title: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun switchTab(idx: Int) {
        activeTab = idx
        val accentCol = UiTheme.accent(this)
        val idleTxt = UiTheme.textSecondary(this)

        val navItems = listOf(navHomeBtn, navSsaaBtn, navHudBtn)
        navItems.forEachIndexed { i, nav ->
            val active = (i == idx)
            val iconTv = nav.getChildAt(0) as? TextView
            val textTv = nav.getChildAt(1) as? TextView

            iconTv?.setTextColor(if (active) accentCol else idleTxt)
            textTv?.setTextColor(if (active) accentCol else idleTxt)
            nav.background = if (active) UiTheme.roundBox(UiTheme.accentSoft(this), 999f) else null
        }

        homePage.visibility = if (idx == 0) View.VISIBLE else View.GONE
        ssaaPage.visibility = if (idx == 1) View.VISIBLE else View.GONE
        hudPage.visibility = if (idx == 2) View.VISIBLE else View.GONE

        if (idx == 0) refreshLiveDashboardStats()
        if (idx == 1) updateResolutionSummary()
        if (idx == 2) updateLiveHudMockup()
    }

    // =========================================================================
    // SCREEN 1: TRANG CHỦ (HOME & TELEMETRY MONITOR)
    // =========================================================================
    private fun buildHomePage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(28))
        }

        // Section Title
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }
        titleCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.home_header_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TECH_AMBER)
        })
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        titleRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.home_title)
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(this).apply {
            text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} • ${Build.MODEL}"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = UiTheme.pillBox(UiTheme.bgTile(this@MainActivity))
        })
        titleCol.addView(titleRow)
        container.addView(titleCol)

        // 1. Hero Status Card (GameCraft SSAA Engine Status)
        heroCardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(20f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }

        heroTagTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hero_status_ready_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TECH_AMBER)
        }
        heroCardView.addView(heroTagTv)

        heroTitleTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hero_status_ready_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(0, dp(6), 0, 0)
        }
        heroCardView.addView(heroTitleTv)

        heroDescTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hero_status_ready_desc, physW, physH)
            textSize = 13.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setLineSpacing(dp(3f), 1.2f)
            setPadding(0, dp(6), 0, dp(16))
        }
        heroCardView.addView(heroDescTv)

        // Big Primary CTA Pill Button
        heroCtaBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hero_btn_optimize)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textOnAccent(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = UiTheme.pillBox(UiTheme.accent(this@MainActivity))
            setOnClickListener {
                if (!ShizukuHelper.isBinderAlive()) {
                    launchShizukuApp()
                } else {
                    switchTab(1)
                }
            }
        }
        heroCardView.addView(heroCtaBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Triple Telemetry Grid in Hero Card Footer
        val heroStatsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
            background = UiTheme.roundBox(UiTheme.bgNav(this@MainActivity), dp(14f))
        }
        val col1 = makeHeroStatCol("6 lượt", Lang.str(this, R.string.hero_metric_daily), UiTheme.textPrimary(this))
        val col2 = makeHeroStatCol("100%", Lang.str(this, R.string.hero_metric_stability), UiTheme.TECH_EMERALD)
        heroBatTempTv = TextView(this).apply {
            text = "41.5°C"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TECH_AMBER)
            gravity = Gravity.CENTER
        }
        val col3 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(heroBatTempTv)
            addView(TextView(this@MainActivity).apply {
                text = Lang.str(this@MainActivity, R.string.hero_metric_battery)
                textSize = 11f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        }
        heroStatsRow.addView(col1)
        heroStatsRow.addView(col2)
        heroStatsRow.addView(col3)
        heroCardView.addView(heroStatsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        container.addView(heroCardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // 2. Realtime Hardware Telemetry Group Header
        val telemHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        telemHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_telemetry)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        telemHeader.addView(TextView(this).apply {
            text = "● LIVE POLLING"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TECH_EMERALD)
        })
        container.addView(telemHeader)

        // Card A: RAM & Storage
        val ramCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val ramTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ramTop.addView(TextView(this).apply {
            text = "💾"
            textSize = 20f
            setPadding(0, 0, dp(12), 0)
        })
        val ramTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        ramTextCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.card_ram_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        ramTextCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.card_ram_sub)
            textSize = 12.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(2), 0, 0)
        })
        ramTop.addView(ramTextCol)

        ramUsageTv = TextView(this).apply {
            text = "47% ĐÃ DÙNG"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = UiTheme.pillBox(UiTheme.bgTile(this@MainActivity))
        }
        ramTop.addView(ramUsageTv)
        ramCard.addView(ramTop)

        ramBarView = ModernRamBarView(this)
        ramCard.addView(ramBarView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply {
            topMargin = dp(14)
            bottomMargin = dp(10)
        })

        storageUsageTv = TextView(this).apply {
            text = "Bộ nhớ trong: Còn trống ... GB"
            textSize = 12.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
        }
        ramCard.addView(storageUsageTv)
        container.addView(ramCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        // Card B: Nhiệt độ & Nguồn pin
        val tempCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val tempTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tempTop.addView(TextView(this).apply {
            text = "🌡️"
            textSize = 20f
            setPadding(0, 0, dp(12), 0)
        })
        val tempTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tempTextCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.card_temp_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        tempTextCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.card_temp_sub)
            textSize = 12.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(2), 0, 0)
        })
        tempTop.addView(tempTextCol)

        val tempBadge = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.temp_status_normal)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TECH_EMERALD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = UiTheme.pillBox(UiTheme.bgTile(this@MainActivity))
        }
        tempTop.addView(tempBadge)
        tempCard.addView(tempTop)

        // 3-Cell Grid
        val threeGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val cell1 = makeTelemetryCell("Pin", "--%", "--°C")
        batPctTv = cell1.getChildAt(1) as TextView
        batTempTv = cell1.getChildAt(2) as TextView

        val cell2 = makeTelemetryCell("CPU Core", "--°C", "Tối ưu")
        cpuTempTv = cell2.getChildAt(1) as TextView

        val cell3 = makeTelemetryCell("Chế độ", "Pin xả", "0.0W")
        batModeTv = cell3.getChildAt(2) as TextView

        threeGrid.addView(cell1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        threeGrid.addView(cell2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        threeGrid.addView(cell3, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tempCard.addView(threeGrid)
        container.addView(tempCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        // Card C: Màn hình & GPU
        val dispCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        dispCard.addView(TextView(this).apply {
            text = "🖥️"
            textSize = 20f
            setPadding(0, 0, dp(12), 0)
        })
        val dispCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        dispCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.card_display_gpu_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        displaySubTv = TextView(this).apply {
            text = "1080 x 2400 (420 DPI)"
            textSize = 13f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(2), 0, 0)
        }
        dispCol.addView(displaySubTv)
        dispCard.addView(dispCol)

        val gpuCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        gpuCol.addView(TextView(this).apply {
            text = "Adreno / Mali"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        })
        gpuCol.addView(TextView(this).apply {
            text = "Vulkan 1.3"
            textSize = 11.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(2), 0, 0)
        })
        dispCard.addView(gpuCol)
        container.addView(dispCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // 3. System Setup Stepper (3 Steps List)
        val stepHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        stepHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_setup_steps)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        stepHeader.addView(TextView(this).apply {
            text = "3/3 Sẵn sàng"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        })
        container.addView(stepHeader)

        // Step 1: Shizuku
        val (s1Row, b1) = makeStepRow(
            icon = "✓",
            title = Lang.str(this, R.string.step_1_title),
            desc = Lang.str(this, R.string.step_1_desc),
            status = Lang.str(this, R.string.step_status_active),
            isOk = ShizukuHelper.isBinderAlive()
        ) { launchShizukuApp() }
        step1Badge = b1
        container.addView(s1Row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        // Step 2: Overlay
        val (s2Row, b2) = makeStepRow(
            icon = "✓",
            title = Lang.str(this, R.string.step_2_title),
            desc = Lang.str(this, R.string.step_2_desc),
            status = Lang.str(this, R.string.step_status_granted),
            isOk = Settings.canDrawOverlays(this)
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        step2Badge = b2
        container.addView(s2Row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        // Step 3: Battery Optimization
        val (s3Row, b3) = makeStepRow(
            icon = "✓",
            title = Lang.str(this, R.string.step_3_title),
            desc = Lang.str(this, R.string.step_3_desc),
            status = Lang.str(this, R.string.step_status_done),
            isOk = true
        ) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {}
        }
        step3Badge = b3
        container.addView(s3Row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) })

        // 4. Quick Action Tools Row (Reset, Insights, Sensors, Quick Tile)
        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolRow.addView(makeSquareAction("↺", Lang.str(this, R.string.dash_reset)) {
            if (ShizukuHelper.ensureReady(this)) {
                ShizukuHelper.resetDisplay()
                cancelActiveNotification()
                stopService(Intent(this, FpsOverlayService::class.java))
                Toast.makeText(this, Lang.str(this, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        toolRow.addView(makeSquareAction("📈", Lang.str(this, R.string.dash_insights)) {
            startActivity(Intent(this, PerfChartActivity::class.java))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        toolRow.addView(makeSquareAction("🌡️", Lang.str(this, R.string.btn_sensors)) {
            showThermalInspectorDialog()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        toolRow.addView(makeSquareAction("🎛️", Lang.str(this, R.string.btn_quick_tile)) {
            requestAddQuickTile()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(toolRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) })

        // 5. Pinned Games Strip
        val gamesHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        gamesHeader.addView(TextView(this).apply {
            text = "⭐ " + Lang.str(this@MainActivity, R.string.dash_my_games)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        gamesHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_manage) + " ➔"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            setOnClickListener { switchTab(1) }
        })
        container.addView(gamesHeader)

        val stripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, dp(8))
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

    private fun makeHeroStatCol(value: String, label: String, valColor: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(valColor)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun makeTelemetryCell(title: String, val1: String, val2: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(12f))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 11f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
            })
            addView(TextView(this@MainActivity).apply {
                text = val1
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
                setPadding(0, dp(2), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = val2
                textSize = 11.5f
                setTextColor(UiTheme.TECH_EMERALD)
                setPadding(0, dp(1), 0, 0)
            })
        }
    }

    private fun makeStepRow(icon: String, title: String, desc: String, status: String, isOk: Boolean, onClick: () -> Unit): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(16f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener { onClick() }
        }
        val iconBox = TextView(this).apply {
            text = icon
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isOk) UiTheme.accent(this@MainActivity) else UiTheme.TECH_AMBER)
            gravity = Gravity.CENTER
            background = UiTheme.ovalBox(if (isOk) UiTheme.accentSoft(this@MainActivity) else UiTheme.bgTile(this@MainActivity), 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) }
        }
        row.addView(iconBox)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) }
        }
        textCol.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        textCol.addView(TextView(this).apply {
            text = desc
            textSize = 12.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(textCol)

        val badge = TextView(this).apply {
            text = status
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isOk) UiTheme.accent(this@MainActivity) else UiTheme.TECH_AMBER)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = UiTheme.pillBox(if (isOk) UiTheme.accentSoft(this@MainActivity) else UiTheme.bgTile(this@MainActivity))
        }
        row.addView(badge)
        return row to badge
    }

    private fun makeSquareAction(icon: String, title: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(14f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
                gravity = Gravity.CENTER
                setSingleLine(true)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    // =========================================================================
    // SCREEN 2: KHỬ RĂNG CƯA (SSAA RESOLUTION & GAME LIBRARY)
    // =========================================================================
    private fun buildSsaaPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(28))
        }

        // Section Title
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }
        titleCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_header_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        })
        titleCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_title)
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        titleCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_subtitle)
            textSize = 13.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setLineSpacing(dp(3f), 1.2f)
            setPadding(0, dp(4), 0, 0)
        })
        container.addView(titleCol)

        // Global Master Toggle Card
        val globalCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val gTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gTop.addView(TextView(this).apply {
            text = "⚡"
            textSize = 22f
            setPadding(0, 0, dp(12), 0)
        })
        val gTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) }
        }
        val gTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gTitleRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_global_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
        })
        gTitleRow.addView(TextView(this).apply {
            text = "DAEMON"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            background = UiTheme.pillBox(UiTheme.accentSoft(this@MainActivity))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) }
        })
        gTextCol.addView(gTitleRow)
        gTextCol.addView(TextView(this).apply {
            text = "● " + Lang.str(this@MainActivity, R.string.ssaa_global_desc)
            textSize = 12.5f
            setTextColor(UiTheme.TECH_EMERALD)
            setPadding(0, dp(3), 0, 0)
        })
        gTop.addView(gTextCol)

        globalSsaaSwitch = ModernToggleSwitch(this).apply {
            setChecked(enableFpsOverlay, animate = false, notify = false)
            onCheckedChangeListener = { isChecked ->
                enableFpsOverlay = isChecked
                prefsMgr.enableFpsOverlay = isChecked
            }
        }
        gTop.addView(globalSsaaSwitch)
        globalCard.addView(gTop)

        globalCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_global_hint)
            textSize = 12.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(12), 0, 0)
        })
        container.addView(globalCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // SSAA Resolution Scale Selection Group
        val scaleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val scaleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        scaleHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.ssaa_scale_group_title)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        resSummaryTv = TextView(this).apply {
            text = "1080x2400"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        }
        scaleHeader.addView(resSummaryTv)
        scaleGroup.addView(scaleHeader)

        ssaaCards.clear()
        ssaaRadios.clear()
        ssaaResolutions.clear()

        val ssaaPresets = listOf(
            Triple(115, R.string.ssaa_preset_115_tag, R.string.ssaa_preset_115_desc),
            Triple(125, R.string.ssaa_preset_125_tag, R.string.ssaa_preset_125_desc),
            Triple(150, R.string.ssaa_preset_150_tag, R.string.ssaa_preset_150_desc),
            Triple(200, R.string.ssaa_preset_200_tag, R.string.ssaa_preset_200_desc)
        )

        ssaaPresets.forEach { (factor, tagRes, descRes) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener { selectGlobalFactor(factor) }
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) }
            }
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val scaleTitle = "${factor / 100}.${factor % 100}x"
            row1.addView(TextView(this).apply {
                text = scaleTitle
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
            })
            val resTv = TextView(this).apply {
                text = ""
                textSize = 13f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                setPadding(dp(8), 0, dp(8), 0)
            }
            ssaaResolutions.add(resTv)
            row1.addView(resTv)

            row1.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, tagRes)
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.accent(this@MainActivity))
                background = UiTheme.pillBox(UiTheme.accentSoft(this@MainActivity))
                setPadding(dp(8), dp(2), dp(8), dp(2))
            })
            textCol.addView(row1)

            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, descRes)
                textSize = 12.5f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                setPadding(0, dp(4), 0, 0)
            })
            card.addView(textCol)

            val radioIndicator = ModernRadioIndicator(this).apply {
                isRadioSelected = (factor == selectedFactor)
            }
            card.addView(radioIndicator)

            ssaaCards.add(card)
            ssaaRadios.add(radioIndicator)
            scaleGroup.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
        refreshSsaaCardStyles()
        container.addView(scaleGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // Quick Pair Buttons: [ Reset Display ] & [ Render Test ]
        val pairRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pairRow.addView(TextView(this).apply {
            text = "↺  " + Lang.str(this@MainActivity, R.string.btn_reset_display)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(14f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                if (ShizukuHelper.ensureReady(this@MainActivity)) {
                    ShizukuHelper.resetDisplay()
                    cancelActiveNotification()
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                    Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        pairRow.addView(TextView(this).apply {
            text = "👁️  " + Lang.str(this@MainActivity, R.string.btn_render_test)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(14f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener { toggleHudPreviewService() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(pairRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // Supported Game Library
        val gameLibCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val libTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        libTop.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_game_list_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        libTop.addView(TextView(this).apply {
            text = "${filteredApps.size} Sẵn sàng"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        })
        gameLibCard.addView(libTop)

        // Filter Bar (Search + Star Filter)
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchBox = EditText(this).apply {
            hint = Lang.str(this@MainActivity, R.string.hint_search)
            textSize = 13.5f
            setSingleLine(true)
            setHintTextColor(UiTheme.textSecondary(this@MainActivity))
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(12f), 1, UiTheme.strokeSubtle(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) }
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
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            fun updateStyle() {
                setTextColor(if (showPinnedOnly) UiTheme.accent(this@MainActivity) else UiTheme.textSecondary(this@MainActivity))
                background = UiTheme.roundBox(
                    if (showPinnedOnly) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgTile(this@MainActivity),
                    dp(12f),
                    1,
                    if (showPinnedOnly) UiTheme.accent(this@MainActivity) else UiTheme.strokeSubtle(this@MainActivity)
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
            textSize = 12f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(dp(4), dp(8), dp(4), dp(10))
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
        val factors = listOf(115, 125, 150, 200)
        factors.forEachIndexed { idx, factor ->
            val isSelected = (factor == selectedFactor)
            if (idx < ssaaCards.size && idx < ssaaRadios.size) {
                ssaaCards[idx].background = UiTheme.roundBox(
                    if (isSelected) UiTheme.bgTileActive(this) else UiTheme.bgTile(this),
                    dp(14f),
                    1,
                    if (isSelected) accentCol else UiTheme.strokeSubtle(this)
                )
                ssaaRadios[idx].isRadioSelected = isSelected
            }
            if (idx < ssaaResolutions.size) {
                val nw = physW * factor / 100
                val nh = physH * factor / 100
                ssaaResolutions[idx].text = "${nw} × ${nh}"
            }
        }
    }

    private fun updateGlobalSsaaToggleStyle() {
        globalSsaaSwitch.setChecked(enableFpsOverlay, animate = true, notify = false)
    }

    // =========================================================================
    // SCREEN 3: THANH ĐO (HUD OVERLAY & HARDWARE TELEMETRY)
    // =========================================================================
    private fun buildHudPage(): ScrollView {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(28))
        }

        // Section Title
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }
        titleCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hud_header_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
        })
        val hRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        hRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hud_title)
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        hRow.addView(TextView(this).apply {
            text = "PRO"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            background = UiTheme.pillBox(UiTheme.accentSoft(this@MainActivity))
            setPadding(dp(10), dp(2), dp(10), dp(2))
        })
        titleCol.addView(hRow)

        titleCol.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hud_subtitle)
            textSize = 13.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setLineSpacing(dp(3f), 1.2f)
            setPadding(0, dp(4), 0, 0)
        })
        container.addView(titleCol)

        // 1. Live Preview Canvas
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val prevHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        prevHeader.addView(TextView(this).apply {
            text = "👁️  " + Lang.str(this@MainActivity, R.string.hud_preview_title)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        prevHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hud_preview_ratio)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@MainActivity))
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = UiTheme.pillBox(UiTheme.bgTile(this@MainActivity))
        })
        previewCard.addView(prevHeader)

        // Simulated Game Screen Frame
        val stageFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(26), dp(16), dp(26))
            background = UiTheme.roundBox(Color.parseColor("#0C0E11"), dp(16f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val mockTv = TextView(this).apply {
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.VOLT_LIME)
        }
        liveHudMockTv = mockTv
        stageFrame.addView(mockTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewCard.addView(stageFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Actions Bar below stage: [ Test on Screen ] & [ Reset Position ]
        val stageActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        testHudToggleBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, if (FpsOverlayService.isServiceRunning) R.string.btn_test_hud_off else R.string.btn_test_hud_on)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (FpsOverlayService.isServiceRunning) UiTheme.TECH_RED else UiTheme.accent(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(12f), 1, if (FpsOverlayService.isServiceRunning) UiTheme.TECH_RED else UiTheme.accent(this@MainActivity))
            setOnClickListener {
                toggleHudPreviewService()
                postDelayed({ updateTestHudButtonText() }, 250)
            }
        }
        stageActions.addView(testHudToggleBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        stageActions.addView(TextView(this).apply {
            text = "↺ " + Lang.str(this@MainActivity, R.string.btn_reset_pos)
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(12f), 1, UiTheme.strokeSubtle(this@MainActivity))
            setOnClickListener {
                prefsMgr.resetHudPosition()
                if (FpsOverlayService.isServiceRunning) {
                    startService(Intent(this@MainActivity, FpsOverlayService::class.java).apply { action = "RESET_POS" })
                }
                Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_pos_reset), Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        previewCard.addView(stageActions)
        container.addView(previewCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // 2. Telemetry Parameters Selector (8 Core Metrics)
        val metricsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        val metHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        metHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_metrics_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        metricsCard.addView(metHeader)

        data class MetricSpec(val key: String, val icon: String, val titleId: Int, val descId: Int, val defVal: Boolean)
        val specs = listOf(
            MetricSpec("m_fps", "⚡", R.string.metric_fps_title, R.string.metric_fps_desc, true),
            MetricSpec("m_bat_temp", "🌡️", R.string.metric_temp_title, R.string.metric_temp_desc, true),
            MetricSpec("m_watt", "🔋", R.string.metric_battery_title, R.string.metric_battery_desc, true),
            MetricSpec("m_gpu", "🎮", R.string.metric_gpu_title, R.string.metric_gpu_desc, true),
            MetricSpec("m_ram", "💾", R.string.metric_ram_title, R.string.metric_ram_desc, false),
            MetricSpec("m_skin_temp", "📱", R.string.metric_skin_title, R.string.metric_skin_desc, false),
            MetricSpec("m_vram", "🕹️", R.string.metric_vram_title, R.string.metric_vram_desc, false),
            MetricSpec("m_cam_temp", "📷", R.string.metric_cam_title, R.string.metric_cam_desc, false)
        )

        specs.forEachIndexed { idx, spec ->
            var enabled = prefsMgr.getMetric(spec.key, spec.defVal)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            row.addView(TextView(this).apply {
                text = spec.icon
                textSize = 18f
                setPadding(0, 0, dp(12), 0)
            })
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) }
            }
            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, spec.titleId)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textPrimary(this@MainActivity))
            })
            textCol.addView(TextView(this).apply {
                text = Lang.str(this@MainActivity, spec.descId)
                textSize = 12.5f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(textCol)

            val toggleSwitch = ModernToggleSwitch(this).apply {
                setChecked(enabled, animate = false, notify = false)
                onCheckedChangeListener = { isChecked ->
                    enabled = isChecked
                    prefsMgr.setMetric(spec.key, isChecked)
                    updateLiveHudMockup()
                    notifyOverlayStyleUpdated()
                }
            }
            row.setOnClickListener {
                toggleSwitch.setChecked(!toggleSwitch.isChecked, animate = true, notify = true)
            }
            row.addView(toggleSwitch)
            metricsCard.addView(row)

            if (idx < specs.size - 1) {
                metricsCard.addView(View(this).apply {
                    setBackgroundColor(UiTheme.divider(this@MainActivity))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(2)
                    }
                })
            }
        }
        container.addView(metricsCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // 3. HUD Styling & Dimensions Group
        val styleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(18f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_styling_tag)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, 0, 0, dp(12))
        })

        // Layout Mode: Horizontal / Vertical
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.lbl_layout_style)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(4), 0, dp(8))
        })
        styleCard.addView(buildSegmentedRow(
            items = listOf(
                0 to Lang.str(this, R.string.layout_horizontal),
                1 to Lang.str(this, R.string.layout_vertical)
            ),
            selectedIndex = if (overlayVertical) 1 else 0,
            padTop = 0,
            padBottom = dp(14)
        ) { idx ->
            overlayVertical = (idx == 1)
            prefsMgr.overlayVertical = overlayVertical
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        // Scale Size: 70%, 85%, 100%, 125%
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.lbl_scale)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(4), 0, dp(8))
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
            padBottom = dp(14)
        ) { lvl ->
            overlaySizeLevel = lvl
            prefsMgr.overlaySizeLevel = lvl
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        // Opacity: Solid, Glass, Sheer, Text Only
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.lbl_opacity)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(4), 0, dp(8))
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
            padBottom = dp(14)
        ) { mode ->
            overlayBgMode = mode
            prefsMgr.overlayBgMode = mode
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        // Polling Rate
        styleCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_hud_poll)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setPadding(0, dp(4), 0, dp(8))
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
            padBottom = dp(4)
        ) { mode ->
            hudPollMode = mode
            prefsMgr.hudPollMode = mode
            notifyOverlayStyleUpdated()
        })
        container.addView(styleCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // 4. Tips Card
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = UiTheme.roundBox(UiTheme.bgCard(this@MainActivity), dp(16f), 1, UiTheme.strokeSubtle(this@MainActivity))
        }
        tipCard.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.hud_guide_tips)
            textSize = 13f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setLineSpacing(dp(3f), 1.25f)
        })
        container.addView(tipCard)

        scroll.addView(container)
        return scroll
    }

    private fun updateTestHudButtonText() {
        val btn = testHudToggleBtn ?: return
        val isRunning = FpsOverlayService.isServiceRunning
        btn.text = Lang.str(this, if (isRunning) R.string.btn_test_hud_off else R.string.btn_test_hud_on)
        btn.setTextColor(if (isRunning) UiTheme.TECH_RED else UiTheme.accent(this))
        btn.background = UiTheme.roundBox(
            UiTheme.bgTile(this),
            dp(12f),
            1,
            if (isRunning) UiTheme.TECH_RED else UiTheme.accent(this)
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
                    dp(10f),
                    if (active) 0 else 1,
                    UiTheme.strokeSubtle(this)
                )
            }
        }

        items.forEach { (id, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(9), dp(8), dp(9))
                setOnClickListener {
                    current = id
                    refresh()
                    onSelect(id)
                }
            }
            chips.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (id < items.size - 1) rightMargin = dp(8)
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
        if (filteredApps.isEmpty()) {
            parentLayout.addView(TextView(this).apply {
                text = "Không tìm thấy game nào"
                textSize = 13.5f
                setTextColor(UiTheme.textSecondary(this@MainActivity))
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }

        val limit = minOf(filteredApps.size, 50)
        for (i in 0 until limit) {
            val item = filteredApps[i]
            val row = buildAppRow(item)
            parentLayout.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
    }

    private fun buildAppRow(item: AppItem): View {
        val accentCol = UiTheme.accent(this)
        val effectiveScale = if (item.customScale > 0) item.customScale else selectedFactor

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = UiTheme.roundBox(
                if (item.isPinned) UiTheme.bgTileActive(this@MainActivity) else UiTheme.bgTile(this@MainActivity),
                dp(14f),
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
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) }
        }
        row.addView(img)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) }
        }
        textCol.addView(TextView(this).apply {
            text = item.name
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@MainActivity))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(this).apply {
            val scaleStr = "${effectiveScale / 100}.${effectiveScale % 100}x SSAA"
            text = "$scaleStr • ${item.pkg}"
            textSize = 12f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(textCol)

        // "Đổi" button to cycle scale
        val changeBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_change_scale)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = UiTheme.pillBox(UiTheme.bgCard(this@MainActivity))
            setOnClickListener { cyclePerGameScale(item) }
        }
        row.addView(changeBtn)

        // Pin button (★)
        val pinBtn = TextView(this).apply {
            text = if (item.isPinned) "★" else "☆"
            textSize = 20f
            setTextColor(if (item.isPinned) accentCol else UiTheme.textSecondary(this@MainActivity))
            setPadding(dp(10), dp(6), dp(4), dp(6))
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
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener { launchGameByPkg(item.pkg, item.name, effectiveScale) }
                setOnLongClickListener {
                    createPinnedShortcut(item, effectiveScale)
                    true
                }
            }
            val img = ImageView(this).apply {
                setImageDrawable(item.icon)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            val lbl = TextView(this).apply {
                text = item.name
                textSize = 11.5f
                setTextColor(UiTheme.textPrimary(this@MainActivity))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
            }
            col.addView(img)
            col.addView(lbl)
            pinnedStripRow.addView(col, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        }

        // "+ Thêm" Slot
        val addCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener { switchTab(1) }
        }
        val plusBox = TextView(this).apply {
            text = "+"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            gravity = Gravity.CENTER
            background = UiTheme.roundBox(UiTheme.bgTile(this@MainActivity), dp(16f), 1, UiTheme.strokeSubtle(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val addLbl = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.dash_add_game)
            textSize = 11.5f
            setTextColor(UiTheme.textSecondary(this@MainActivity))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
        }
        addCol.addView(plusBox)
        addCol.addView(addLbl)
        pinnedStripRow.addView(addCol)
    }

    private fun refreshLiveDashboardStats() {
        val shizukuOk = ShizukuHelper.isBinderAlive()

        // Update Hero Status Card
        if (::heroTitleTv.isInitialized && ::heroTagTv.isInitialized && ::heroDescTv.isInitialized && ::heroCtaBtn.isInitialized) {
            if (shizukuOk) {
                heroTagTv.text = Lang.str(this, R.string.hero_status_ready_tag)
                heroTagTv.setTextColor(UiTheme.TECH_AMBER)
                heroTitleTv.text = Lang.str(this, R.string.hero_status_ready_title)
                heroDescTv.text = Lang.str(this, R.string.hero_status_ready_desc, physW, physH)
                heroCtaBtn.text = Lang.str(this, R.string.hero_btn_optimize)
                heroCtaBtn.background = UiTheme.pillBox(UiTheme.accent(this))
                heroCtaBtn.setTextColor(UiTheme.textOnAccent(this))
            } else {
                heroTagTv.text = Lang.str(this, R.string.hero_status_off_tag)
                heroTagTv.setTextColor(UiTheme.TECH_AMBER)
                heroTitleTv.text = Lang.str(this, R.string.hero_status_off_title)
                heroDescTv.text = Lang.str(this, R.string.hero_status_off_desc)
                heroCtaBtn.text = Lang.str(this, R.string.hero_btn_launch_shizuku)
                heroCtaBtn.background = UiTheme.pillBox(UiTheme.TECH_AMBER)
                heroCtaBtn.setTextColor(Color.parseColor("#171E00"))
            }
        }

        // Stepper Badges
        if (::step1Badge.isInitialized) {
            step1Badge.text = Lang.str(this, if (shizukuOk) R.string.step_status_active else R.string.step_status_pending)
            step1Badge.setTextColor(if (shizukuOk) UiTheme.accent(this) else UiTheme.TECH_AMBER)
        }
        if (::step2Badge.isInitialized) {
            val ov = Settings.canDrawOverlays(this)
            step2Badge.text = Lang.str(this, if (ov) R.string.step_status_granted else R.string.step_status_pending)
            step2Badge.setTextColor(if (ov) UiTheme.accent(this) else UiTheme.TECH_AMBER)
        }

        // RAM Usage
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val usedPct = (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem.toDouble()) * 100).toInt().coerceIn(1, 99)
            val usedGb = (mi.totalMem - mi.availMem) / (1024.0 * 1024.0 * 1024.0)
            val totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
            val freeGb = mi.availMem / (1024.0 * 1024.0 * 1024.0)

            if (::ramBarView.isInitialized) ramBarView.setPercent(usedPct)
            if (::ramUsageTv.isInitialized) {
                ramUsageTv.text = "$usedPct% ĐÃ DÙNG"
            }
            if (::storageUsageTv.isInitialized) {
                val (freeStorage, totalStorage) = getStorageInfo()
                storageUsageTv.text = Lang.str(this, R.string.storage_fmt, String.format(Locale.US, "%.0f", freeStorage), String.format(Locale.US, "%.0f", totalStorage))
            }
        } catch (_: Exception) {}

        // Battery Temp, % and Wattage
        try {
            val batIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val batTempStr = if (tempTenths > 0) String.format(Locale.US, "%.1f°C", tempTenths / 10f) else "41.5°C"
            val batPctVal = if (level >= 0 && scale > 0) (level * 100) / scale else 88

            if (::heroBatTempTv.isInitialized) heroBatTempTv.text = batTempStr
            if (::batTempTv.isInitialized) batTempTv.text = batTempStr
            if (::batPctTv.isInitialized) batPctTv.text = "$batPctVal%"

            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null && ::batModeTv.isInitialized) {
                val microAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val status = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                if (microAmps != Int.MIN_VALUE && microAmps != 0) {
                    val watts = (Math.abs(microAmps) / 1_000_000.0) * 3.85
                    batModeTv.text = String.format(Locale.US, "%s%.1fW", if (isCharging) "+" else "-", watts)
                } else {
                    batModeTv.text = if (isCharging) "Sạc pin" else "Pin xả"
                }
            }
        } catch (_: Exception) {}

        // CPU / SoC Quick Stats
        try {
            if (::cpuTempTv.isInitialized) {
                val t = readQuickThermal()
                cpuTempTv.text = if (t > 0) String.format(Locale.US, "%.0f°C", t) else "44°C"
            }
        } catch (_: Exception) {}

        updateTestHudButtonText()
    }

    private fun getStorageInfo(): Pair<Float, Float> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeGb = freeBytes / (1024f * 1024f * 1024f)
            val totalGb = totalBytes / (1024f * 1024f * 1024f)
            freeGb to totalGb
        } catch (_: Exception) { 128f to 256f }
    }

    private fun getDeviceSocName(): String {
        return try {
            if (Build.VERSION.SDK_INT >= 31 && !Build.SOC_MODEL.isNullOrEmpty() && Build.SOC_MODEL != "unknown") {
                Build.SOC_MODEL
            } else if (!Build.HARDWARE.isNullOrEmpty() && Build.HARDWARE != "unknown") {
                Build.HARDWARE.uppercase()
            } else {
                "Snapdragon 8 Gen 3"
            }
        } catch (_: Exception) { "Snapdragon 8 Gen 3" }
    }

    private fun readQuickThermal(): Float {
        return try {
            val f = File("/sys/class/thermal/thermal_zone0/temp")
            if (f.exists() && f.canRead()) {
                val raw = f.readText().trim().toFloatOrNull() ?: 0f
                if (raw > 1000) raw / 1000f else raw
            } else 44f
        } catch (_: Exception) { 44f }
    }

    private fun updateResolutionSummary() {
        val newW = physW * selectedFactor / 100
        val newH = physH * selectedFactor / 100
        val newDen = physDen * selectedFactor / 100

        if (::displaySubTv.isInitialized) {
            displaySubTv.text = "${physW} × ${physH} (${physDen} DPI)"
        }
        if (::resSummaryTv.isInitialized) {
            resSummaryTv.text = "${physW}x${physH} (${physDen} DPI)"
        }
        refreshSsaaCardStyles()
    }

    private fun updateLiveHudMockup() {
        val tv = liveHudMockTv ?: return
        val isCompact = (overlaySizeLevel == 0) || !overlayVertical
        val parts = mutableListOf<String>()

        if (prefsMgr.getMetric("m_fps", true)) parts.add(if (isCompact) "59.8 FPS" else "⚡ FPS: 59.8")
        if (prefsMgr.getMetric("m_bat_temp", true)) parts.add(if (isCompact) "41.2°C" else "🌡️ Nhiệt: 41.2°C")
        if (prefsMgr.getMetric("m_watt", true)) parts.add(if (isCompact) "88% • 4.2W" else "🔋 Pin: 88% (4.2W)")
        if (prefsMgr.getMetric("m_gpu", true)) parts.add(if (isCompact) "GPU 68%" else "🎮 GPU: 68%")
        if (prefsMgr.getMetric("m_ram", false)) parts.add(if (isCompact) "RAM 52%" else "💾 RAM: 52%")
        if (prefsMgr.getMetric("m_skin_temp", false)) parts.add(if (isCompact) "Vỏ 38°" else "📱 Vỏ: 38°C")
        if (prefsMgr.getMetric("m_vram", false)) parts.add(if (isCompact) "V:640M" else "🕹️ VRAM: 640MB")
        if (prefsMgr.getMetric("m_cam_temp", false)) parts.add(if (isCompact) "CAM 39°" else "📷 CAM: 39°C")

        if (parts.isEmpty()) parts.add(Lang.str(this, R.string.hud_no_metrics))

        tv.text = if (overlayVertical) parts.joinToString("\n") else parts.joinToString("  •  ")

        val idx = overlaySizeLevel.coerceIn(0, 3)
        val bIdx = overlayBgMode.coerceIn(0, 3)
        val sizes = floatArrayOf(8.5f, 10f, 11.5f, 13f)
        val padH = intArrayOf(12, 16, 20, 24)
        val padV = intArrayOf(6, 8, 10, 12)
        tv.textSize = sizes[idx]
        tv.setPadding(padH[idx], padV[idx], padH[idx], padV[idx])
        tv.setShadowLayer(2f, 1f, 1f, UiTheme.TEXT_SHADOW)

        if (bIdx == 3) {
            tv.background = null
        } else {
            tv.background = UiTheme.roundBox(UiTheme.HUD_BG_OPACITY[bIdx], 999f, 1, UiTheme.HUD_STROKE_OPACITY[bIdx])
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
        val cycle = listOf(0, 115, 125, 150, 200)
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
// MODERN CLEAN RAM PROGRESS BAR VIEW (WITH VOLT LIME GRADIENT)
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
                percent >= 85 -> UiTheme.TECH_RED
                percent >= 70 -> UiTheme.TECH_AMBER
                else -> UiTheme.accent(context)
            }
            rect.set(0f, 0f, fillW, h)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
    }
}

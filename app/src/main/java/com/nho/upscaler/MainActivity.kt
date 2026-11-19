package com.nho.upscaler

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
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
    private lateinit var previewTv: TextView
    private lateinit var statusPillTv: TextView
    private var liveHudMockTv: TextView? = null
    private var perfLogBtn: TextView? = null
    private val pinnedPkgs = mutableSetOf<String>()

    private lateinit var tabArmoryBtn: TextView
    private lateinit var tabHudBtn: TextView
    private lateinit var armoryPage: LinearLayout
    private lateinit var hudStudioPage: ScrollView

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
            setBackgroundColor(UiTheme.BG_ROOT)
            setPadding(28, 34, 28, 12)
        }

        // 1. Top Gaming Command Header
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "⚡ GAMEUPSCALER // PRO"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_GREEN)
        })
        statusPillTv = TextView(this).apply {
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 2, 0, 0)
        }
        titleCol.addView(statusPillTv)
        headerBar.addView(titleCol)

        headerBar.addView(TextView(this).apply {
            text = Lang.modeBadge(this@MainActivity)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_CYAN)
            setPadding(16, 9, 16, 9)
            background = UiTheme.roundBox(UiTheme.BTN_LANG_BG, 12f, 1, UiTheme.BTN_CYAN_STROKE)
            setOnClickListener {
                prefsMgr.appLang = (prefsMgr.appLang + 1) % 3
                notifyOverlayStyleUpdated()
                recreate()
            }
        })
        root.addView(headerBar)

        // 2. Segmented Gaming Workspace Switcher (Kho Game vs Tùy chỉnh HUD)
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(6, 6, 6, 6)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_SUBTLE)
        }
        tabArmoryBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.tab_armory)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 12)
            setOnClickListener { switchTab(0) }
        }
        tabHudBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.tab_hud_studio)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 12)
            setOnClickListener { switchTab(1) }
        }
        tabBar.addView(tabArmoryBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 6 })
        tabBar.addView(tabHudBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12
            bottomMargin = 12
        })

        // 3. Page 1: Game Armory (Clean, spacious, fast game launching)
        armoryPage = buildArmoryPage()
        root.addView(armoryPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // 4. Page 2: HUD Studio (Spacious, categorized, live HUD preview)
        hudStudioPage = buildHudStudioPage()
        root.addView(hudStudioPage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        switchTab(0)
        updateShizukuBadge()
        updatePreviewText()
        updateLiveHudMockup()
        refreshPerfLogBadge()

        if (ShizukuHelper.ensureReady(this)) {
            ShizukuHelper.fetchPhysicalDisplay { w, h, den ->
                physW = w
                physH = h
                physDen = den
                runOnUiThread { updatePreviewText() }
            }
        }
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        updateShizukuBadge()
        refreshPerfLogBadge()
    }

    private fun updateShizukuBadge() {
        if (!::statusPillTv.isInitialized) return
        val ok = ShizukuHelper.isBinderAlive()
        statusPillTv.text = Lang.str(this, if (ok) R.string.status_shizuku_ok else R.string.status_shizuku_off)
        statusPillTv.setTextColor(if (ok) UiTheme.ACCENT_GREEN else UiTheme.ACCENT_RED_BRIGHT)
    }

    private fun switchTab(index: Int) {
        activeTab = index
        val isArmory = (index == 0)

        tabArmoryBtn.setTextColor(if (isArmory) UiTheme.TEXT_DARK else UiTheme.TEXT_CHIP)
        tabArmoryBtn.background = if (isArmory) UiTheme.roundBox(UiTheme.ACCENT_GREEN, 12f) else null

        tabHudBtn.setTextColor(if (!isArmory) UiTheme.TEXT_DARK else UiTheme.TEXT_CHIP)
        tabHudBtn.background = if (!isArmory) UiTheme.roundBox(UiTheme.ACCENT_CYAN, 12f) else null

        armoryPage.visibility = if (isArmory) View.VISIBLE else View.GONE
        hudStudioPage.visibility = if (!isArmory) View.VISIBLE else View.GONE

        if (!isArmory) {
            updateLiveHudMockup()
            refreshPerfLogBadge()
        }
    }

    private fun buildArmoryPage(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Compact SSAA Engine Card
        val engineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_NEON_DIM)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.sec_ssaa_engine)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_CYAN)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_reset_display)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_WHITE)
            setPadding(16, 6, 16, 6)
            background = UiTheme.roundBox(UiTheme.ACCENT_RED, 10f)
            setOnClickListener {
                if (ShizukuHelper.ensureReady(this@MainActivity)) {
                    ShizukuHelper.resetDisplay()
                    cancelActiveNotification()
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                    Toast.makeText(context, Lang.str(this@MainActivity, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
                }
            }
        })
        engineCard.addView(topRow)

        previewTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.reading_display)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_WHITE)
            setPadding(14, 10, 14, 10)
            background = UiTheme.roundBox(UiTheme.BG_PREVIEW, 10f, 1, UiTheme.STROKE_SUBTLE)
        }
        engineCard.addView(previewTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 8
            bottomMargin = 10
        })

        // 4 Tactile Scale Selector Buttons
        val scaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val scaleChips = mutableListOf<Pair<Int, TextView>>()
        fun refreshScaleButtons() {
            for ((fac, btn) in scaleChips) {
                val active = (fac == selectedFactor)
                btn.setTextColor(if (active) UiTheme.TEXT_DARK else UiTheme.TEXT_WHITE)
                btn.background = UiTheme.roundBox(
                    if (active) UiTheme.ACCENT_GREEN else UiTheme.BG_CHIP_INACTIVE,
                    11f,
                    if (active) 0 else 1,
                    UiTheme.STROKE_SUBTLE
                )
            }
        }
        listOf(110 to "1.10x", 115 to "1.15x", 120 to "1.20x", 125 to "1.25x").forEachIndexed { idx, (factor, label) ->
            val btn = TextView(this).apply {
                text = label
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(8, 11, 8, 11)
                setOnClickListener {
                    selectedFactor = factor
                    prefsMgr.savedFactor = factor
                    refreshScaleButtons()
                    updatePreviewText()
                    adapter.notifyDataSetChanged()
                }
            }
            scaleChips.add(factor to btn)
            scaleRow.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (idx < 3) rightMargin = 8
            })
        }
        refreshScaleButtons()
        engineCard.addView(scaleRow)

        // Quick Auto-HUD Toggle + Test Button
        val quickHudRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
        }
        quickHudRow.addView(CheckBox(this).apply {
            text = Lang.str(this@MainActivity, R.string.chk_telemetry_hud)
            textSize = 12f
            setTextColor(UiTheme.TEXT_CHIP)
            isChecked = enableFpsOverlay
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnCheckedChangeListener { _, isChecked ->
                enableFpsOverlay = isChecked
                prefsMgr.enableFpsOverlay = isChecked
                if (isChecked && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_grant_overlay_long), Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else if (!isChecked) {
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                }
            }
        })
        quickHudRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_test_hud)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_DARK)
            setPadding(16, 8, 16, 8)
            background = UiTheme.roundBox(UiTheme.ACCENT_TEST_GREEN, 10f)
            setOnClickListener { toggleHudPreviewService() }
        })
        engineCard.addView(quickHudRow)
        page.addView(engineCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        // Filter & Search Row
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchBox = EditText(this).apply {
            hint = Lang.str(this@MainActivity, R.string.hint_search)
            textSize = 12.5f
            setSingleLine(true)
            setHintTextColor(UiTheme.TEXT_MUTED)
            setTextColor(UiTheme.TEXT_WHITE)
            setPadding(20, 13, 20, 13)
            background = UiTheme.roundBox(UiTheme.BG_INPUT, 12f, 1, UiTheme.STROKE_SUBTLE)
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
            text = "⭐"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(18, 11, 18, 11)
            fun updateStyle() {
                background = UiTheme.roundBox(
                    if (showPinnedOnly) UiTheme.BTN_GOLD_BG else UiTheme.BG_INPUT,
                    12f,
                    1,
                    if (showPinnedOnly) UiTheme.ACCENT_STAR else UiTheme.STROKE_SUBTLE
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

        // Hint line
        page.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.subtitle_hint)
            textSize = 10.5f
            setTextColor(UiTheme.TEXT_SUB)
            setPadding(4, 0, 4, 8)
        })

        // Spacious Game List
        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 10
        }
        adapter = createAppAdapter()
        listView.adapter = adapter
        page.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        return page
    }

    private fun buildHudStudioPage(): ScrollView {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        // Section 0: Live HUD Preview Mockup Card
        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 16)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_NEON_DIM)
        }
        val prevTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prevTop.addView(makeSectionLabel(Lang.str(this, R.string.sec_hud_preview), UiTheme.ACCENT_GREEN).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        prevTop.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_test_hud)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_DARK)
            setPadding(16, 7, 16, 7)
            background = UiTheme.roundBox(UiTheme.ACCENT_TEST_GREEN, 10f)
            setOnClickListener { toggleHudPreviewService() }
        })
        previewBox.addView(prevTop)

        val stageFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 20)
            background = UiTheme.roundBox(UiTheme.BG_ROOT, 12f, 1, UiTheme.STROKE_SUBTLE)
        }
        val mockTv = TextView(this).apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_GREEN)
        }
        liveHudMockTv = mockTv
        stageFrame.addView(mockTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        previewBox.addView(stageFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })
        container.addView(previewBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        // Section 1: Layout, Size & Transparency
        val styleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 16)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_SUBTLE)
        }
        val styleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        styleHeader.addView(makeSectionLabel(Lang.str(this, R.string.sec_hud_style), UiTheme.ACCENT_CYAN).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        styleHeader.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_DARK)
            setPadding(16, 7, 16, 7)
            background = UiTheme.roundBox(UiTheme.ACCENT_GOLD, 10f)
            setOnClickListener {
                overlayVertical = !overlayVertical
                prefsMgr.overlayVertical = overlayVertical
                text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
                updateLiveHudMockup()
                notifyOverlayStyleUpdated()
            }
        })
        styleCard.addView(styleHeader)

        styleCard.addView(buildChipRow(
            items = listOf(
                0 to Lang.str(this, R.string.size_tiny),
                1 to Lang.str(this, R.string.size_small),
                2 to Lang.str(this, R.string.size_medium),
                3 to Lang.str(this, R.string.size_large)
            ),
            selectedIndex = overlaySizeLevel,
            activeColor = UiTheme.ACCENT_CYAN,
            padTop = 10,
            padBottom = 8
        ) { lvl ->
            overlaySizeLevel = lvl
            prefsMgr.overlaySizeLevel = lvl
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })

        styleCard.addView(buildChipRow(
            items = listOf(
                0 to Lang.str(this, R.string.bg_solid),
                1 to Lang.str(this, R.string.bg_glass),
                2 to Lang.str(this, R.string.bg_sheer),
                3 to Lang.str(this, R.string.bg_clear)
            ),
            selectedIndex = overlayBgMode,
            activeColor = UiTheme.ACCENT_PURPLE,
            padTop = 0,
            padBottom = 0
        ) { mode ->
            overlayBgMode = mode
            prefsMgr.overlayBgMode = mode
            updateLiveHudMockup()
            notifyOverlayStyleUpdated()
        })
        container.addView(styleCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        // Section 2: Polling Rate
        val pollCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 16)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_SUBTLE)
        }
        pollCard.addView(makeSectionLabel(Lang.str(this, R.string.sec_hud_poll), UiTheme.ACCENT_GREEN))
        pollCard.addView(buildChipRow(
            items = listOf(
                0 to Lang.str(this, R.string.poll_auto),
                1 to Lang.str(this, R.string.poll_300ms),
                2 to Lang.str(this, R.string.poll_1s),
                3 to Lang.str(this, R.string.poll_2s)
            ),
            selectedIndex = hudPollMode,
            activeColor = UiTheme.ACCENT_GREEN,
            padTop = 10,
            padBottom = 0
        ) { mode ->
            hudPollMode = mode
            prefsMgr.hudPollMode = mode
            notifyOverlayStyleUpdated()
        })
        container.addView(pollCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        // Section 3: Categorized 2-Column Telemetry Toggles (Spacious & Uncluttered!)
        container.addView(buildMetricCategoryCard(
            title = Lang.str(this, R.string.sec_metrics_perf),
            accentColor = UiTheme.ACCENT_GREEN,
            metrics = listOf(
                Triple("m_fps", Lang.str(this, R.string.metric_fps), true),
                Triple("m_watt", Lang.str(this, R.string.metric_watt), true),
                Triple("m_gpu", Lang.str(this, R.string.metric_vram), true),
                Triple("m_cpu_freq", Lang.str(this, R.string.metric_cpu_freq), false)
            )
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        container.addView(buildMetricCategoryCard(
            title = Lang.str(this, R.string.sec_metrics_core_temp),
            accentColor = UiTheme.ACCENT_GOLD,
            metrics = listOf(
                Triple("m_bat_temp", Lang.str(this, R.string.metric_bat_temp), true),
                Triple("m_soc_temp", Lang.str(this, R.string.metric_soc_temp), true),
                Triple("m_cpu_temp", Lang.str(this, R.string.metric_cpu_temp), false),
                Triple("m_gpu_temp", Lang.str(this, R.string.metric_gpu_temp), false)
            )
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        container.addView(buildMetricCategoryCard(
            title = Lang.str(this, R.string.sec_metrics_hw_temp),
            accentColor = UiTheme.ACCENT_CYAN,
            metrics = listOf(
                Triple("m_ddr_temp", Lang.str(this, R.string.metric_ddr_temp), false),
                Triple("m_cam_temp", Lang.str(this, R.string.metric_cam_temp), false),
                Triple("m_skin_temp", Lang.str(this, R.string.metric_skin_temp), false),
                Triple("m_ram", Lang.str(this, R.string.metric_ram), false)
            )
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })

        // Section 4: Tools & Performance Logs
        val toolsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 16)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_SUBTLE)
        }
        toolsCard.addView(makeSectionLabel(Lang.str(this, R.string.sec_hud_tools), UiTheme.ACCENT_PURPLE))

        val toolsRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 8)
        }
        toolsRow1.addView(makeActionChip(Lang.str(this, R.string.btn_quick_tile), UiTheme.ACCENT_CYAN, UiTheme.BTN_CYAN_BG, UiTheme.BTN_CYAN_STROKE) {
            requestAddQuickTile()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 8 })

        toolsRow1.addView(makeActionChip(Lang.str(this, R.string.btn_reset_pos), UiTheme.ACCENT_GOLD, UiTheme.BTN_GOLD_BG, UiTheme.BTN_GOLD_STROKE) {
            prefsMgr.resetHudPosition()
            if (FpsOverlayService.isServiceRunning) {
                startService(Intent(this, FpsOverlayService::class.java).apply { action = "RESET_POS" })
            }
            Toast.makeText(this, Lang.str(this, R.string.toast_pos_reset), Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolsCard.addView(toolsRow1)

        val toolsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        toolsRow2.addView(makeActionChip(Lang.str(this, R.string.btn_sensors), UiTheme.ACCENT_PURPLE, UiTheme.BTN_PURPLE_BG, UiTheme.BTN_PURPLE_STROKE) {
            showThermalInspectorDialog()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f).apply { rightMargin = 8 })

        val logBtn = makeActionChip(Lang.str(this, R.string.btn_perf_logs), UiTheme.ACCENT_GREEN, UiTheme.BG_PILL_ACTIVE, UiTheme.ACCENT_GREEN) {
            startActivity(Intent(this, PerfChartActivity::class.java))
        }
        perfLogBtn = logBtn
        toolsRow2.addView(logBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
        toolsCard.addView(toolsRow2)

        container.addView(toolsCard)
        scroll.addView(container)
        return scroll
    }

    private fun buildMetricCategoryCard(
        title: String,
        accentColor: Int,
        metrics: List<Triple<String, String, Boolean>>
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 16)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 16f, 1, UiTheme.STROKE_SUBTLE)
        }
        card.addView(makeSectionLabel(title, accentColor))

        // 2x2 Grid with generous spacing
        metrics.chunked(2).forEachIndexed { rowIdx, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, if (rowIdx == 0) 10 else 8, 0, 0)
            }
            pair.forEachIndexed { colIdx, (key, label, defVal) ->
                var enabled = prefsMgr.getMetric(key, defVal)
                val pill = TextView(this).apply {
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(18, 13, 16, 13)
                    fun updateStyle() {
                        text = if (enabled) "$label  ●" else label
                        setTextColor(if (enabled) UiTheme.ACCENT_GREEN else UiTheme.TEXT_CHIP)
                        background = UiTheme.roundBox(
                            if (enabled) UiTheme.BG_PILL_ACTIVE else UiTheme.BG_PILL_INACTIVE,
                            12f,
                            1,
                            if (enabled) UiTheme.ACCENT_GREEN else UiTheme.STROKE_PILL_OFF
                        )
                    }
                    updateStyle()
                    setOnClickListener {
                        enabled = !enabled
                        prefsMgr.setMetric(key, enabled)
                        updateStyle()
                        updateLiveHudMockup()
                        notifyOverlayStyleUpdated()
                    }
                }
                row.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (colIdx == 0 && pair.size > 1) rightMargin = 8
                })
            }
            card.addView(row)
        }
        return card
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

    private fun makeSectionLabel(text: String, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(color)
        }
    }

    private fun refreshPerfLogBadge() {
        val btn = perfLogBtn ?: return
        val baseTitle = Lang.str(this, R.string.btn_perf_logs)
        val count = try {
            PerfLogManager.listSessions(this).size
        } catch (_: Exception) {
            0
        }
        btn.text = if (count > 0) "$baseTitle ($count)" else baseTitle
    }

    private fun buildChipRow(
        items: List<Pair<Int, String>>,
        selectedIndex: Int,
        activeColor: Int,
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

        fun refresh() {
            chips.forEachIndexed { idx, chip ->
                val active = (idx == current)
                chip.setTextColor(if (active) UiTheme.TEXT_DARK else UiTheme.TEXT_CHIP)
                chip.background = UiTheme.roundBox(if (active) activeColor else UiTheme.BG_CHIP_INACTIVE, 11f)
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

    private fun makeActionChip(label: String, textColor: Int, bgColor: Int, strokeColor: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(10, 13, 10, 13)
            background = UiTheme.roundBox(bgColor, 12f, 1, strokeColor)
            setOnClickListener { onClick() }
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
                        textSize = 14.5f
                        setTypeface(null, Typeface.BOLD)
                    }
                    val pkgTv = TextView(this@MainActivity).apply {
                        textSize = 10.5f
                        setTextColor(UiTheme.TEXT_PKG)
                    }
                    textCol.addView(nameTv)
                    textCol.addView(pkgTv)

                    val scaleBadge = TextView(this@MainActivity).apply {
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(16, 9, 16, 9)
                        background = UiTheme.roundBox(UiTheme.BG_BADGE, 10f, 1, UiTheme.STROKE_SUBTLE)
                    }
                    val pinBtn = TextView(this@MainActivity).apply {
                        textSize = 19f
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
                    if (item.isPinned) UiTheme.BG_ROW_PINNED else UiTheme.BG_ROW,
                    16f,
                    1,
                    if (item.isPinned) UiTheme.ACCENT_GREEN else UiTheme.STROKE_SUBTLE
                )
                holder.img.setImageDrawable(item.icon)
                holder.nameTv.text = item.name
                holder.nameTv.setTextColor(if (item.isPinned) UiTheme.ACCENT_GREEN else UiTheme.TEXT_WHITE)
                holder.pkgTv.text = item.pkg

                val labelStr = "${effectiveScale / 100}.${effectiveScale % 100}x"
                holder.scaleBadge.text = if (item.customScale > 0) "🎯 $labelStr" else labelStr
                holder.scaleBadge.setTextColor(if (item.customScale > 0) UiTheme.ACCENT_GREEN else UiTheme.TEXT_CHIP)
                holder.scaleBadge.setOnClickListener { cyclePerGameScale(item) }

                holder.pinBtn.text = if (item.isPinned) "⭐" else "☆"
                holder.pinBtn.setTextColor(if (item.isPinned) UiTheme.ACCENT_STAR else UiTheme.TEXT_STAR_OFF)
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

    private fun updatePreviewText() {
        if (!::previewTv.isInitialized) return
        val newW = physW * selectedFactor / 100
        val newH = physH * selectedFactor / 100
        val newDen = physDen * selectedFactor / 100
        previewTv.text = Lang.str(this, R.string.preview_display_fmt, physW, physH, physDen, newW, newH, newDen)
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
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
    private val pinnedPkgs = mutableSetOf<String>()

    private var selectedFactor = 125
    private var showPinnedOnly = false
    private var enableFpsOverlay = true
    private var overlaySizeLevel = 1
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
            setPadding(32, 38, 32, 16)
        }

        // Header + Language Switcher
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerBar.addView(TextView(this).apply {
            text = "⚡ ${Lang.str(this@MainActivity, R.string.app_name).uppercase(Locale.US)}"
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_GREEN)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerBar.addView(TextView(this).apply {
            text = Lang.modeBadge(this@MainActivity)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_CYAN)
            setPadding(18, 9, 18, 9)
            background = UiTheme.roundBox(UiTheme.BTN_LANG_BG, 14f, 1, UiTheme.BTN_CYAN_STROKE)
            setOnClickListener {
                prefsMgr.appLang = (prefsMgr.appLang + 1) % 3
                notifyOverlayStyleUpdated()
                recreate()
            }
        })
        root.addView(headerBar)

        root.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.subtitle_hint)
            textSize = 11f
            setTextColor(UiTheme.TEXT_SUB)
            setPadding(0, 4, 0, 12)
        })

        previewTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.reading_display)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_CYAN)
            setPadding(20, 14, 20, 14)
            background = UiTheme.roundBox(UiTheme.BG_PREVIEW, 14f, 1, UiTheme.STROKE_SUBTLE)
        }
        root.addView(previewTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        listOf(110 to "1.10x", 115 to "1.15x", 120 to "1.20x", 125 to "1.25x").forEach { (factor, label) ->
            radioGroup.addView(RadioButton(this).apply {
                text = label
                textSize = 12.5f
                setTextColor(UiTheme.TEXT_WHITE)
                id = factor
                isChecked = (factor == selectedFactor)
                layoutParams = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    selectedFactor = factor
                    prefsMgr.savedFactor = factor
                    updatePreviewText()
                    adapter.notifyDataSetChanged()
                }
            })
        }
        root.addView(radioGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val resetBtn = Button(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_reset_display)
            textSize = 12.5f
            setTextColor(UiTheme.TEXT_WHITE)
            background = UiTheme.roundBox(UiTheme.ACCENT_RED, 18f)
            setOnClickListener {
                if (ShizukuHelper.ensureReady(this@MainActivity)) {
                    ShizukuHelper.resetDisplay()
                    cancelActiveNotification()
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                    Toast.makeText(context, Lang.str(this@MainActivity, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        root.addView(buildHudControlCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })

        root.addView(CheckBox(this).apply {
            text = Lang.str(this@MainActivity, R.string.chk_pinned_only)
            textSize = 12.5f
            setTextColor(UiTheme.ACCENT_STAR)
            isChecked = showPinnedOnly
            setOnCheckedChangeListener { _, isChecked ->
                showPinnedOnly = isChecked
                prefsMgr.showPinnedOnly = isChecked
                sortAndFilterApps()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 })

        root.addView(EditText(this).apply {
            hint = Lang.str(this@MainActivity, R.string.hint_search)
            textSize = 13f
            setHintTextColor(UiTheme.TEXT_MUTED)
            setTextColor(UiTheme.TEXT_WHITE)
            setPadding(24, 16, 24, 16)
            background = UiTheme.roundBox(UiTheme.BG_INPUT, 16f)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    currentQuery = s?.toString() ?: ""
                    sortAndFilterApps()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 12
        }
        adapter = createAppAdapter()
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        updatePreviewText()
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

    private fun buildHudControlCard(): LinearLayout {
        val hudCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 14)
            background = UiTheme.roundBox(UiTheme.BG_CARD, 18f, 1, UiTheme.STROKE_SUBTLE)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(CheckBox(this).apply {
            text = Lang.str(this@MainActivity, R.string.chk_telemetry_hud)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_CYAN)
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

        headerRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_DARK)
            setPadding(18, 8, 18, 8)
            background = UiTheme.roundBox(UiTheme.ACCENT_GOLD, 12f)
            setOnClickListener {
                overlayVertical = !overlayVertical
                prefsMgr.overlayVertical = overlayVertical
                text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
                notifyOverlayStyleUpdated()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })

        headerRow.addView(TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_test_hud)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.TEXT_WHITE)
            setPadding(18, 8, 18, 8)
            background = UiTheme.roundBox(UiTheme.ACCENT_TEST_GREEN, 12f)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_grant_overlay_short), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@setOnClickListener
                }
                if (!FpsOverlayService.isServiceRunning) {
                    val svc = Intent(this@MainActivity, FpsOverlayService::class.java).apply {
                        putExtra("target_pkg", "")
                        putExtra("game_name", "HUD Preview")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
                } else {
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                }
            }
        })
        hudCard.addView(headerRow)

        // Size chips row
        hudCard.addView(buildChipRow(
            items = listOf(
                0 to Lang.str(this, R.string.size_tiny),
                1 to Lang.str(this, R.string.size_small),
                2 to Lang.str(this, R.string.size_medium),
                3 to Lang.str(this, R.string.size_large)
            ),
            selectedIndex = overlaySizeLevel,
            activeColor = UiTheme.ACCENT_CYAN,
            padTop = 6,
            padBottom = 6
        ) { lvl ->
            overlaySizeLevel = lvl
            prefsMgr.overlaySizeLevel = lvl
            notifyOverlayStyleUpdated()
        })

        // Polling rate chips row
        hudCard.addView(buildChipRow(
            items = listOf(
                0 to Lang.str(this, R.string.poll_auto),
                1 to Lang.str(this, R.string.poll_300ms),
                2 to Lang.str(this, R.string.poll_1s),
                3 to Lang.str(this, R.string.poll_2s)
            ),
            selectedIndex = hudPollMode,
            activeColor = UiTheme.ACCENT_GREEN,
            padTop = 0,
            padBottom = 8
        ) { mode ->
            hudPollMode = mode
            prefsMgr.hudPollMode = mode
            notifyOverlayStyleUpdated()
        })

        // 12 Telemetry Metric Toggles
        val metricRows = listOf(
            listOf(
                Triple("m_fps", Lang.str(this, R.string.metric_fps), true),
                Triple("m_watt", Lang.str(this, R.string.metric_watt), true),
                Triple("m_gpu", Lang.str(this, R.string.metric_vram), true),
                Triple("m_cpu_freq", Lang.str(this, R.string.metric_cpu_freq), false)
            ),
            listOf(
                Triple("m_bat_temp", Lang.str(this, R.string.metric_bat_temp), true),
                Triple("m_soc_temp", Lang.str(this, R.string.metric_soc_temp), true),
                Triple("m_cpu_temp", Lang.str(this, R.string.metric_cpu_temp), false),
                Triple("m_gpu_temp", Lang.str(this, R.string.metric_gpu_temp), false)
            ),
            listOf(
                Triple("m_ddr_temp", Lang.str(this, R.string.metric_ddr_temp), false),
                Triple("m_cam_temp", Lang.str(this, R.string.metric_cam_temp), false),
                Triple("m_skin_temp", Lang.str(this, R.string.metric_skin_temp), false),
                Triple("m_ram", Lang.str(this, R.string.metric_ram), false)
            )
        )

        metricRows.forEachIndexed { rowIdx, rowItems ->
            val mRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (rowIdx > 0) setPadding(0, 6, 0, 0)
            }
            rowItems.forEachIndexed { colIdx, (key, label, defVal) ->
                var enabled = prefsMgr.getMetric(key, defVal)
                val pill = TextView(this).apply {
                    text = label
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(4, 8, 4, 8)
                    fun updateStyle() {
                        setTextColor(if (enabled) UiTheme.ACCENT_GREEN else UiTheme.TEXT_MUTED)
                        background = UiTheme.roundBox(
                            if (enabled) UiTheme.BG_PILL_ACTIVE else UiTheme.BG_PILL_INACTIVE,
                            10f,
                            1,
                            if (enabled) UiTheme.ACCENT_GREEN else UiTheme.STROKE_PILL_OFF
                        )
                    }
                    updateStyle()
                    setOnClickListener {
                        enabled = !enabled
                        prefsMgr.setMetric(key, enabled)
                        updateStyle()
                        notifyOverlayStyleUpdated()
                    }
                }
                mRow.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (colIdx < rowItems.size - 1) rightMargin = 5
                })
            }
            hudCard.addView(mRow)
        }

        // Bottom Utility Buttons (Quick Tile, Reset Pos, Sensors)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        bottomRow.addView(makeActionChip(Lang.str(this, R.string.btn_quick_tile), UiTheme.ACCENT_CYAN, UiTheme.BTN_CYAN_BG, UiTheme.BTN_CYAN_STROKE) {
            requestAddQuickTile()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f).apply { rightMargin = 6 })

        bottomRow.addView(makeActionChip(Lang.str(this, R.string.btn_reset_pos), UiTheme.ACCENT_GOLD, UiTheme.BTN_GOLD_BG, UiTheme.BTN_GOLD_STROKE) {
            prefsMgr.resetHudPosition()
            if (FpsOverlayService.isServiceRunning) {
                startService(Intent(this, FpsOverlayService::class.java).apply { action = "RESET_POS" })
            }
            Toast.makeText(this, Lang.str(this, R.string.toast_pos_reset), Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f).apply { rightMargin = 6 })

        bottomRow.addView(makeActionChip(Lang.str(this, R.string.btn_sensors), UiTheme.ACCENT_PURPLE, UiTheme.BTN_PURPLE_BG, UiTheme.BTN_PURPLE_STROKE) {
            showThermalInspectorDialog()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        hudCard.addView(bottomRow)
        return hudCard
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
                chip.background = UiTheme.roundBox(if (active) activeColor else UiTheme.BG_CHIP_INACTIVE, 10f)
            }
        }

        items.forEach { (id, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(6, 8, 6, 8)
                setOnClickListener {
                    current = id
                    refresh()
                    onSelect(id)
                }
            }
            chips.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (id < items.size - 1) rightMargin = 6
            })
        }
        refresh()
        return row
    }

    private fun makeActionChip(label: String, textColor: Int, bgColor: Int, strokeColor: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
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
                        setPadding(20, 16, 20, 16)
                    }
                    val img = ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(88, 88).apply { rightMargin = 20 }
                    }
                    val textCol = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val nameTv = TextView(this@MainActivity).apply {
                        textSize = 15f
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
                        setPadding(18, 10, 18, 10)
                        background = UiTheme.roundBox(UiTheme.BG_BADGE, 12f)
                    }
                    val pinBtn = TextView(this@MainActivity).apply {
                        textSize = 19f
                        setPadding(18, 8, 6, 8)
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
                    20f,
                    if (item.isPinned) 2 else 0,
                    UiTheme.ACCENT_GREEN
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
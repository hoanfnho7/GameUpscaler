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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
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
    private lateinit var prefs: SharedPreferences
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

        prefs = getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)
        selectedFactor = prefs.getInt("saved_factor", 125)
        showPinnedOnly = prefs.getBoolean("show_pinned_only", false)
        enableFpsOverlay = prefs.getBoolean("enable_fps_overlay", true)
        overlaySizeLevel = prefs.getInt("overlay_size_level", 1)
        hudPollMode = prefs.getInt("hud_poll_mode", 0)
        overlayVertical = prefs.getBoolean("overlay_vertical", false)

        if (handleIncomingIntent(intent)) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 102)
            }
        }

        val defaultPins = setOf(
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.vng.pubgmobile",
            "com.tencent.ig"
        )
        val savedSet = prefs.getStringSet("pinned_pkgs", null)
        if (savedSet == null) {
            pinnedPkgs.addAll(defaultPins)
            prefs.edit().putStringSet("pinned_pkgs", HashSet(pinnedPkgs)).apply()
        } else {
            pinnedPkgs.addAll(savedSet)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101216"))
            setPadding(32, 38, 32, 16)
        }

        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "⚡ ${Lang.str(this@MainActivity, R.string.app_name).uppercase(Locale.US)}"
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E676"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerBar.addView(title)

        val langBtn = TextView(this).apply {
            text = Lang.modeBadge(this@MainActivity)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64FFDA"))
            setPadding(18, 9, 18, 9)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B242B"))
                cornerRadius = 14f
                setStroke(1, Color.parseColor("#2E4A52"))
            }
            setOnClickListener {
                val nextLang = (Lang.getMode(this@MainActivity) + 1) % 3
                prefs.edit().putInt("app_lang", nextLang).apply()
                notifyOverlayStyleUpdated()
                recreate()
            }
        }
        headerBar.addView(langBtn)
        root.addView(headerBar)

        val subTitle = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.subtitle_hint)
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 4, 0, 12)
        }
        root.addView(subTitle)

        previewTv = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.reading_display)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64FFDA"))
            setPadding(20, 14, 20, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182026"))
                cornerRadius = 14f
                setStroke(1, Color.parseColor("#263238"))
            }
        }
        root.addView(previewTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        val scales = listOf(110 to "1.10x", 115 to "1.15x", 120 to "1.20x", 125 to "1.25x")
        scales.forEach { (factor, label) ->
            val rb = RadioButton(this).apply {
                text = label
                textSize = 12.5f
                setTextColor(Color.WHITE)
                id = factor
                isChecked = (factor == selectedFactor)
                layoutParams = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    selectedFactor = factor
                    prefs.edit().putInt("saved_factor", factor).apply()
                    updatePreviewText()
                    adapter.notifyDataSetChanged()
                }
            }
            radioGroup.addView(rb)
        }
        root.addView(radioGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val resetBtn = Button(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_reset_display)
            textSize = 12.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D32F2F"))
                cornerRadius = 18f
            }
            setOnClickListener {
                if (checkShizuku()) {
                    runShizukuScript("wm size reset; wm density reset")
                    cancelActiveNotification()
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                    Toast.makeText(context, Lang.str(this@MainActivity, R.string.toast_display_reset), Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        val hudCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161920"))
                cornerRadius = 18f
                setStroke(1, Color.parseColor("#263238"))
            }
        }

        val hudHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fpsCheck = CheckBox(this).apply {
            text = Lang.str(this@MainActivity, R.string.chk_telemetry_hud)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64FFDA"))
            isChecked = enableFpsOverlay
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnCheckedChangeListener { _, isChecked ->
                enableFpsOverlay = isChecked
                prefs.edit().putBoolean("enable_fps_overlay", isChecked).apply()
                if (isChecked && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_grant_overlay_long), Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else if (!isChecked) {
                    stopService(Intent(this@MainActivity, FpsOverlayService::class.java))
                }
            }
        }
        hudHeaderRow.addView(fpsCheck)

        val orientBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#101216"))
            setPadding(18, 8, 18, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD740"))
                cornerRadius = 12f
            }
            setOnClickListener {
                overlayVertical = !overlayVertical
                prefs.edit().putBoolean("overlay_vertical", overlayVertical).apply()
                text = Lang.str(this@MainActivity, if (overlayVertical) R.string.btn_orient_vert else R.string.btn_orient_horiz)
                notifyOverlayStyleUpdated()
            }
        }
        hudHeaderRow.addView(orientBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })

        val testOverlayBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_test_hud)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(18, 8, 18, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E7D32"))
                cornerRadius = 12f
            }
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
        }
        hudHeaderRow.addView(testOverlayBtn)
        hudCard.addView(hudHeaderRow)

        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
        }
        val sizeChips = mutableListOf<TextView>()
        val sizeLabels = listOf(
            0 to Lang.str(this, R.string.size_tiny),
            1 to Lang.str(this, R.string.size_small),
            2 to Lang.str(this, R.string.size_medium),
            3 to Lang.str(this, R.string.size_large)
        )
        fun refreshSizeChips() {
            sizeChips.forEachIndexed { idx, chip ->
                val active = (idx == overlaySizeLevel)
                chip.setTextColor(if (active) Color.parseColor("#101216") else Color.parseColor("#B0BEC5"))
                chip.background = GradientDrawable().apply {
                    setColor(if (active) Color.parseColor("#64FFDA") else Color.parseColor("#222630"))
                    cornerRadius = 10f
                }
            }
        }
        sizeLabels.forEach { (lvl, lbl) ->
            val chip = TextView(this).apply {
                text = lbl
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    overlaySizeLevel = lvl
                    prefs.edit().putInt("overlay_size_level", lvl).apply()
                    refreshSizeChips()
                    notifyOverlayStyleUpdated()
                }
            }
            sizeChips.add(chip)
            sizeRow.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (lvl < 3) rightMargin = 6 })
        }
        refreshSizeChips()
        hudCard.addView(sizeRow)

        val pollRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        val pollChips = mutableListOf<TextView>()
        val pollLabels = listOf(
            0 to Lang.str(this, R.string.poll_auto),
            1 to Lang.str(this, R.string.poll_300ms),
            2 to Lang.str(this, R.string.poll_1s),
            3 to Lang.str(this, R.string.poll_2s)
        )
        fun refreshPollChips() {
            pollChips.forEachIndexed { idx, chip ->
                val active = (idx == hudPollMode)
                chip.setTextColor(if (active) Color.parseColor("#101216") else Color.parseColor("#B0BEC5"))
                chip.background = GradientDrawable().apply {
                    setColor(if (active) Color.parseColor("#00E676") else Color.parseColor("#222630"))
                    cornerRadius = 10f
                }
            }
        }
        pollLabels.forEach { (mode, lbl) ->
            val chip = TextView(this).apply {
                text = lbl
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(6, 7, 6, 7)
                setOnClickListener {
                    hudPollMode = mode
                    prefs.edit().putInt("hud_poll_mode", mode).apply()
                    refreshPollChips()
                    notifyOverlayStyleUpdated()
                }
            }
            pollChips.add(chip)
            pollRow.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (mode < 3) rightMargin = 6 })
        }
        refreshPollChips()
        hudCard.addView(pollRow)

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
                var enabled = prefs.getBoolean(key, defVal)
                val pill = TextView(this).apply {
                    text = label
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(4, 8, 4, 8)
                    fun updatePillStyle() {
                        setTextColor(if (enabled) Color.parseColor("#00E676") else Color.parseColor("#757575"))
                        background = GradientDrawable().apply {
                            setColor(if (enabled) Color.parseColor("#1B2E24") else Color.parseColor("#1D2028"))
                            cornerRadius = 10f
                            setStroke(1, if (enabled) Color.parseColor("#00E676") else Color.parseColor("#2C303A"))
                        }
                    }
                    updatePillStyle()
                    setOnClickListener {
                        enabled = !enabled
                        prefs.edit().putBoolean(key, enabled).apply()
                        updatePillStyle()
                        notifyOverlayStyleUpdated()
                    }
                }
                mRow.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (colIdx < rowItems.size - 1) rightMargin = 5 })
            }
            hudCard.addView(mRow)
        }

        val actionBottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        val addQsTileBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_quick_tile)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64FFDA"))
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182429"))
                cornerRadius = 12f
                setStroke(1, Color.parseColor("#2E4A52"))
            }
            setOnClickListener {
                val hintMsg = Lang.str(this@MainActivity, R.string.hint_quick_tile)
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        val sbm = getSystemService(StatusBarManager::class.java)
                        sbm?.requestAddTileService(
                            ComponentName(this@MainActivity, HudTileService::class.java),
                            Lang.tileLabel(this@MainActivity),
                            Icon.createWithResource(this@MainActivity, R.drawable.ic_hud_tile),
                            mainExecutor
                        ) { result ->
                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_quick_tile_added), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, hintMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, hintMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
        actionBottomRow.addView(addQsTileBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f).apply { rightMargin = 6 })

        val resetPosBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_reset_pos)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFD740"))
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242118"))
                cornerRadius = 12f
                setStroke(1, Color.parseColor("#4A4228"))
            }
            setOnClickListener {
                prefs.edit()
                    .remove("overlay_land_x")
                    .remove("overlay_land_y")
                    .remove("overlay_port_x")
                    .remove("overlay_port_y")
                    .apply()
                if (FpsOverlayService.isServiceRunning) {
                    startService(Intent(this@MainActivity, FpsOverlayService::class.java).apply { action = "RESET_POS" })
                }
                Toast.makeText(this@MainActivity, Lang.str(this@MainActivity, R.string.toast_pos_reset), Toast.LENGTH_SHORT).show()
            }
        }
        actionBottomRow.addView(resetPosBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f).apply { rightMargin = 6 })

        val inspectSensorsBtn = TextView(this).apply {
            text = Lang.str(this@MainActivity, R.string.btn_sensors)
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CE93D8"))
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#231926"))
                cornerRadius = 12f
                setStroke(1, Color.parseColor("#472D4F"))
            }
            setOnClickListener { showThermalInspectorDialog() }
        }
        actionBottomRow.addView(inspectSensorsBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hudCard.addView(actionBottomRow)

        root.addView(hudCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })

        val favCheck = CheckBox(this).apply {
            text = Lang.str(this@MainActivity, R.string.chk_pinned_only)
            textSize = 12.5f
            setTextColor(Color.parseColor("#FFD600"))
            isChecked = showPinnedOnly
            setOnCheckedChangeListener { _, isChecked ->
                showPinnedOnly = isChecked
                prefs.edit().putBoolean("show_pinned_only", isChecked).apply()
                sortAndFilterApps()
            }
        }
        root.addView(favCheck, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 })

        val searchBox = EditText(this).apply {
            hint = Lang.str(this@MainActivity, R.string.hint_search)
            textSize = 13f
            setHintTextColor(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2229"))
                cornerRadius = 16f
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    currentQuery = s?.toString() ?: ""
                    sortAndFilterApps()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 12
        }

        class RowHolder(
            val row: LinearLayout,
            val img: ImageView,
            val nameTv: TextView,
            val pkgTv: TextView,
            val scaleBadge: TextView,
            val pinBtn: TextView
        )

        adapter = object : BaseAdapter() {
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
                        setTextColor(Color.parseColor("#888888"))
                    }
                    textCol.addView(nameTv)
                    textCol.addView(pkgTv)

                    val scaleBadge = TextView(this@MainActivity).apply {
                        textSize = 11.5f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(18, 10, 18, 10)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#263238"))
                            cornerRadius = 12f
                        }
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

                holder.row.background = GradientDrawable().apply {
                    setColor(if (item.isPinned) Color.parseColor("#1E2822") else Color.parseColor("#1A1D24"))
                    cornerRadius = 20f
                    if (item.isPinned) setStroke(2, Color.parseColor("#00E676"))
                }
                holder.img.setImageDrawable(item.icon)
                holder.nameTv.text = item.name
                holder.nameTv.setTextColor(if (item.isPinned) Color.parseColor("#00E676") else Color.WHITE)
                holder.pkgTv.text = item.pkg

                val labelStr = "${effectiveScale / 100}.${effectiveScale % 100}x"
                holder.scaleBadge.text = if (item.customScale > 0) "🎯 $labelStr" else labelStr
                holder.scaleBadge.setTextColor(if (item.customScale > 0) Color.parseColor("#00E676") else Color.parseColor("#B0BEC5"))
                holder.scaleBadge.setOnClickListener { cyclePerGameScale(item) }

                holder.pinBtn.text = if (item.isPinned) "⭐" else "☆"
                holder.pinBtn.setTextColor(if (item.isPinned) Color.parseColor("#FFD600") else Color.parseColor("#616161"))
                holder.pinBtn.setOnClickListener { togglePin(item) }

                holder.row.setOnClickListener { launchGameByPkg(item.pkg, item.name, effectiveScale) }
                holder.row.setOnLongClickListener {
                    createPinnedShortcut(item, effectiveScale)
                    true
                }
                return holder.row
            }
        }
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        updatePreviewText()
        if (checkShizuku()) fetchPhysicalDisplayInfo()
        loadInstalledApps()
    }

    override fun onDestroy() {
        allApps.clear()
        filteredApps.clear()
        super.onDestroy()
    }

    private fun showThermalInspectorDialog() {
        if (!checkShizuku()) return
        Toast.makeText(this, Lang.str(this, R.string.toast_scanning_sensors), Toast.LENGTH_SHORT).show()
        thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val cmd = "for z in /sys/class/thermal/thermal_zone*; do " +
                        "if [ -r \"\$z/type\" ] && [ -r \"\$z/temp\" ]; then " +
                        "read -r t < \"\$z/type\"; read -r v < \"\$z/temp\"; " +
                        "printf \"%s|%s\\n\" \"\$t\" \"\$v\"; fi; done"
                val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val lines = BufferedReader(InputStreamReader(proc.inputStream)).readLines()
                proc.waitFor()

                val report = StringBuilder()
                for (line in lines) {
                    val parts = line.split("|")
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val raw = parts[1].trim().toFloatOrNull() ?: continue
                        val c = when {
                            raw > 1000f -> raw / 1000f
                            raw > 200f -> raw / 10f
                            else -> raw
                        }
                        if (c in 15f..125f) {
                            report.append(String.format(Locale.US, "• %-22s : %.1f°C\n", name, c))
                        }
                    }
                }
                val finalText = if (report.isEmpty()) Lang.str(this, R.string.no_sensors_found) else report.toString()
                val dlgTitle = Lang.str(this, R.string.dlg_sensors_title, lines.size)
                val btnText = Lang.str(this, R.string.btn_close)
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(dlgTitle)
                        .setMessage(finalText)
                        .setPositiveButton(btnText, null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun notifyOverlayStyleUpdated() {
        if (FpsOverlayService.isServiceRunning) {
            val upd = Intent(this, FpsOverlayService::class.java).apply { action = "UPDATE_STYLE" }
            startService(upd)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && handleIncomingIntent(intent)) finish()
    }

    private fun handleIncomingIntent(intent: Intent): Boolean {
        val action = intent.getStringExtra("action_type") ?: return false
        if (action == "RESET_NOW") {
            if (checkShizuku()) {
                runShizukuScript("wm size reset; wm density reset")
                cancelActiveNotification()
                stopService(Intent(this, FpsOverlayService::class.java))
                Toast.makeText(this, Lang.str(this, R.string.toast_display_reset_short), Toast.LENGTH_SHORT).show()
            }
            return true
        } else if (action == "QUICK_LAUNCH") {
            val pkg = intent.getStringExtra("target_pkg") ?: return true
            val name = intent.getStringExtra("target_name") ?: pkg
            val savedCustom = prefs.getInt("scale_$pkg", 0)
            val factor = if (savedCustom > 0) savedCustom else prefs.getInt("saved_factor", 125)
            launchGameByPkg(pkg, name, factor)
            return true
        }
        return false
    }

    private fun cyclePerGameScale(item: AppItem) {
        val cycle = listOf(0, 110, 115, 120, 125)
        val nextIdx = (cycle.indexOf(item.customScale) + 1) % cycle.size
        item.customScale = cycle[nextIdx]
        prefs.edit().putInt("scale_${item.pkg}", item.customScale).apply()

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

    private fun fetchPhysicalDisplayInfo() {
        thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val cmd = "wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'; wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'"
                val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val lines = BufferedReader(InputStreamReader(proc.inputStream)).readLines()
                proc.waitFor()
                if (lines.size >= 2) {
                    val parts = lines[0].split("x")
                    if (parts.size == 2) {
                        physW = parts[0].toIntOrNull() ?: 1440
                        physH = parts[1].toIntOrNull() ?: 3168
                    }
                    physDen = lines[1].toIntOrNull() ?: 624
                    runOnUiThread { updatePreviewText() }
                }
            } catch (_: Exception) {}
        }
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
            val channel = NotificationChannel(channelId, Lang.str(this, R.string.notif_channel_upscale), NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val resetIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("action_type", "RESET_NOW")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 99, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1001)
    }

    private fun togglePin(item: AppItem) {
        if (pinnedPkgs.contains(item.pkg)) {
            pinnedPkgs.remove(item.pkg)
            item.isPinned = false
        } else {
            pinnedPkgs.add(item.pkg)
            item.isPinned = true
        }
        prefs.edit().putStringSet("pinned_pkgs", HashSet(pinnedPkgs)).apply()
        sortAndFilterApps()
    }

    private fun sortAndFilterApps() {
        allApps.forEach {
            it.isPinned = pinnedPkgs.contains(it.pkg)
            it.customScale = prefs.getInt("scale_${it.pkg}", 0)
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

    private fun checkShizuku(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, Lang.str(this, R.string.toast_start_shizuku), Toast.LENGTH_LONG).show()
                false
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
                false
            } else true
        } catch (e: Exception) {
            Toast.makeText(this, Lang.str(this, R.string.toast_shizuku_error), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun loadInstalledApps() {
        thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolves = pm.queryIntentActivities(intent, 0)
            val temp = resolves.mapNotNull { r ->
                val pkg = r.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                // Thu nhỏ icon về 64x64px để giảm ~16 lần RAM chiếm dụng
                val scaledIcon = BitmapDrawable(resources, drawableToBitmap(r.loadIcon(pm), 64))
                AppItem(
                    r.loadLabel(pm).toString(),
                    pkg,
                    scaledIcon,
                    pinnedPkgs.contains(pkg),
                    prefs.getInt("scale_$pkg", 0)
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
        if (!checkShizuku()) return
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

        val dl = "$"
        val script = "PHYS_RES=" + dl + "(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); " +
                "PHYS_DEN=" + dl + "(wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'); " +
                "W=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f1); " +
                "H=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f2); " +
                "NEW_W=" + dl + "(( W * " + factor + " / 100 )); " +
                "NEW_H=" + dl + "(( H * " + factor + " / 100 )); " +
                "NEW_DEN=" + dl + "(( PHYS_DEN * " + factor + " / 100 )); " +
                "wm size " + dl + "{NEW_W}x" + dl + "{NEW_H}; " +
                "wm density " + dl + "{NEW_DEN}; " +
                "sleep 1; " +
                "ACT=" + dl + "(cmd package resolve-activity --brief " + pkg + " | tr -d '\\r' | tail -n 1); " +
                "am start --user 0 -n \"" + dl + "ACT\" > /dev/null 2>&1; " +
                "nohup sh -c 'sleep 8; while pidof " + pkg + " > /dev/null; do sleep 3; done; wm size reset; wm density reset' >/dev/null 2>&1 &"

        runShizukuScript(script)
        // Đóng MainActivity để giải phóng toàn bộ giao diện và danh sách icon khỏi RAM
        finish()
    }

    private fun runShizukuScript(cmd: String) {
        thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                proc.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
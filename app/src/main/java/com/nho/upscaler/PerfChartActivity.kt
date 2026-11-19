package com.nho.upscaler

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerfChartActivity : Activity() {
    private var allSessions = mutableListOf<PerfSession>()
    private var filteredSessions = mutableListOf<PerfSession>()
    private var gameGroups = mutableListOf<Pair<String, String>>()
    private var selectedGameKey = "__ALL__"
    private var currentSessionIndex = 0

    private lateinit var gameChipRow: LinearLayout
    private lateinit var sessionChipRow: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private var scrubBannerTv: TextView? = null
    private val chartViews = mutableListOf<TelemetryChartView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.bgRoot(this@PerfChartActivity))
            setPadding(28, 36, 28, 16)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "←"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.accent(this@PerfChartActivity))
            setPadding(12, 6, 22, 6)
            setOnClickListener { finish() }
        })
        topBar.addView(TextView(this).apply {
            text = Lang.str(this@PerfChartActivity, R.string.chart_title).uppercase(Locale.US)
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.textPrimary(this@PerfChartActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(TextView(this).apply {
            text = Lang.str(this@PerfChartActivity, R.string.chart_delete_btn)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(UiTheme.ACCENT_RED_BRIGHT)
            setPadding(16, 8, 16, 8)
            background = UiTheme.roundBox(UiTheme.bgCard(this@PerfChartActivity), 12f, 1, UiTheme.ACCENT_RED_BRIGHT)
            setOnClickListener {
                if (filteredSessions.isNotEmpty()) {
                    val target = filteredSessions[currentSessionIndex]
                    PerfLogManager.deleteSession(this@PerfChartActivity, target.fileName)
                    Toast.makeText(this@PerfChartActivity, Lang.str(this@PerfChartActivity, R.string.chart_deleted_toast), Toast.LENGTH_SHORT).show()
                    reloadAll()
                }
            }
        })
        root.addView(topBar)

        val gameScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 12, 0, 6)
        }
        gameChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gameScroll.addView(gameChipRow)
        root.addView(gameScroll)

        val sessionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 4, 0, 10)
        }
        sessionChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sessionScroll.addView(sessionChipRow)
        root.addView(sessionScroll)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        contentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(contentContainer)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        reloadAll()
    }

    private fun sessionGroupKey(s: PerfSession): String = s.pkg.ifBlank { s.gameName }

    private fun reloadAll() {
        allSessions = PerfLogManager.listSessions(this).toMutableList()
        gameGroups.clear()

        val grouped = LinkedHashMap<String, MutableList<PerfSession>>()
        for (s in allSessions) {
            grouped.getOrPut(sessionGroupKey(s)) { mutableListOf() }.add(s)
        }
        for ((k, list) in grouped) {
            gameGroups.add(k to "${list.first().gameName} (${list.size})")
        }
        if (selectedGameKey != "__ALL__" && grouped[selectedGameKey] == null) {
            selectedGameKey = "__ALL__"
        }
        applyGameFilter()
    }

    private fun applyGameFilter() {
        filteredSessions = if (selectedGameKey == "__ALL__") {
            allSessions.toMutableList()
        } else {
            allSessions.filter { sessionGroupKey(it) == selectedGameKey }.toMutableList()
        }
        currentSessionIndex = 0
        rebuildGameChips()
        rebuildSessionChips()
        renderSelectedSession()
    }

    private fun rebuildGameChips() {
        gameChipRow.removeAllViews()
        if (allSessions.isEmpty()) return

        val accentCol = UiTheme.accent(this)
        val allActive = (selectedGameKey == "__ALL__")
        gameChipRow.addView(TextView(this).apply {
            text = Lang.str(this@PerfChartActivity, R.string.chart_all_games, allSessions.size)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (allActive) UiTheme.textOnAccent(this@PerfChartActivity) else UiTheme.textPrimary(this@PerfChartActivity))
            setPadding(16, 8, 16, 8)
            background = UiTheme.roundBox(
                if (allActive) accentCol else UiTheme.bgCard(this@PerfChartActivity),
                12f,
                if (allActive) 0 else 1,
                UiTheme.strokeSubtle(this@PerfChartActivity)
            )
            setOnClickListener {
                selectedGameKey = "__ALL__"
                applyGameFilter()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })

        for ((key, label) in gameGroups) {
            val active = (selectedGameKey == key)
            gameChipRow.addView(TextView(this).apply {
                text = label
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) UiTheme.textOnAccent(this@PerfChartActivity) else UiTheme.textPrimary(this@PerfChartActivity))
                setPadding(16, 8, 16, 8)
                background = UiTheme.roundBox(
                    if (active) accentCol else UiTheme.bgCard(this@PerfChartActivity),
                    12f,
                    if (active) 0 else 1,
                    UiTheme.strokeSubtle(this@PerfChartActivity)
                )
                setOnClickListener {
                    selectedGameKey = key
                    applyGameFilter()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })
        }
    }

    private fun rebuildSessionChips() {
        sessionChipRow.removeAllViews()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        val accentCol = UiTheme.accent(this)
        filteredSessions.forEachIndexed { idx, s ->
            val active = (idx == currentSessionIndex)
            val durStr = formatDuration(s.durationSec)
            val prefix = if (selectedGameKey == "__ALL__") "${s.gameName} • " else ""
            val chip = TextView(this).apply {
                text = "$prefix${s.scaleLabel} • ${String.format(Locale.US, "%.1fF", s.avgFps)} • $durStr • ${fmt.format(Date(s.timestampMs))}"
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) accentCol else UiTheme.textSecondary(this@PerfChartActivity))
                setPadding(16, 9, 16, 9)
                background = UiTheme.roundBox(
                    if (active) UiTheme.bgTileActive(this@PerfChartActivity) else UiTheme.bgCard(this@PerfChartActivity),
                    12f,
                    1,
                    if (active) accentCol else UiTheme.strokeSubtle(this@PerfChartActivity)
                )
                setOnClickListener {
                    currentSessionIndex = idx
                    rebuildSessionChips()
                    renderSelectedSession()
                }
            }
            sessionChipRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8 })
        }
    }

    private fun renderSelectedSession() {
        contentContainer.removeAllViews()
        chartViews.clear()

        if (filteredSessions.isEmpty()) {
            contentContainer.addView(TextView(this).apply {
                text = Lang.str(this@PerfChartActivity, R.string.chart_no_logs)
                textSize = 14f
                setTextColor(UiTheme.textSecondary(this@PerfChartActivity))
                gravity = Gravity.CENTER
                setPadding(24, 140, 24, 40)
            })
            return
        }

        val s = filteredSessions[currentSessionIndex]
        val accentCol = UiTheme.accent(this)

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 8)
        }
        row1.addView(makeKpiCard("AVG / 1% LOW", String.format(Locale.US, "%.1f / %.1f", s.avgFps, s.low1Fps), "Min ${String.format(Locale.US, "%.0f", s.minFps)} • Max ${String.format(Locale.US, "%.0f", s.maxFps)}", accentCol), kpiLp(true))
        row1.addView(makeKpiCard("AVG / PEAK W", String.format(Locale.US, "%.1fW / %.1fW", s.avgWatts, s.maxWatts), String.format(Locale.US, "%.0f mWh • %s", s.energyMwh, formatDuration(s.durationSec)), UiTheme.ACCENT_GOLD), kpiLp(true))
        row1.addView(makeKpiCard("PEAK TEMP", String.format(Locale.US, "SoC %.0f°C", s.peakSocTemp), String.format(Locale.US, "Bat %.1f°C • %s", s.peakBatTemp, s.scaleLabel), UiTheme.ACCENT_RED_BRIGHT), kpiLp(false))
        contentContainer.addView(row1)

        val banner = TextView(this).apply {
            text = Lang.str(this@PerfChartActivity, R.string.chart_scrub_hint)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentCol)
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            background = UiTheme.roundBox(UiTheme.bgTileActive(this@PerfChartActivity), 14f, 1, UiTheme.strokeSubtle(this@PerfChartActivity))
        }
        scrubBannerTv = banner
        contentContainer.addView(banner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 })

        val fpsChart = TelemetryChartView(
            context = this,
            title = Lang.str(this, R.string.chart_fps_title),
            minVal = 0f,
            maxVal = 144f,
            gridSteps = listOf(30f, 60f, 90f, 120f, 144f),
            primaryValues = s.samples.map { it.fps },
            primaryColor = accentCol,
            secondaryValues = null,
            secondaryColor = 0
        ) { idx -> syncScrub(s, idx) }
        chartViews.add(fpsChart)
        contentContainer.addView(fpsChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 380).apply { bottomMargin = 12 })

        val maxW = (s.maxWatts * 1.2f).coerceAtLeast(8f)
        val midW = (maxW / 2f)
        val wattChart = TelemetryChartView(
            context = this,
            title = Lang.str(this, R.string.chart_power_title),
            minVal = 0f,
            maxVal = maxW,
            gridSteps = listOf(midW * 0.5f, midW, midW * 1.5f),
            primaryValues = s.samples.map { it.watts },
            primaryColor = UiTheme.ACCENT_GOLD,
            secondaryValues = null,
            secondaryColor = 0
        ) { idx -> syncScrub(s, idx) }
        chartViews.add(wattChart)
        contentContainer.addView(wattChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 340).apply { bottomMargin = 12 })

        val maxT = (maxOf(s.peakSocTemp, s.peakBatTemp) + 8f).coerceIn(55f, 110f)
        val tempChart = TelemetryChartView(
            context = this,
            title = Lang.str(this, R.string.chart_temp_title),
            minVal = 25f,
            maxVal = maxT,
            gridSteps = listOf(40f, 55f, 70f, 85f).filter { it < maxT },
            primaryValues = s.samples.map { it.socTemp },
            primaryColor = UiTheme.ACCENT_RED_BRIGHT,
            secondaryValues = s.samples.map { it.batTemp },
            secondaryColor = UiTheme.ACCENT_CYAN
        ) { idx -> syncScrub(s, idx) }
        chartViews.add(tempChart)
        contentContainer.addView(tempChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 360).apply { bottomMargin = 20 })
    }

    private fun syncScrub(session: PerfSession, sampleIndex: Int) {
        for (cv in chartViews) cv.setScrubIndex(sampleIndex)
        val pt = session.samples.getOrNull(sampleIndex) ?: return
        scrubBannerTv?.text = String.format(
            Locale.US,
            "%s  |  %.1f FPS  |  %.1fW  |  SoC %.1f°C  |  Bat %.1f°C",
            formatDuration(pt.sec), pt.fps, pt.watts, pt.socTemp, pt.batTemp
        )
    }

    private fun kpiLp(withMargin: Boolean): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (withMargin) rightMargin = 8
        }
    }

    private fun makeKpiCard(title: String, mainVal: String, subVal: String, accentColor: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = UiTheme.roundBox(UiTheme.bgCard(this@PerfChartActivity), 14f, 1, UiTheme.strokeSubtle(this@PerfChartActivity))
            addView(TextView(this@PerfChartActivity).apply {
                text = title
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UiTheme.textSecondary(this@PerfChartActivity))
            })
            addView(TextView(this@PerfChartActivity).apply {
                text = mainVal
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
                setPadding(0, 4, 0, 2)
            })
            addView(TextView(this@PerfChartActivity).apply {
                text = subVal
                textSize = 9.5f
                setTextColor(UiTheme.textSecondary(this@PerfChartActivity))
            })
        }
    }

    private fun formatDuration(totalSec: Int): String {
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
    }
}

class TelemetryChartView(
    context: Context,
    private val title: String,
    private val minVal: Float,
    private val maxVal: Float,
    private val gridSteps: List<Float>,
    private val primaryValues: List<Float>,
    private val primaryColor: Int,
    private val secondaryValues: List<Float>?,
    private val secondaryColor: Int,
    private val onScrub: (Int) -> Unit
) : View(context) {

    private var scrubIdx = -1

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.bgCard(context)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.strokeSubtle(context)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.divider(context)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.textSecondary(context)
        textSize = 24f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.textPrimary(context)
        textSize = 27f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val secLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scrubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiTheme.textPrimary(context)
        strokeWidth = 2.5f
    }

    fun setScrubIndex(idx: Int) {
        if (scrubIdx != idx) {
            scrubIdx = idx
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (primaryValues.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val padLeft = 72f
                val padRight = 24f
                val plotW = (width - padLeft - padRight).coerceAtLeast(1f)
                val ratio = ((event.x - padLeft) / plotW).coerceIn(0f, 1f)
                val idx = (ratio * (primaryValues.size - 1)).toInt().coerceIn(0, primaryValues.size - 1)
                onScrub(idx)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 24f, 24f, bgPaint)
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, 24f, 24f, borderPaint)

        canvas.drawText(title, 24f, 40f, titlePaint)

        val padLeft = 72f
        val padRight = 24f
        val padTop = 64f
        val padBottom = 26f
        val plotW = (w - padLeft - padRight).coerceAtLeast(1f)
        val plotH = (h - padTop - padBottom).coerceAtLeast(1f)
        val span = (maxVal - minVal).coerceAtLeast(1f)

        for (step in gridSteps) {
            val norm = ((step - minVal) / span).coerceIn(0f, 1f)
            val y = padTop + plotH * (1f - norm)
            canvas.drawLine(padLeft, y, w - padRight, y, gridPaint)
            canvas.drawText(String.format(Locale.US, "%.0f", step), 14f, y + 8f, labelPaint)
        }

        if (primaryValues.size < 2) return

        val linePath = Path()
        val areaPath = Path()
        val count = primaryValues.size

        for (i in 0 until count) {
            val x = padLeft + (i.toFloat() / (count - 1)) * plotW
            val norm = ((primaryValues[i] - minVal) / span).coerceIn(0f, 1f)
            val y = padTop + plotH * (1f - norm)
            if (i == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, padTop + plotH)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(padLeft + plotW, padTop + plotH)
        areaPath.close()

        fillPaint.shader = LinearGradient(
            0f, padTop, 0f, padTop + plotH,
            Color.argb(60, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(areaPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        if (secondaryValues != null && secondaryValues.size == count) {
            val secPath = Path()
            for (i in 0 until count) {
                val x = padLeft + (i.toFloat() / (count - 1)) * plotW
                val norm = ((secondaryValues[i] - minVal) / span).coerceIn(0f, 1f)
                val y = padTop + plotH * (1f - norm)
                if (i == 0) secPath.moveTo(x, y) else secPath.lineTo(x, y)
            }
            canvas.drawPath(secPath, secLinePaint)
        }

        if (scrubIdx in 0 until count) {
            val sx = padLeft + (scrubIdx.toFloat() / (count - 1)) * plotW
            canvas.drawLine(sx, padTop, sx, padTop + plotH, scrubPaint)
            val norm = ((primaryValues[scrubIdx] - minVal) / span).coerceIn(0f, 1f)
            val sy = padTop + plotH * (1f - norm)
            canvas.drawCircle(sx, sy, 8f, linePaint)
        }
    }
}
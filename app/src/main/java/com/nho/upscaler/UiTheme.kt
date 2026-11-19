package com.nho.upscaler

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UiTheme {
    val BG_ROOT = Color.parseColor("#101216")
    val BG_CARD = Color.parseColor("#161920")
    val BG_INPUT = Color.parseColor("#1E2229")
    val BG_PREVIEW = Color.parseColor("#182026")
    val BG_ROW = Color.parseColor("#1A1D24")
    val BG_ROW_PINNED = Color.parseColor("#1E2822")
    val BG_CHIP_INACTIVE = Color.parseColor("#222630")
    val BG_PILL_ACTIVE = Color.parseColor("#1B2E24")
    val BG_PILL_INACTIVE = Color.parseColor("#1D2028")
    val BG_BADGE = Color.parseColor("#263238")

    val BTN_CYAN_BG = Color.parseColor("#182429")
    val BTN_CYAN_STROKE = Color.parseColor("#2E4A52")
    val BTN_GOLD_BG = Color.parseColor("#242118")
    val BTN_GOLD_STROKE = Color.parseColor("#4A4228")
    val BTN_PURPLE_BG = Color.parseColor("#231926")
    val BTN_PURPLE_STROKE = Color.parseColor("#472D4F")
    val BTN_LANG_BG = Color.parseColor("#1B242B")

    val ACCENT_GREEN = Color.parseColor("#00E676")
    val ACCENT_CYAN = Color.parseColor("#64FFDA")
    val ACCENT_GOLD = Color.parseColor("#FFD740")
    val ACCENT_STAR = Color.parseColor("#FFD600")
    val ACCENT_PURPLE = Color.parseColor("#CE93D8")
    val ACCENT_RED = Color.parseColor("#D32F2F")
    val ACCENT_RED_BRIGHT = Color.parseColor("#FF5252")
    val ACCENT_RED_ALERT = Color.parseColor("#FF1744")
    val ACCENT_TEST_GREEN = Color.parseColor("#2E7D32")

    val TEXT_WHITE = Color.WHITE
    val TEXT_DARK = Color.parseColor("#101216")
    val TEXT_SUB = Color.parseColor("#9E9E9E")
    val TEXT_CHIP = Color.parseColor("#B0BEC5")
    val TEXT_MUTED = Color.parseColor("#757575")
    val TEXT_PKG = Color.parseColor("#888888")
    val TEXT_STAR_OFF = Color.parseColor("#616161")

    val STROKE_SUBTLE = Color.parseColor("#263238")
    val STROKE_PILL_OFF = Color.parseColor("#2C303A")

    val HUD_BG_LEVELS = intArrayOf(
        Color.parseColor("#88101216"),
        Color.parseColor("#B3101216"),
        Color.parseColor("#CC101216"),
        Color.parseColor("#DD101216")
    )
    val HUD_STROKE_LEVELS = intArrayOf(
        Color.parseColor("#4400E676"),
        Color.parseColor("#5500E676"),
        Color.parseColor("#6600E676"),
        Color.parseColor("#7700E676")
    )
    val CLOSE_ZONE_IDLE = Color.parseColor("#CC261212")

    fun roundBox(fillColor: Int, radiusPx: Float, strokeWidth: Int = 0, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusPx
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }
    }

    fun ovalBox(fillColor: Int, strokeWidth: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }
    }
}
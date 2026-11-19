package com.nho.upscaler

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UiTheme {
    val BG_ROOT = Color.parseColor("#090B10")
    val BG_CARD = Color.parseColor("#11151E")
    val BG_CARD_ALT = Color.parseColor("#151A25")
    val BG_INPUT = Color.parseColor("#131822")
    val BG_PREVIEW = Color.parseColor("#0D1917")
    val BG_ROW = Color.parseColor("#11151E")
    val BG_ROW_PINNED = Color.parseColor("#11211B")
    val BG_CHIP_INACTIVE = Color.parseColor("#181E2B")
    val BG_PILL_ACTIVE = Color.parseColor("#102920")
    val BG_PILL_INACTIVE = Color.parseColor("#141923")
    val BG_BADGE = Color.parseColor("#1B2432")

    val BTN_CYAN_BG = Color.parseColor("#0F2126")
    val BTN_CYAN_STROKE = Color.parseColor("#1F4E5B")
    val BTN_GOLD_BG = Color.parseColor("#241E11")
    val BTN_GOLD_STROKE = Color.parseColor("#57461E")
    val BTN_PURPLE_BG = Color.parseColor("#1F1526")
    val BTN_PURPLE_STROKE = Color.parseColor("#4D2B63")
    val BTN_LANG_BG = Color.parseColor("#131B26")

    val ACCENT_GREEN = Color.parseColor("#00F58C")
    val ACCENT_CYAN = Color.parseColor("#00E5FF")
    val ACCENT_GOLD = Color.parseColor("#FFC400")
    val ACCENT_STAR = Color.parseColor("#FFD600")
    val ACCENT_PURPLE = Color.parseColor("#D580FF")
    val ACCENT_RED = Color.parseColor("#D50000")
    val ACCENT_RED_BRIGHT = Color.parseColor("#FF5252")
    val ACCENT_RED_ALERT = Color.parseColor("#FF1744")
    val ACCENT_TEST_GREEN = Color.parseColor("#00BFA5")

    val TEXT_WHITE = Color.parseColor("#F5F7FA")
    val TEXT_DARK = Color.parseColor("#090B10")
    val TEXT_SUB = Color.parseColor("#8A94A6")
    val TEXT_CHIP = Color.parseColor("#B0BAC9")
    val TEXT_MUTED = Color.parseColor("#646E82")
    val TEXT_PKG = Color.parseColor("#6E788C")
    val TEXT_STAR_OFF = Color.parseColor("#4A5366")
    val TEXT_SHADOW = Color.parseColor("#F0000000")

    val STROKE_SUBTLE = Color.parseColor("#1E2636")
    val STROKE_NEON_DIM = Color.parseColor("#1E3D34")
    val STROKE_PILL_OFF = Color.parseColor("#222A3B")

    val HUD_BG_OPACITY = intArrayOf(
        Color.parseColor("#DD090B10"),
        Color.parseColor("#99090B10"),
        Color.parseColor("#38090B10"),
        Color.TRANSPARENT
    )
    val HUD_STROKE_OPACITY = intArrayOf(
        Color.parseColor("#6600F58C"),
        Color.parseColor("#4400F58C"),
        Color.parseColor("#2400F58C"),
        Color.TRANSPARENT
    )
    val CLOSE_ZONE_IDLE = Color.parseColor("#CC261212")

    fun roundBox(fillColor: Int, radiusPx: Float, strokeWidth: Int = 0, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusPx
            if (strokeWidth > 0 && strokeColor != Color.TRANSPARENT) {
                setStroke(strokeWidth, strokeColor)
            }
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
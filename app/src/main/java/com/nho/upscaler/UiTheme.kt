package com.nho.upscaler

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UiTheme {
    // Cyber-Utility Palette from GameCraft SSAA DESIGN.md
    val VOLT_LIME = Color.parseColor("#D0EF65")
    val VOLT_LIME_BRIGHT = Color.parseColor("#D4F469")
    val VOLT_DARK_TEXT = Color.parseColor("#171E00")
    val TECH_AMBER = Color.parseColor("#EEC14A")
    val TECH_EMERALD = Color.parseColor("#6DFE9C")
    val TECH_RED = Color.parseColor("#FF5252")
    val TECH_BLUE = Color.parseColor("#38BDF8")

    // Backward-compatible color aliases
    val TEXT_WHITE = Color.WHITE
    val ACCENT_EMERALD = VOLT_LIME
    val ACCENT_EMERALD_DARK = Color.parseColor("#65A30D")
    val ACCENT_GREEN = VOLT_LIME
    val ACCENT_RED_BRIGHT = TECH_RED
    val ACCENT_RED_ALERT = Color.parseColor("#FF1744")
    val ACCENT_GOLD = TECH_AMBER
    val ACCENT_CYAN = Color.parseColor("#38BDF8")
    val TEXT_SHADOW = Color.parseColor("#B0000000")
    val CLOSE_ZONE_IDLE = Color.parseColor("#D91F242B")
    val CLOSE_ZONE_ACTIVE = Color.parseColor("#E6FF5252")

    // Overlay Transparency Palettes (Mockup spec: #0C0E11 @ 85% default)
    val HUD_BG_OPACITY = intArrayOf(
        Color.parseColor("#EB0C0E11"), // Solid dark slate (92%)
        Color.parseColor("#D90C0E11"), // Modern glass 85% (Default)
        Color.parseColor("#730C0E11"), // Sheer 45%
        Color.TRANSPARENT              // Text only (0%)
    )
    val HUD_STROKE_OPACITY = intArrayOf(
        Color.parseColor("#80D4F469"),
        Color.parseColor("#66D4F469"),
        Color.parseColor("#38D4F469"),
        Color.TRANSPARENT
    )
    val HUD_LABEL_MUTED = Color.parseColor("#94A3B8")
    val HUD_SEPARATOR = Color.parseColor("#475569")
    val HUD_DOT_SEPARATOR = Color.parseColor("#64748B")
    val HUD_CYAN = Color.parseColor("#22D3EE")
    val HUD_SLATE_300 = Color.parseColor("#CBD5E1")
    val HUD_DIVIDER_LIGHT = Color.parseColor("#1FFFFFFF")
    val HUD_REC_BG = Color.parseColor("#38FF5252")
    val HUD_REC_STROKE = Color.parseColor("#80FF5252")

    fun isDark(ctx: Context): Boolean {
        val mode = ctx.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE).getInt("theme_mode", 0)
        return when (mode) {
            1 -> true
            2 -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    // Material 3 / Stitch Design Surface Tiers
    fun bgRoot(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#111316") else Color.parseColor("#FAF8FF")
    fun bgNav(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#0C0E11") else Color.parseColor("#F2F3FF")
    fun bgCard(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1A1C1F") else Color.parseColor("#FFFFFF")
    fun bgCardElevated(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1E2023") else Color.parseColor("#FFFFFF")
    fun bgTile(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#282A2D") else Color.parseColor("#EAEDFF")
    fun bgTileActive(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#233110") else Color.parseColor("#E8F5D2")
    fun bgInput(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1E2023") else Color.parseColor("#EAEDFF")

    fun strokeSubtle(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#2A2D33") else Color.parseColor("#E2E7FF")
    fun strokeActive(ctx: Context): Int = if (isDark(ctx)) VOLT_LIME else Color.parseColor("#65A30D")
    fun accent(ctx: Context): Int = if (isDark(ctx)) VOLT_LIME else Color.parseColor("#65A30D")
    fun accentSoft(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#2E3D12") else Color.parseColor("#D4F469")

    fun textPrimary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#E2E2E6") else Color.parseColor("#131B2E")
    fun textSecondary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#C6C8B2") else Color.parseColor("#475569")
    fun textTertiary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#8F937E") else Color.parseColor("#727A66")
    fun textOnAccent(ctx: Context): Int = if (isDark(ctx)) VOLT_DARK_TEXT else Color.WHITE

    fun gaugeTrack(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#333538") else Color.parseColor("#DAE2FD")
    fun divider(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#282A2D") else Color.parseColor("#E2E7FF")

    fun statusOk(ctx: Context): Int = TECH_EMERALD
    fun statusWarning(ctx: Context): Int = TECH_AMBER
    fun statusError(ctx: Context): Int = TECH_RED

    fun dp(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(ctx: Context, dp: Float): Float = dp * ctx.resources.displayMetrics.density
    fun dpF(ctx: Context, dp: Float): Float = dp * ctx.resources.displayMetrics.density

    fun roundBoxDp(ctx: Context, fillColor: Int, radiusDp: Float, strokeWidthDp: Float = 0f, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(ctx, radiusDp)
            if (strokeWidthDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(ctx, strokeWidthDp.toInt()).coerceAtLeast(1), strokeColor)
            }
        }
    }

    fun roundBox(fillColor: Int, radiusPx: Float, strokeWidth: Int = 0, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusPx
            if (strokeWidth > 0 && strokeColor != Color.TRANSPARENT) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    fun pillBox(fillColor: Int, strokeWidth: Int = 0, strokeColor: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = 999f
            if (strokeWidth > 0 && strokeColor != Color.TRANSPARENT) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    fun ovalBox(fillColor: Int, strokeWidth: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }
}

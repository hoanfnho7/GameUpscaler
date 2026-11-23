package com.nho.upscaler

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UiTheme {
    // Modern Functional Colors
    val TEXT_WHITE = Color.WHITE
    val ACCENT_EMERALD = Color.parseColor("#00E676")
    val ACCENT_EMERALD_DARK = Color.parseColor("#059669")
    val ACCENT_BLUE = Color.parseColor("#3B82F6")
    val ACCENT_AMBER = Color.parseColor("#F59E0B")
    val ACCENT_RED = Color.parseColor("#EF4444")
    val ACCENT_PURPLE = Color.parseColor("#8B5CF6")
    val TEXT_SHADOW = Color.parseColor("#B0000000")
    val CLOSE_ZONE_IDLE = Color.parseColor("#D91F242B")
    val CLOSE_ZONE_ACTIVE = Color.parseColor("#E6EF4444")

    // Backward-compatible color aliases
    val ACCENT_GREEN = ACCENT_EMERALD
    val ACCENT_RED_BRIGHT = ACCENT_RED
    val ACCENT_RED_ALERT = Color.parseColor("#FF1744")
    val ACCENT_GOLD = ACCENT_AMBER
    val ACCENT_CYAN = Color.parseColor("#29B6F6")

    // Overlay Transparency Palettes
    val HUD_BG_OPACITY = intArrayOf(
        Color.parseColor("#E612151B"), // Solid dark slate
        Color.parseColor("#B312151B"), // Modern glass
        Color.parseColor("#5912151B"), // Sheer ultra-translucent
        Color.TRANSPARENT              // Text only
    )
    val HUD_STROKE_OPACITY = intArrayOf(
        Color.parseColor("#663B82F6"),
        Color.parseColor("#403B82F6"),
        Color.parseColor("#263B82F6"),
        Color.TRANSPARENT
    )

    fun isDark(ctx: Context): Boolean {
        val mode = ctx.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE).getInt("theme_mode", 0)
        return when (mode) {
            1 -> true
            2 -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    // Modern Material 3 & Minimalist Palette
    fun bgRoot(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#0D0F12") else Color.parseColor("#F8FAFC")
    fun bgCard(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#15181E") else Color.parseColor("#FFFFFF")
    fun bgCardElevated(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1B1F27") else Color.parseColor("#FFFFFF")
    fun bgTile(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1E232B") else Color.parseColor("#F1F5F9")
    fun bgTileActive(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#122A1E") else Color.parseColor("#ECFDF5")
    fun bgInput(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1A1E25") else Color.parseColor("#EDF2F7")

    fun strokeSubtle(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#262C36") else Color.parseColor("#E2E8F0")
    fun strokeActive(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#00E676") else Color.parseColor("#059669")
    fun accent(ctx: Context): Int = if (isDark(ctx)) ACCENT_EMERALD else ACCENT_EMERALD_DARK
    fun accentSoft(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#163324") else Color.parseColor("#D1FAE5")

    fun textPrimary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#F3F4F6") else Color.parseColor("#0F172A")
    fun textSecondary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
    fun textTertiary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#64748B") else Color.parseColor("#94A3B8")
    fun textOnAccent(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#09100C") else Color.parseColor("#FFFFFF")

    fun gaugeTrack(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#262D38") else Color.parseColor("#E2E8F0")
    fun divider(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#20252F") else Color.parseColor("#E2E8F0")

    fun statusOk(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#10B981") else Color.parseColor("#059669")
    fun statusWarning(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#F59E0B") else Color.parseColor("#D97706")
    fun statusError(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#EF4444") else Color.parseColor("#DC2626")

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

    fun ovalBox(fillColor: Int, strokeWidth: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }
    }
}

package com.nho.upscaler

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UiTheme {
    // Fixed Accent & Alert Colors
    val ACCENT_GREEN = Color.parseColor("#44D62C")
    val ACCENT_RED_BRIGHT = Color.parseColor("#FF453A")
    val ACCENT_RED_ALERT = Color.parseColor("#FF1744")
    val ACCENT_GOLD = Color.parseColor("#FFB300")
    val ACCENT_CYAN = Color.parseColor("#29B6F6")
    val TEXT_SHADOW = Color.parseColor("#F0000000")
    val CLOSE_ZONE_IDLE = Color.parseColor("#CC261212")

    val HUD_BG_OPACITY = intArrayOf(
        Color.parseColor("#DD0B0D0C"),
        Color.parseColor("#990B0D0C"),
        Color.parseColor("#380B0D0C"),
        Color.TRANSPARENT
    )
    val HUD_STROKE_OPACITY = intArrayOf(
        Color.parseColor("#6644D62C"),
        Color.parseColor("#4444D62C"),
        Color.parseColor("#2444D62C"),
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

    fun bgRoot(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#0B0D0C") else Color.parseColor("#F2F5F3")
    fun bgCard(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#131715") else Color.parseColor("#FFFFFF")
    fun bgTile(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#181D1A") else Color.parseColor("#F7FAF8")
    fun bgTileActive(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#142914") else Color.parseColor("#E6F7E4")
    fun bgInput(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#161B18") else Color.parseColor("#E8ECE9")

    fun strokeSubtle(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#242C27") else Color.parseColor("#D5DFD9")
    fun accent(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#44D62C") else Color.parseColor("#22A314")
    fun accentSoft(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1A331A") else Color.parseColor("#DDF4DA")

    fun textPrimary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#F2F5F3") else Color.parseColor("#111714")
    fun textSecondary(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#8C9690") else Color.parseColor("#5C6962")
    fun textOnAccent(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#090C0A") else Color.parseColor("#FFFFFF")
    fun gaugeTrack(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#2A322D") else Color.parseColor("#D8E2DC")
    fun divider(ctx: Context): Int = if (isDark(ctx)) Color.parseColor("#1E2521") else Color.parseColor("#E3EAE6")

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
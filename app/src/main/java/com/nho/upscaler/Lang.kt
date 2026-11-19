package com.nho.upscaler

import android.content.Context
import java.util.Locale

object Lang {
    fun getMode(ctx: Context): Int {
        return ctx.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE).getInt("app_lang", 0)
    }

    fun isVi(ctx: Context): Boolean {
        return when (getMode(ctx)) {
            1 -> true
            2 -> false
            else -> Locale.getDefault().language.equals("vi", ignoreCase = true)
        }
    }

    fun modeBadge(ctx: Context): String {
        return when (getMode(ctx)) {
            1 -> "🇻🇳 VI"
            2 -> "🇺🇸 EN"
            else -> "🌐 Auto"
        }
    }

    fun tileLabel(ctx: Context): String = if (isVi(ctx)) "HUD Đo FPS" else "FPS HUD"
}
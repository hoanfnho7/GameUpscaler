package com.nho.upscaler

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object Lang {
    fun getMode(ctx: Context): Int {
        return ctx.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE).getInt("app_lang", 0)
    }

    fun context(base: Context): Context {
        val mode = getMode(base)
        if (mode == 0) return base
        return try {
            val locale = if (mode == 1) Locale("vi") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            base.createConfigurationContext(config)
        } catch (_: Exception) {
            base
        }
    }

    fun str(base: Context, resId: Int, vararg args: Any): String {
        val resCtx = context(base)
        return if (args.isEmpty()) resCtx.getString(resId) else resCtx.getString(resId, *args)
    }

    fun modeBadge(base: Context): String {
        return when (getMode(base)) {
            1 -> str(base, R.string.lang_badge_vi)
            2 -> str(base, R.string.lang_badge_en)
            else -> str(base, R.string.lang_badge_auto)
        }
    }

    fun tileLabel(base: Context): String = str(base, R.string.tile_label)
}
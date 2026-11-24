package com.nho.upscaler

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)

    companion object {
        private val DEFAULT_PINS = setOf(
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.vng.pubgmobile",
            "com.tencent.ig"
        )
    }

    var savedFactor: Int
        get() = prefs.getInt("saved_factor", 125)
        set(value) = prefs.edit().putInt("saved_factor", value).apply()

    var targetScaleFactor: Int
        get() = savedFactor
        set(value) { savedFactor = value }

    var showPinnedOnly: Boolean
        get() = prefs.getBoolean("show_pinned_only", false)
        set(value) = prefs.edit().putBoolean("show_pinned_only", value).apply()

    var enableFpsOverlay: Boolean
        get() = prefs.getBoolean("enable_fps_overlay", true)
        set(value) = prefs.edit().putBoolean("enable_fps_overlay", value).apply()

    var overlaySizeLevel: Int
        get() = prefs.getInt("overlay_size_level", 1)
        set(value) = prefs.edit().putInt("overlay_size_level", value).apply()

    var overlayBgMode: Int
        get() = prefs.getInt("overlay_bg_mode", 1)
        set(value) = prefs.edit().putInt("overlay_bg_mode", value).apply()

    var hudPollMode: Int
        get() = prefs.getInt("hud_poll_mode", 0)
        set(value) = prefs.edit().putInt("hud_poll_mode", value).apply()

    var hudLayoutMode: Int
        get() = prefs.getInt("hud_layout_mode", 0)
        set(value) = prefs.edit().putInt("hud_layout_mode", value).apply()

    var hudColorAccent: Int
        get() = prefs.getInt("hud_color_accent", 0)
        set(value) = prefs.edit().putInt("hud_color_accent", value).apply()

    var overlayVertical: Boolean
        get() = prefs.getBoolean("overlay_vertical", false)
        set(value) = prefs.edit().putBoolean("overlay_vertical", value).apply()

    var hudHideLabels: Boolean
        get() = prefs.getBoolean("hud_hide_labels", false)
        set(value) = prefs.edit().putBoolean("hud_hide_labels", value).apply()

    var appLang: Int
        get() = prefs.getInt("app_lang", 0)
        set(value) = prefs.edit().putInt("app_lang", value).apply()

    var appLanguage: Int
        get() = appLang
        set(value) { appLang = value }

    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    fun isGameEnabled(pkg: String): Boolean = prefs.getBoolean("enabled_$pkg", true)

    fun setGameEnabled(pkg: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$pkg", enabled).apply()
    }

    fun getPinnedPackages(): MutableSet<String> {
        val saved = prefs.getStringSet("pinned_pkgs", null)
        return if (saved == null) {
            val initSet = HashSet(DEFAULT_PINS)
            prefs.edit().putStringSet("pinned_pkgs", initSet).apply()
            initSet
        } else {
            HashSet(saved)
        }
    }

    fun savePinnedPackages(pkgs: Set<String>) {
        prefs.edit().putStringSet("pinned_pkgs", HashSet(pkgs)).apply()
    }

    fun getGameScale(pkg: String): Int = prefs.getInt("scale_$pkg", 0)

    fun setGameScale(pkg: String, scale: Int) {
        prefs.edit().putInt("scale_$pkg", scale).apply()
    }

    fun getAppCustomScale(pkg: String): Int = getGameScale(pkg)
    fun setAppCustomScale(pkg: String, scale: Int) = setGameScale(pkg, scale)

    fun getCustomGames(): Set<String> {
        return prefs.getStringSet("custom_games", emptySet()) ?: emptySet()
    }

    fun addCustomGame(pkg: String) {
        val current = HashSet(getCustomGames())
        current.add(pkg)
        prefs.edit().putStringSet("custom_games", current).apply()
    }

    fun removeCustomGame(pkg: String) {
        val current = HashSet(getCustomGames())
        current.remove(pkg)
        prefs.edit().putStringSet("custom_games", current).apply()
    }

    fun getMetric(key: String, defVal: Boolean): Boolean = prefs.getBoolean(key, defVal)

    fun setMetric(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun getHudPosition(isLandscape: Boolean): Pair<Int, Int> {
        return if (isLandscape) {
            prefs.getInt("overlay_land_x", 24) to prefs.getInt("overlay_land_y", 6)
        } else {
            prefs.getInt("overlay_port_x", 48) to prefs.getInt("overlay_port_y", 110)
        }
    }

    fun saveHudPosition(isLandscape: Boolean, x: Int, y: Int) {
        if (isLandscape) {
            prefs.edit().putInt("overlay_land_x", x).putInt("overlay_land_y", y).apply()
        } else {
            prefs.edit().putInt("overlay_port_x", x).putInt("overlay_port_y", y).apply()
        }
    }

    fun resetHudPosition() {
        prefs.edit()
            .remove("overlay_land_x")
            .remove("overlay_land_y")
            .remove("overlay_port_x")
            .remove("overlay_port_y")
            .apply()
    }
}
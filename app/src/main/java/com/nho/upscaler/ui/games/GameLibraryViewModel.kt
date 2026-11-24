package com.nho.upscaler.ui.games

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nho.upscaler.FpsOverlayService
import com.nho.upscaler.PrefsManager
import com.nho.upscaler.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class GameProfileItem(
    val name: String,
    val pkg: String,
    val icon: Drawable?,
    val customScale: Float = 0f,
    val isPinned: Boolean = false,
    val isEnabled: Boolean = true,
    val detectedEngine: String = "",
    val isDetectedGame: Boolean = false,
    val fpsCap: Int = 0,
    val refreshRate: Int = 0
)

data class GameLibraryUiState(
    val autoLaunchHud: Boolean = true,
    val globalScale: Float = 1.15f,
    val searchQuery: String = "",
    val games: List<GameProfileItem> = emptyList(),
    val filteredGames: List<GameProfileItem> = emptyList(),
    val allInstalledApps: List<GameProfileItem> = emptyList(),
    val detectedGameCount: Int = 0,
    val isAppPickerOpen: Boolean = false,
    val selectedGameForScale: GameProfileItem? = null
)

class GameLibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameLibraryUiState())
    val uiState: StateFlow<GameLibraryUiState> = _uiState.asStateFlow()

    private var prefsMgr: PrefsManager? = null
    private val engineCache = HashMap<String, String>()

    fun init(context: Context) {
        if (prefsMgr == null) {
            prefsMgr = PrefsManager(context.applicationContext)
            val currentScale = (prefsMgr?.targetScaleFactor ?: 115) / 100f
            val autoHud = prefsMgr?.enableFpsOverlay ?: true
            _uiState.update {
                it.copy(
                    globalScale = currentScale,
                    autoLaunchHud = autoHud
                )
            }
        }
        loadInstalledGames(context)
    }

    fun loadInstalledGames(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            val customGames = prefsMgr?.getCustomGames() ?: emptySet()
            val pinnedGames = getPinnedGames()

            val gameItems = mutableListOf<GameProfileItem>()
            val allApps = mutableListOf<GameProfileItem>()
            val seenPkgs = HashSet<String>()

            for (ri in resolved) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || !seenPkgs.add(pkg)) continue

                val label = ri.loadLabel(pm).toString()
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                val isCustom = customGames.contains(pkg)
                val isPinned = pinnedGames.contains(pkg)
                val isEnabled = prefsMgr?.isGameEnabled(pkg) ?: true
                val customScale = (prefsMgr?.getAppCustomScale(pkg) ?: 0) / 100f
                val fpsCap = prefsMgr?.getGameFpsCap(pkg) ?: 0
                val refreshRate = prefsMgr?.getGameRefreshRate(pkg) ?: 0

                val appInfo = ri.activityInfo.applicationInfo
                val detectedEngine = engineCache.getOrPut(pkg) { detectGameEngine(ri, appInfo) }
                val isDetectedGame = detectedEngine.isNotEmpty()

                val item = GameProfileItem(
                    name = label,
                    pkg = pkg,
                    icon = icon,
                    customScale = customScale,
                    isPinned = isPinned,
                    isEnabled = isEnabled,
                    detectedEngine = detectedEngine,
                    isDetectedGame = isDetectedGame,
                    fpsCap = fpsCap,
                    refreshRate = refreshRate
                )
                allApps.add(item)

                // Only show games manually added by user via "+ Thêm Game"
                if (isCustom) {
                    gameItems.add(item)
                }
            }

            // Sort: Pinned first, then alphabetically
            gameItems.sortWith(compareByDescending<GameProfileItem> { it.isPinned }.thenBy { it.name.lowercase(Locale.ROOT) })
            // Sort picker: Detected games first (by engine priority), then alphabetically
            allApps.sortWith(
                compareByDescending<GameProfileItem> { it.isDetectedGame }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            val detectedCount = allApps.count { it.isDetectedGame }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        games = gameItems,
                        filteredGames = filterGames(gameItems, it.searchQuery),
                        allInstalledApps = allApps,
                        detectedGameCount = detectedCount
                    )
                }
            }
        }
    }

    private fun detectGameEngine(ri: ResolveInfo, appInfo: ApplicationInfo): String {
        val pkg = ri.activityInfo.packageName
        if (isExcludedApp(pkg)) return ""
        if (isEmulatorPackage(pkg)) return "Emulator"

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        // 1. Scan native libraries (.so) for Unity / Unreal / Cocos / Godot (inspired by Scene)
        if (!isSystem) {
            val nativeEngine = scanNativeEngine(appInfo)
            if (nativeEngine.isNotEmpty()) return nativeEngine
        }

        // 2. Android Game Category / Legacy Flag / Known Publisher
        val isGameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appInfo.category == ApplicationInfo.CATEGORY_GAME
        } else false
        @Suppress("DEPRECATION")
        val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        if (isGameCategory || isGameFlag || isKnownGamePackage(pkg)) {
            return "Game"
        }

        // 3. Landscape-locked launch activity for non-system apps (Scene heuristic)
        if (!isSystem) {
            val orient = ri.activityInfo.screenOrientation
            val isLandscapeLocked = orient == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    orient == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
                    orient == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
                    orient == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            if (isLandscapeLocked) return "Game"
        }

        return ""
    }

    private fun scanNativeEngine(appInfo: ApplicationInfo): String {
        // Fast path: check extracted nativeLibraryDir first
        try {
            val libDir = appInfo.nativeLibraryDir?.let { File(it) }
            if (libDir != null && libDir.exists() && libDir.isDirectory) {
                val files = libDir.list()
                if (files != null && files.isNotEmpty()) {
                    val matched = matchEngineFromNames(files.asIterable())
                    if (matched.isNotEmpty()) return matched
                }
            }
        } catch (_: Exception) {}

        // Fallback for extractNativeLibs=false / App Bundles: read ZIP central directory headers (no decompression)
        val apkPaths = mutableListOf<String>()
        appInfo.sourceDir?.let { apkPaths.add(it) }
        appInfo.splitSourceDirs?.let { splits ->
            for (s in splits) {
                if (s.contains("arm", ignoreCase = true) || s.contains("abi", ignoreCase = true) || s.contains("lib", ignoreCase = true)) {
                    apkPaths.add(s)
                }
            }
        }

        for (path in apkPaths) {
            try {
                ZipFile(path).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.startsWith("lib/") && name.endsWith(".so")) {
                            val soName = name.substringAfterLast('/').lowercase(Locale.ROOT)
                            val engine = classifySoLibrary(soName)
                            if (engine.isNotEmpty()) return engine
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    private fun matchEngineFromNames(names: Iterable<String>): String {
        for (raw in names) {
            val engine = classifySoLibrary(raw.lowercase(Locale.ROOT))
            if (engine.isNotEmpty()) return engine
        }
        return ""
    }

    private fun classifySoLibrary(soName: String): String {
        return when {
            soName == "libunity.so" || soName == "libil2cpp.so" -> "Unity"
            soName == "libue4.so" || soName == "libunreal.so" || soName.startsWith("libue4") -> "Unreal"
            soName.startsWith("libcocos2d") || soName == "libcocos.so" -> "Cocos"
            soName.contains("libgodot") -> "Godot"
            soName.contains("libtmgp") || soName.contains("libcri_ware") || soName.contains("libfmod") -> "Game"
            else -> ""
        }
    }

    private val EXCLUDED_PACKAGES = setOf(
        "com.google.android.play.games",
        "com.valvesoftware.android.steam.community",
        "com.discord",
        "com.microsoft.xboxone.smartglass",
        "com.playstation.mobile",
        "com.nvidia.geforcenow",
        "com.vng.zalo",
        "com.viber.voip",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.instagram.android",
        "com.tiktok.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.trill",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.google.android.youtube",
        "tv.danmaku.bili",
        "com.bilibili.app.in",
        "com.netflix.mediaclient",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "org.videolan.vlc",
        "com.primatelabs.geekbench5",
        "com.primatelabs.geekbench6",
        "com.antutu.abenchmark",
        "com.antutu.benchmark.full",
        "com.futuremark.dmandroid.application",
        "com.glbenchmark.glbenchmark27"
    )

    private fun isExcludedApp(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        if (EXCLUDED_PACKAGES.contains(p)) return true
        if (p.startsWith("com.google.") || p.startsWith("com.android.") || p.startsWith("com.sec.android.")) return true
        if (p.contains("launcher") || p.contains("camera") || p.contains("scanner") || p.contains("browser") || p.contains("videoplayer")) return true
        if (p.contains("bank") || p.contains("mbmobile") || p.contains("smartotp") || p.contains("wallet") || p.contains("pay") || p.contains("fintech") || p.contains("zalo")) return true
        if (p.contains("benchmark") || p.contains("geekbench") || p.contains("antutu") || p.contains("3dmark")) return true
        return false
    }

    private fun isEmulatorPackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        return p.contains("aethersx2") || p.contains("nethersx2") ||
               p.contains("ppsspp") || p.contains("skyline.emu") ||
               p.contains("yuzu") || p.contains("sudachi") ||
               p.contains("dolphinemu") || p.contains("citra") ||
               p.contains("winlator") || p.contains("gamesirnsemulator") ||
               p.contains("retroarch") || p.contains("duckstation") ||
               p.contains("drastic") || p.contains("cemu")
    }

    private fun isKnownGamePackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        return p.contains("garena.game") || p.contains("dts.freefire") ||
               p.contains("pubgmobile") || p.contains("tencent.ig") ||
               p.contains("tencent.tmgp") || p.contains("mihoyo") ||
               p.contains("hoyoverse") || p.contains("riotgames") ||
               p.contains("kurogame") || p.contains("epicgames") ||
               p.contains("gameloft") || p.contains("supercell") ||
               p.contains("ea.game") || p.contains("krafton") ||
               p.contains("nintendo") || p.contains("mobile.legends") ||
               p.contains("activision") || p.contains("rockstargames")
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredGames = filterGames(it.games, query)
            )
        }
    }

    private fun filterGames(list: List<GameProfileItem>, query: String): List<GameProfileItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return list
        return list.filter {
            it.name.lowercase(Locale.ROOT).contains(q) || it.pkg.lowercase(Locale.ROOT).contains(q)
        }
    }

    fun setGlobalScale(scale: Float, context: Context) {
        val scaleInt = (scale * 100).toInt()
        prefsMgr?.targetScaleFactor = scaleInt
        _uiState.update { it.copy(globalScale = scale) }
    }

    fun toggleAutoLaunchHud(enabled: Boolean) {
        prefsMgr?.enableFpsOverlay = enabled
        _uiState.update { it.copy(autoLaunchHud = enabled) }
    }

    fun togglePin(pkg: String, context: Context) {
        prefsMgr?.let { mgr ->
            val current = mgr.getPinnedPackages()
            if (current.contains(pkg)) current.remove(pkg) else current.add(pkg)
            mgr.savePinnedPackages(current)
        }
        loadInstalledGames(context)
    }

    private fun getPinnedGames(): Set<String> {
        return prefsMgr?.getPinnedPackages() ?: emptySet()
    }

    fun toggleGameEnabled(pkg: String, enabled: Boolean) {
        prefsMgr?.setGameEnabled(pkg, enabled)
        _uiState.update { state ->
            val updated = state.games.map { if (it.pkg == pkg) it.copy(isEnabled = enabled) else it }
            state.copy(games = updated, filteredGames = filterGames(updated, state.searchQuery))
        }
    }

    fun openGameScaleDialog(game: GameProfileItem) {
        _uiState.update { it.copy(selectedGameForScale = game) }
    }

    fun closeGameScaleDialog() {
        _uiState.update { it.copy(selectedGameForScale = null) }
    }

    fun setGameProfileConfig(pkg: String, scale: Float, fpsCap: Int, refreshRate: Int, context: Context) {
        val scaleInt = (scale * 100).toInt()
        prefsMgr?.setAppCustomScale(pkg, scaleInt)
        prefsMgr?.setGameFpsCap(pkg, fpsCap)
        prefsMgr?.setGameRefreshRate(pkg, refreshRate)
        closeGameScaleDialog()
        loadInstalledGames(context)
        val parts = mutableListOf<String>()
        if (scaleInt == 100) parts.add("Native")
        else if (scaleInt > 0) parts.add(String.format(Locale.US, "%.2fx", scale))
        if (fpsCap > 0) parts.add("${fpsCap} FPS")
        if (refreshRate > 0) parts.add("${refreshRate}Hz")
        val msg = if (parts.isEmpty()) "Đã đặt cấu hình game về mặc định" else "Đã lưu: ${parts.joinToString(" • ")}"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun openAppPicker() {
        _uiState.update { it.copy(isAppPickerOpen = true) }
    }

    fun closeAppPicker() {
        _uiState.update { it.copy(isAppPickerOpen = false) }
    }

    fun addCustomGame(pkg: String, context: Context) {
        prefsMgr?.addCustomGame(pkg)
        loadInstalledGames(context)
        Toast.makeText(context, "Đã thêm game vào danh sách", Toast.LENGTH_SHORT).show()
    }

    fun addAllDetectedGames(context: Context) {
        val detected = _uiState.value.allInstalledApps.filter { it.isDetectedGame }
        if (detected.isEmpty()) return
        val existing = prefsMgr?.getCustomGames() ?: emptySet()
        var added = 0
        for (app in detected) {
            if (!existing.contains(app.pkg)) {
                prefsMgr?.addCustomGame(app.pkg)
                added++
            }
        }
        loadInstalledGames(context)
        val msg = if (added > 0) "Đã tự động thêm $added game vào danh sách" else "Tất cả game đã có trong danh sách"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun removeCustomGame(pkg: String, context: Context) {
        prefsMgr?.removeCustomGame(pkg)
        loadInstalledGames(context)
        Toast.makeText(context, "Đã xóa khỏi danh sách", Toast.LENGTH_SHORT).show()
    }

    fun launchGame(context: Context, game: GameProfileItem) {
        val custom = prefsMgr?.getAppCustomScale(game.pkg) ?: 0
        val effectiveFactor = if (custom > 0) custom else (prefsMgr?.targetScaleFactor ?: 115)
        val fpsCap = prefsMgr?.getGameFpsCap(game.pkg) ?: 0
        val refreshRate = prefsMgr?.getGameRefreshRate(game.pkg) ?: 0
        val scaleLabel = if (effectiveFactor == 100) "Native" else String.format(Locale.US, "%.2fx", effectiveFactor / 100f)
        ShizukuHelper.launchGameWithUpscale(game.pkg, effectiveFactor, fpsCap, refreshRate)

        // Auto launch HUD if enabled
        if (prefsMgr?.enableFpsOverlay == true && Settings.canDrawOverlays(context)) {
            val intent = Intent(context, FpsOverlayService::class.java).apply {
                putExtra("target_pkg", game.pkg)
                putExtra("game_name", game.name)
                putExtra("scale_label", scaleLabel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

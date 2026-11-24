package com.nho.upscaler.ui.games

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import java.util.Locale

data class GameProfileItem(
    val name: String,
    val pkg: String,
    val icon: Drawable?,
    val customScale: Float = 0f,
    val isPinned: Boolean = false,
    val isEnabled: Boolean = true
)

data class GameLibraryUiState(
    val autoLaunchHud: Boolean = true,
    val globalScale: Float = 1.15f,
    val searchQuery: String = "",
    val games: List<GameProfileItem> = emptyList(),
    val filteredGames: List<GameProfileItem> = emptyList(),
    val allInstalledApps: List<GameProfileItem> = emptyList(),
    val isAppPickerOpen: Boolean = false,
    val selectedGameForScale: GameProfileItem? = null
)

class GameLibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameLibraryUiState())
    val uiState: StateFlow<GameLibraryUiState> = _uiState.asStateFlow()

    private var prefsMgr: PrefsManager? = null

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

            for (ri in resolved) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue

                val label = ri.loadLabel(pm).toString()
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                val isCustom = customGames.contains(pkg)
                val isPinned = pinnedGames.contains(pkg)
                val isEnabled = prefsMgr?.isGameEnabled(pkg) ?: true
                val customScale = (prefsMgr?.getAppCustomScale(pkg) ?: 0) / 100f

                val appInfo = ri.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isGameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                } else false
                val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                val isGame = (isGameCategory || isGameFlag || isKnownGamePackage(pkg)) && !isExcludedApp(pkg)

                val item = GameProfileItem(label, pkg, icon, customScale, isPinned, isEnabled)
                allApps.add(item)

                // Only show games manually added by user via "+ Thêm Game"
                if (isCustom) {
                    gameItems.add(item)
                }
            }

            // Sort: Pinned first, then alphabetically
            gameItems.sortWith(compareByDescending<GameProfileItem> { it.isPinned }.thenBy { it.name.lowercase(Locale.ROOT) })
            allApps.sortBy { it.name.lowercase(Locale.ROOT) }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        games = gameItems,
                        filteredGames = filterGames(gameItems, it.searchQuery),
                        allInstalledApps = allApps
                    )
                }
            }
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
        "com.instagram.android",
        "com.tiktok.android",
        "com.zhiliaoapp.musically",
        "com.whatsapp"
    )

    private fun isExcludedApp(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        if (EXCLUDED_PACKAGES.contains(p)) return true
        if (p.startsWith("com.google.") || p.startsWith("com.android.") || p.startsWith("com.sec.android.")) return true
        if (p.contains("bank") || p.contains("mbmobile") || p.contains("smartotp") || p.contains("wallet") || p.contains("pay") || p.contains("fintech") || p.contains("zalo")) return true
        return false
    }

    private fun isKnownGamePackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        return p.contains("garena.game") || p.contains("dts.freefire") ||
               p.contains("pubgmobile") || p.contains("tencent.ig") ||
               p.contains("mihoyo") || p.contains("hoyoverse") ||
               p.contains("riotgames") || p.contains("kurogame") ||
               p.contains("epicgames") || p.contains("gameloft") ||
               p.contains("supercell") || p.contains("ea.game") ||
               p.contains("krafton") || p.contains("nintendo")
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

    fun setGameCustomScale(pkg: String, scale: Float, context: Context) {
        val scaleInt = (scale * 100).toInt()
        prefsMgr?.setAppCustomScale(pkg, scaleInt)
        closeGameScaleDialog()
        loadInstalledGames(context)
        Toast.makeText(context, if (scale > 0f) "Đã khóa ${scale}x cho game này" else "Đã đặt về mặc định chung", Toast.LENGTH_SHORT).show()
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

    fun removeCustomGame(pkg: String, context: Context) {
        prefsMgr?.removeCustomGame(pkg)
        loadInstalledGames(context)
        Toast.makeText(context, "Đã xóa khỏi danh sách", Toast.LENGTH_SHORT).show()
    }

    fun launchGame(context: Context, game: GameProfileItem) {
        val custom = prefsMgr?.getAppCustomScale(game.pkg) ?: 0
        val effectiveFactor = if (custom > 0) custom else (prefsMgr?.targetScaleFactor ?: 115)
        val scaleLabel = String.format(Locale.US, "%.2fx", effectiveFactor / 100f)
        ShizukuHelper.launchGameWithUpscale(game.pkg, effectiveFactor)

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

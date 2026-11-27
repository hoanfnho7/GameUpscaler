package com.nho.upscaler.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nho.upscaler.AppReleaseInfo
import com.nho.upscaler.AppUpdater
import com.nho.upscaler.PrefsManager
import com.nho.upscaler.UpdateDownloadState
import com.nho.upscaler.ui.analytics.AnalyticsViewModel
import com.nho.upscaler.ui.analytics.PerformanceAnalyticsScreen
import com.nho.upscaler.ui.games.GameLibraryScreen
import com.nho.upscaler.ui.games.GameLibraryViewModel
import com.nho.upscaler.ui.home.HomeScreen
import com.nho.upscaler.ui.home.HomeViewModel
import com.nho.upscaler.ui.hud.HudSettingsScreen
import com.nho.upscaler.ui.hud.HudSettingsViewModel
import com.nho.upscaler.ui.theme.GameUpscalerTheme
import com.nho.upscaler.ui.theme.TechEmerald
import com.nho.upscaler.ui.theme.TechRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenDestination {
    HOME,
    GAMES,
    HUD,
    ANALYTICS
}

@Composable
fun GameUpscalerApp(
    homeViewModel: HomeViewModel = viewModel(),
    gameViewModel: GameLibraryViewModel = viewModel(),
    hudViewModel: HudSettingsViewModel = viewModel(),
    analyticsViewModel: AnalyticsViewModel = viewModel()
) {
    var currentDestination by remember { mutableStateOf(ScreenDestination.HOME) }
    val homeUiState by homeViewModel.uiState.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsMgr = remember { PrefsManager(context.applicationContext) }
    val currentVersion = remember { AppUpdater.getCurrentVersionName(context) }

    var availableRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }

    // Silent background check for GitHub Release updates on app launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AppUpdater.cleanupOldApks(context)
        }
        val result = AppUpdater.checkForUpdate(context)
        result.getOrNull()?.let { release ->
            availableRelease = release
            if (prefsMgr.skippedUpdateTag != release.tagName) {
                showUpdateDialog = true
            }
        }
    }

    fun triggerManualUpdateCheck() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = AppUpdater.checkForUpdate(context)
            isCheckingUpdate = false
            result.onSuccess { release ->
                if (release != null) {
                    availableRelease = release
                    downloadState = UpdateDownloadState.Idle
                    showAboutDialog = false
                    showUpdateDialog = true
                } else {
                    val msg = if (homeUiState.currentLang == "VI") {
                        "Bạn đang dùng phiên bản mới nhất (v$currentVersion)!"
                    } else {
                        "You're on the latest version (v$currentVersion)!"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                val msg = if (homeUiState.currentLang == "VI") {
                    "Không thể kiểm tra cập nhật. Vui lòng kiểm tra kết nối mạng!"
                } else {
                    "Failed to check for updates. Please check your connection!"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startInAppDownload(release: AppReleaseInfo) {
        if (downloadState is UpdateDownloadState.Downloading) return
        downloadState = UpdateDownloadState.Downloading(0f, 0L, release.apkSizeBytes)
        scope.launch {
            val res = AppUpdater.downloadApk(context, release) { progress, downloaded, total ->
                downloadState = UpdateDownloadState.Downloading(progress, downloaded, total)
            }
            res.onSuccess { apkFile ->
                downloadState = UpdateDownloadState.ReadyToInstall(apkFile)
                val launched = try {
                    AppUpdater.installApk(context, apkFile)
                } catch (e: Exception) {
                    downloadState = UpdateDownloadState.Error(e.message ?: "Install error")
                    false
                }
                if (!launched && downloadState is UpdateDownloadState.ReadyToInstall) {
                    val msg = if (homeUiState.currentLang == "VI") {
                        "Hãy bật quyền 'Cài đặt ứng dụng không xác định' rồi quay lại bấm Cài đặt!"
                    } else {
                        "Enable 'Install unknown apps' permission, then return and tap Install!"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                downloadState = UpdateDownloadState.Error(err.message ?: "Download failed")
            }
        }
    }

    // Handle Back Press
    BackHandler(enabled = currentDestination != ScreenDestination.HOME) {
        currentDestination = ScreenDestination.HOME
    }

    GameUpscalerTheme(darkTheme = homeUiState.isDarkTheme) {
        if (showAboutDialog) {
            AboutDialog(
                lang = homeUiState.currentLang,
                currentVersion = currentVersion,
                isCheckingUpdate = isCheckingUpdate,
                hasUpdateAvailable = availableRelease != null,
                onCheckUpdate = {
                    if (availableRelease != null) {
                        showAboutDialog = false
                        showUpdateDialog = true
                    } else {
                        triggerManualUpdateCheck()
                    }
                },
                onDismiss = { showAboutDialog = false }
            )
        }

        val activeRelease = availableRelease
        if (showUpdateDialog && activeRelease != null) {
            UpdateAvailableDialog(
                release = activeRelease,
                currentVersion = currentVersion,
                lang = homeUiState.currentLang,
                downloadState = downloadState,
                onStartDownload = { startInAppDownload(activeRelease) },
                onInstallFile = { file ->
                    try {
                        val launched = AppUpdater.installApk(context, file)
                        if (!launched) {
                            val msg = if (homeUiState.currentLang == "VI") {
                                "Hãy bật quyền 'Cài đặt ứng dụng không xác định' rồi quay lại bấm Cài đặt!"
                            } else {
                                "Enable 'Install unknown apps' permission, then return and tap Install!"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Install error", Toast.LENGTH_LONG).show()
                    }
                },
                onSkipVersion = {
                    prefsMgr.skippedUpdateTag = activeRelease.tagName
                    showUpdateDialog = false
                },
                onDismiss = {
                    if (downloadState !is UpdateDownloadState.Downloading) {
                        showUpdateDialog = false
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // Persistent Top App Bar across all tabs
            TopAppBarHeader(
                isDark = homeUiState.isDarkTheme,
                lang = homeUiState.currentLang,
                hasUpdateAvailable = availableRelease != null,
                onOpenUpdate = { showUpdateDialog = true },
                onToggleTheme = { homeViewModel.toggleTheme() },
                onToggleLang = { homeViewModel.toggleLanguage() },
                onOpenAbout = { showAboutDialog = true }
            )

            // Main Screen View with Crossfade Animation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(
                    targetState = currentDestination,
                    label = "ScreenTransition"
                ) { destination ->
                    when (destination) {
                        ScreenDestination.HOME -> {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToGames = { currentDestination = ScreenDestination.GAMES },
                                onNavigateToAnalytics = { currentDestination = ScreenDestination.ANALYTICS }
                            )
                        }
                        ScreenDestination.GAMES -> {
                            GameLibraryScreen(
                                viewModel = gameViewModel,
                                lang = homeUiState.currentLang
                            )
                        }
                        ScreenDestination.HUD -> {
                            HudSettingsScreen(
                                viewModel = hudViewModel,
                                lang = homeUiState.currentLang
                            )
                        }
                        ScreenDestination.ANALYTICS -> {
                            PerformanceAnalyticsScreen(
                                viewModel = analyticsViewModel,
                                lang = homeUiState.currentLang,
                                onNavigateBack = { currentDestination = ScreenDestination.HOME }
                            )
                        }
                    }
                }

                // Bottom Navigation Bar (Hidden on Analytics screen)
                if (currentDestination != ScreenDestination.ANALYTICS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        BottomNavigationBar(
                            currentDestination = currentDestination,
                            lang = homeUiState.currentLang,
                            onSelectDestination = { currentDestination = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAppBarHeader(
    isDark: Boolean,
    lang: String,
    hasUpdateAvailable: Boolean,
    onOpenUpdate: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleLang: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Logo & Pro badge (Clickable to open About dialog)
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenAbout() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "GameUpscaler",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF171E00) else Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Right: Optional update badge + Theme and Language quick toggle buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasUpdateAvailable) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onOpenUpdate() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update available",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Theme button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable { onToggleTheme() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDark) {
                            if (lang == "VI") "Tối" else "Dark"
                        } else {
                            if (lang == "VI") "Sáng" else "Light"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Language button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .clickable { onToggleLang() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = lang,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(
    lang: String,
    currentVersion: String,
    isCheckingUpdate: Boolean,
    hasUpdateAvailable: Boolean,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVi = lang == "VI"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "GameUpscaler Pro",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isVi) "Phiên bản v$currentVersion" else "Version v$currentVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isVi)
                        "Công cụ tối ưu hóa độ phân giải hiển thị (SSAA) và hiển thị thanh đo thông số phần cứng thời gian thực qua Shizuku."
                    else
                        "Display resolution optimizer (SSAA) and real-time hardware telemetry overlay via Shizuku.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isVi) "Tác giả: hoanfnho7" else "Author: hoanfnho7",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isVi) "Giấy phép: GNU General Public License v3.0" else "License: GNU GPL v3.0",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isVi) "Mã nguồn: 100% Open Source" else "Source: 100% Open Source",
                            style = MaterialTheme.typography.labelMedium,
                            color = TechEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Button(
                    onClick = onCheckUpdate,
                    enabled = !isCheckingUpdate,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = if (MaterialTheme.colorScheme.primary == Color(0xFFD4F469)) Color(0xFF171E00) else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = if (MaterialTheme.colorScheme.primary == Color(0xFFD4F469)) Color(0xFF171E00) else Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (hasUpdateAvailable) Icons.Default.SystemUpdate else Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isCheckingUpdate -> if (isVi) "Đang kiểm tra..." else "Checking..."
                            hasUpdateAvailable -> if (isVi) "Có bản cập nhật mới — Xem ngay" else "Update Available — View Now"
                            else -> if (isVi) "Kiểm tra cập nhật" else "Check for Updates"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hoanfnho7/GameUpscaler")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "https://github.com/hoanfnho7/GameUpscaler", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GitHub Repository",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (isVi) "Đóng" else "Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun UpdateAvailableDialog(
    release: AppReleaseInfo,
    currentVersion: String,
    lang: String,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallFile: (java.io.File) -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVi = lang == "VI"
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    val primaryContentColor = if (MaterialTheme.colorScheme.primary == Color(0xFFD4F469)) Color(0xFF171E00) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isVi) "Có bản cập nhật mới!" else "New Update Available!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    val metaParts = buildList {
                        add("v$currentVersion → ${release.tagName}")
                        val sz = AppUpdater.formatSizeMb(release.apkSizeBytes)
                        if (sz.isNotEmpty()) add(sz)
                        if (release.publishedDate.isNotEmpty()) add(release.publishedDate)
                    }
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Release notes card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isVi) "CÓ GÌ MỚI TRONG ${release.tagName}" else "WHAT'S NEW IN ${release.tagName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        val notesText = release.changelog.ifBlank {
                            if (isVi) "• Cải thiện hiệu năng, độ ổn định và sửa lỗi hệ thống."
                            else "• Performance improvements, stability enhancements, and bug fixes."
                        }
                        Text(
                            text = notesText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Live download status / progress bar
                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val pctText = if (downloadState.progress >= 0f) {
                                "${(downloadState.progress * 100).toInt()}%"
                            } else {
                                "..."
                            }
                            val dlMb = AppUpdater.formatSizeMb(downloadState.downloadedBytes)
                            val totalMb = AppUpdater.formatSizeMb(downloadState.totalBytes)
                            val sizeInfo = if (dlMb.isNotEmpty() && totalMb.isNotEmpty()) "$dlMb / $totalMb" else dlMb
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isVi) "Đang tải APK trực tiếp..." else "Downloading APK...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (sizeInfo.isNotEmpty()) "$pctText ($sizeInfo)" else pctText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (downloadState.progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = downloadState.progress.coerceIn(0f, 1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TechEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isVi) "Đã tải xong! Bấm Cài đặt để nâng cấp ngay." else "Download complete! Tap Install to upgrade.",
                                style = MaterialTheme.typography.labelMedium,
                                color = TechEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = (if (isVi) "Lỗi tải xuống: " else "Download failed: ") + downloadState.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = TechRed
                            )
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isVi) "Mở trang GitHub dự phòng" else "Open GitHub fallback",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    UpdateDownloadState.Idle -> Unit
                }

                // Primary Action Button inside dialog content for full width clean CTA
                when (downloadState) {
                    is UpdateDownloadState.ReadyToInstall -> {
                        Button(
                            onClick = { onInstallFile(downloadState.apkFile) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TechEmerald,
                                contentColor = Color(0xFF042214)
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isVi) "Cài đặt bản cập nhật" else "Install Update",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    else -> {
                        Button(
                            onClick = onStartDownload,
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = primaryContentColor
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isDownloading -> if (isVi) "Đang tải xuống..." else "Downloading..."
                                    downloadState is UpdateDownloadState.Error -> if (isVi) "Thử tải lại" else "Retry Download"
                                    else -> if (isVi) "Tải & Cài đặt ngay" else "Download & Install"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onSkipVersion) {
                    Text(
                        text = if (isVi) "Bỏ qua bản này" else "Skip version",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = onDismiss,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(if (isVi) "Để sau" else "Later", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun BottomNavigationBar(
    currentDestination: ScreenDestination,
    lang: String,
    onSelectDestination: (ScreenDestination) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Home,
                label = if (lang == "VI") "Trang chủ" else "Home",
                isSelected = currentDestination == ScreenDestination.HOME,
                onClick = { onSelectDestination(ScreenDestination.HOME) }
            )

            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.SportsEsports,
                label = if (lang == "VI") "Kho Game" else "Games",
                isSelected = currentDestination == ScreenDestination.GAMES,
                onClick = { onSelectDestination(ScreenDestination.GAMES) }
            )

            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = if (lang == "VI") "Thanh đo HUD" else "HUD",
                isSelected = currentDestination == ScreenDestination.HUD,
                onClick = { onSelectDestination(ScreenDestination.HUD) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val pillBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val borderCol = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent
    val contentCol = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = pillBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentCol,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentCol,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

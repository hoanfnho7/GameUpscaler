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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    // Handle Back Press
    BackHandler(enabled = currentDestination != ScreenDestination.HOME) {
        currentDestination = ScreenDestination.HOME
    }

    GameUpscalerTheme(darkTheme = homeUiState.isDarkTheme) {
        if (showAboutDialog) {
            AboutDialog(
                lang = homeUiState.currentLang,
                onDismiss = { showAboutDialog = false }
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

        // Right: Theme and Language quick toggle buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                        text = "Phiên bản v1.0.53",
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
                    text = if (lang == "VI")
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
                            text = if (lang == "VI") "Tác giả: hoanfnho7" else "Author: hoanfnho7",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (lang == "VI") "Giấy phép: GNU General Public License v3.0" else "License: GNU GPL v3.0",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (lang == "VI") "Mã nguồn: 100% Open Source" else "Source: 100% Open Source",
                            style = MaterialTheme.typography.labelMedium,
                            color = TechEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Button(
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = if (MaterialTheme.colorScheme.primary == Color(0xFFD4F469)) Color(0xFF171E00) else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GitHub Repository",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
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
                Text(if (lang == "VI") "Đóng" else "Close", fontWeight = FontWeight.Bold)
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

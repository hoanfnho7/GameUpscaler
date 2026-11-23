package com.nho.upscaler.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
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
import com.nho.upscaler.ui.theme.DarkBorderSubtle
import com.nho.upscaler.ui.theme.DarkSurfaceLowest
import com.nho.upscaler.ui.theme.GameUpscalerTheme
import com.nho.upscaler.ui.theme.VoltLime

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

    // Handle Back Press
    BackHandler(enabled = currentDestination != ScreenDestination.HOME) {
        currentDestination = ScreenDestination.HOME
    }

    GameUpscalerTheme(darkTheme = homeUiState.isDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Screen View with Crossfade Animation
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
                            viewModel = gameViewModel
                        )
                    }
                    ScreenDestination.HUD -> {
                        HudSettingsScreen(
                            viewModel = hudViewModel
                        )
                    }
                    ScreenDestination.ANALYTICS -> {
                        PerformanceAnalyticsScreen(
                            viewModel = analyticsViewModel,
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

@Composable
private fun BottomNavigationBar(
    currentDestination: ScreenDestination,
    lang: String,
    onSelectDestination: (ScreenDestination) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = DarkSurfaceLowest.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
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
    val pillBg = if (isSelected) VoltLime.copy(alpha = 0.15f) else Color.Transparent
    val borderCol = if (isSelected) VoltLime.copy(alpha = 0.35f) else Color.Transparent
    val contentCol = if (isSelected) VoltLime else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = pillBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
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

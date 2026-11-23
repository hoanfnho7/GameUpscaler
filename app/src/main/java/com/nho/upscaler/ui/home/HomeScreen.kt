package com.nho.upscaler.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nho.upscaler.ui.theme.DarkBorderSubtle
import com.nho.upscaler.ui.theme.DarkSurfaceHigh
import com.nho.upscaler.ui.theme.DarkSurfaceLow
import com.nho.upscaler.ui.theme.DarkSurfaceLowest
import com.nho.upscaler.ui.theme.TechAmber
import com.nho.upscaler.ui.theme.TechEmerald
import com.nho.upscaler.ui.theme.TechRed
import com.nho.upscaler.ui.theme.VoltLime
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToGames: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // TOP APP BAR (Real native header)
        TopAppBarHeader(
            isDark = uiState.isDarkTheme,
            lang = uiState.currentLang,
            onToggleTheme = { viewModel.toggleTheme() },
            onToggleLang = { viewModel.toggleLanguage() }
        )

        // SCROLLABLE CONTENT
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Title Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (uiState.currentLang == "VI") "THÔNG TIN HỆ THỐNG & TRẠNG THÁI" else "SYSTEM TELEMETRY & STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TechAmber,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (uiState.currentLang == "VI") "Trang chủ" else "Home",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                    }

                    // Pulsing Status Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PulsingDot(color = if (uiState.isShizukuReady) TechEmerald else TechAmber)
                        Text(
                            text = if (uiState.isShizukuReady) {
                                if (uiState.currentLang == "VI") "Dịch vụ sẵn sàng" else "Daemon Ready"
                            } else {
                                if (uiState.currentLang == "VI") "Chưa cấp Shizuku" else "No Shizuku"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }

            // Hero Status Card (Shizuku & Resolution Reset)
            item {
                HeroStatusCard(
                    uiState = uiState,
                    onResetRes = {
                        viewModel.resetNativeResolution(context)
                        Toast.makeText(context, if (uiState.currentLang == "VI") "Đã khôi phục độ phân giải gốc 1.0x" else "Reset to Native Resolution 1.0x", Toast.LENGTH_SHORT).show()
                    },
                    onOpenGames = onNavigateToGames
                )
            }

            // Real-Time Hardware Telemetry Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.currentLang == "VI") "THÔNG SỐ PHẦN CỨNG THỜI GIAN THỰC" else "REAL-TIME HARDWARE TELEMETRY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = TechEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "POLLING 1s",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechEmerald
                            )
                        }
                    }

                    // Card 1: RAM & Storage
                    RamStorageCard(uiState = uiState)

                    // Card 2: Battery & Temp
                    BatteryTempCard(uiState = uiState)

                    // Card 3: Display & SoC Tuning
                    DisplaySocCard(uiState = uiState)
                }
            }

            // Card History Logs & Charts
            item {
                HistoryLogsCard(
                    sessionCount = uiState.sessionCount,
                    lang = uiState.currentLang,
                    onClick = onNavigateToAnalytics
                )
            }

            // Setup Workflow (3 Steps)
            item {
                SetupWorkflowCard(
                    uiState = uiState,
                    onStep1 = { viewModel.requestShizukuPermission(context) },
                    onStep2 = { viewModel.requestOverlayPermission(context) },
                    onStep3 = { viewModel.requestBatteryOptimization(context) }
                )
            }
        }
    }
}

@Composable
private fun TopAppBarHeader(
    isDark: Boolean,
    lang: String,
    onToggleTheme: () -> Unit,
    onToggleLang: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Logo & Pro badge
        Row(
            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoltLime.copy(alpha = 0.2f))
                    .border(1.dp, VoltLime.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = VoltLime,
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
                        .background(VoltLime)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF171E00),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Right: Theme and Language buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Theme button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        tint = VoltLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDark) "Tối" else "Sáng",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Language button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = TechAmber,
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
private fun HeroStatusCard(
    uiState: HomeUiState,
    onResetRes: () -> Unit,
    onOpenGames: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(VoltLime.copy(alpha = 0.08f), Color.Transparent),
                        radius = 600f
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Eyebrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PulsingDot(color = if (uiState.isShizukuReady) TechEmerald else TechAmber)
                        Text(
                            text = if (uiState.isShizukuReady) {
                                if (uiState.currentLang == "VI") "KẾT NỐI SHIZUKU: SẴN SÀNG" else "SHIZUKU: CONNECTED"
                            } else {
                                if (uiState.currentLang == "VI") "KẾT NỐI SHIZUKU: CHƯA CẤP" else "SHIZUKU: DISCONNECTED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isShizukuReady) TechEmerald else TechAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TechEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ADB Daemon",
                            style = MaterialTheme.typography.labelSmall,
                            color = TechEmerald
                        )
                    }
                }

                // Heading & Description
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (uiState.isShizukuReady) {
                            if (uiState.currentLang == "VI") "SSAA Engine đang sẵn sàng" else "SSAA Engine is Ready"
                        } else {
                            if (uiState.currentLang == "VI") "Cần cấp quyền Shizuku" else "Shizuku Permission Required"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uiState.currentLang == "VI") {
                            "Đã nhận diện độ phân giải gốc. Sẵn sàng ép xung siêu phân giải khử răng cưa và kích hoạt thanh đo FPS nổi."
                        } else {
                            "Native display identified. Ready to super-sample and upscale render targets."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Actions: Reset & Open Game Library
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset Button
                    Button(
                        onClick = onResetRes,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = TechAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.currentLang == "VI") "Reset gốc" else "Reset Native",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Open Games Button
                    Button(
                        onClick = onOpenGames,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoltLime,
                            contentColor = Color(0xFF171E00)
                        ),
                        modifier = Modifier.weight(1.1f).height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color(0xFF171E00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.currentLang == "VI") "Mở Kho Game" else "Game Library",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamStorageCard(uiState: HomeUiState) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = VoltLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (uiState.currentLang == "VI") "RAM & Bộ nhớ" else "RAM & Storage",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "LPDDR5X + UFS 4.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(VoltLime.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${uiState.ramUsedPct}% " + (if (uiState.currentLang == "VI") "ĐÃ DÙNG" else "USED"),
                        style = MaterialTheme.typography.labelSmall,
                        color = VoltLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = (uiState.ramUsedPct / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = VoltLime,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "RAM: %.1f GB / %.0f GB", uiState.ramUsedGb, uiState.ramTotalGb),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "%s: %d GB", if (uiState.currentLang == "VI") "Trống" else "Free", uiState.freeUfsGb),
                    style = MaterialTheme.typography.labelMedium,
                    color = TechEmerald
                )
            }
        }
    }
}

@Composable
private fun BatteryTempCard(uiState: HomeUiState) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = TechAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = if (uiState.currentLang == "VI") "Pin & Nhiệt độ" else "Battery & Thermal",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = if (uiState.currentLang == "VI") "Cảm biến nhiệt độ pin thực tế" else "Real-time battery sensor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${uiState.batteryPct}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f°C", uiState.batteryTemp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.batteryTemp >= 43f) TechRed else TechAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (uiState.batteryTemp >= 43f) Icons.Default.BatteryAlert else Icons.Default.BatteryStd,
                    contentDescription = null,
                    tint = if (uiState.isCharging) TechEmerald else VoltLime,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun DisplaySocCard(uiState: HomeUiState) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = VoltLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (uiState.currentLang == "VI") "Màn hình & SoC" else "Display & SoC",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.socName} • ${uiState.refreshRate}Hz",
                            style = MaterialTheme.typography.labelMedium,
                            color = VoltLime,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(TechEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Vulkan API",
                        style = MaterialTheme.typography.labelSmall,
                        color = TechEmerald,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Comparison Box (Native -> SSAA)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceLowest.copy(alpha = 0.6f))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.currentLang == "VI") "GỐC" else "NATIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.nativeW}x${uiState.nativeH} (${uiState.nativeDen} DPI)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.TrendingFlat,
                        contentDescription = null,
                        tint = VoltLime,
                        modifier = Modifier.size(20.dp).padding(horizontal = 4.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (uiState.currentLang == "VI") "SSAA TỐI ƯU" else "SSAA UPSCALE",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoltLime,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.targetW}x${uiState.targetH} (${uiState.targetDen} DPI)",
                            style = MaterialTheme.typography.labelMedium,
                            color = VoltLime,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLogsCard(
    sessionCount: Int,
    lang: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, TechAmber.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TechAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = TechAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (lang == "VI") "Lịch sử Log & Biểu đồ" else "Performance Logs & Charts",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$sessionCount " + (if (lang == "VI") "phiên" else "sessions"),
                                style = MaterialTheme.typography.labelSmall,
                                color = TechAmber,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = if (lang == "VI") "Xem biểu đồ FPS, dao động khung hình và nhiệt độ" else "Inspect FPS stability, power and thermal analytics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = VoltLime,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SetupWorkflowCard(
    uiState: HomeUiState,
    onStep1: () -> Unit,
    onStep2: () -> Unit,
    onStep3: () -> Unit
) {
    val completedCount = (if (uiState.isShizukuReady) 1 else 0) +
            (if (uiState.isOverlayGranted) 1 else 0) +
            (if (uiState.isBatteryOptIgnored) 1 else 0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.currentLang == "VI") "QUY TRÌNH THIẾT LẬP QUYỀN (3 BƯỚC)" else "SETUP PERMISSIONS (3 STEPS)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$completedCount/3 " + (if (uiState.currentLang == "VI") "Hoàn tất" else "Ready"),
                style = MaterialTheme.typography.labelSmall,
                color = if (completedCount == 3) TechEmerald else TechAmber,
                fontWeight = FontWeight.Bold
            )
        }

        // Step 1: Shizuku
        SetupStepItem(
            icon = Icons.Default.Verified,
            title = if (uiState.currentLang == "VI") "Bước 1: Cấp quyền Shizuku" else "Step 1: Shizuku Permission",
            subtitle = if (uiState.isShizukuReady) {
                if (uiState.currentLang == "VI") "Đã kết nối Shizuku Service thành công" else "Shizuku Service connected"
            } else {
                if (uiState.currentLang == "VI") "Chạm để cấp quyền Shizuku / ADB" else "Tap to grant Shizuku access"
            },
            isDone = uiState.isShizukuReady,
            doneText = if (uiState.currentLang == "VI") "Đã cấp" else "Granted",
            pendingText = if (uiState.currentLang == "VI") "Chưa cấp" else "Pending",
            onClick = onStep1
        )

        // Step 2: Overlay
        SetupStepItem(
            icon = Icons.Default.Layers,
            title = if (uiState.currentLang == "VI") "Bước 2: Quyền hiển thị trên ứng dụng khác" else "Step 2: Draw Over Other Apps",
            subtitle = if (uiState.isOverlayGranted) {
                if (uiState.currentLang == "VI") "Cho phép vẽ HUD FPS trên nền game" else "Allows floating HUD over games"
            } else {
                if (uiState.currentLang == "VI") "Chạm để mở cài đặt cấp quyền" else "Tap to open overlay settings"
            },
            isDone = uiState.isOverlayGranted,
            doneText = if (uiState.currentLang == "VI") "Đã cấp" else "Granted",
            pendingText = if (uiState.currentLang == "VI") "Chưa cấp" else "Pending",
            onClick = onStep2
        )

        // Step 3: Battery Optimization
        SetupStepItem(
            icon = Icons.Default.BatterySaver,
            title = if (uiState.currentLang == "VI") "Bước 3: Tắt tối ưu hóa pin" else "Step 3: Ignore Battery Optimization",
            subtitle = if (uiState.isBatteryOptIgnored) {
                if (uiState.currentLang == "VI") "Chạy nền không bị hệ thống kill process" else "Prevents background process kill"
            } else {
                if (uiState.currentLang == "VI") "Chạm để tắt tối ưu hóa pin" else "Tap to ignore optimizations"
            },
            isDone = uiState.isBatteryOptIgnored,
            doneText = if (uiState.currentLang == "VI") "Đã tắt" else "Ignored",
            pendingText = if (uiState.currentLang == "VI") "Chưa tắt" else "Not Ignored",
            onClick = onStep3
        )
    }
}

@Composable
private fun SetupStepItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isDone: Boolean,
    doneText: String,
    pendingText: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDone) VoltLime.copy(alpha = 0.2f) else TechAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDone) VoltLime else TechAmber,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isDone) VoltLime.copy(alpha = 0.15f) else TechAmber.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isDone) doneText else pendingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDone) VoltLime else TechAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .alpha(alpha)
            .background(color)
    )
}

package com.nho.upscaler.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nho.upscaler.ui.theme.DarkBorderSubtle
import com.nho.upscaler.ui.theme.DarkSurfaceHigh
import com.nho.upscaler.ui.theme.DarkSurfaceLow
import com.nho.upscaler.ui.theme.DarkSurfaceLowest
import com.nho.upscaler.ui.theme.TechAmber
import com.nho.upscaler.ui.theme.TechBlue
import com.nho.upscaler.ui.theme.TechEmerald
import com.nho.upscaler.ui.theme.TechRed
import com.nho.upscaler.ui.theme.VoltLime
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Locale

@Composable
fun HudSettingsScreen(
    viewModel: HudSettingsViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val accentColors = listOf(
        VoltLime,
        Color(0xFFFFFFFF),
        TechBlue,
        Color(0xFFFACC15)
    )
    val currentAccent = accentColors.getOrElse(uiState.colorAccentIdx) { VoltLime }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "HUD OVERLAY & HARDWARE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Cài đặt Thanh đo Nổi",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tùy biến hiển thị thông số hiệu năng phần cứng thời gian thực khi chơi game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Preview Canvas (20:9 Game Simulation)
            item {
                LiveHudPreviewCanvas(
                    uiState = uiState,
                    accentColor = currentAccent
                )
            }

            // Hero Toggle CTA Button
            item {
                Button(
                    onClick = { viewModel.toggleActualHud(context) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isHudActive) TechRed else VoltLime,
                        contentColor = if (uiState.isHudActive) Color.White else Color(0xFF171E00)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isHudActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isHudActive) "DỪNG THANH ĐO NỔI (STOP OVERLAY)" else "Khởi động thanh đo nổi ngay (Test Overlay)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Appearance Controls Panel
            item {
                HudAppearanceCard(
                    uiState = uiState,
                    accentColors = accentColors,
                    onLayoutChange = { viewModel.setLayoutPreset(it, context) },
                    onScaleChange = { viewModel.setScale(it, context) },
                    onOpacityChange = { viewModel.setOpacity(it, context) },
                    onColorChange = { viewModel.setColorAccent(it, context) },
                    onPollingRateChange = { viewModel.setPollingRate(it, context) }
                )
            }

            // 13 Telemetry Metrics Toggles
            item {
                HudMetricsListCard(
                    metricsMap = uiState.metricsMap,
                    onToggleMetric = { viewModel.toggleMetric(it, context) }
                )
            }

            // 3 System Utilities Row
            item {
                HudUtilityActionsRow(
                    onResetCoords = { viewModel.resetHudCoordinates(context) },
                    onInspectSensors = { viewModel.openThermalDialog(context) },
                    onAddQsTile = { viewModel.openQsTileDialog() }
                )
            }
        }
    }

    // Thermal Sensors Dialog
    if (uiState.isThermalDialogOpen) {
        ThermalSensorsDialog(
            sensors = uiState.thermalSensors,
            onDismiss = { viewModel.closeThermalDialog() }
        )
    }

    // Quick Settings Tile Guide Dialog
    if (uiState.isQsTileDialogOpen) {
        QsTileGuideDialog(
            onDismiss = { viewModel.closeQsTileDialog() }
        )
    }
}

@Composable
private fun LiveHudPreviewCanvas(
    uiState: HudSettingsUiState,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = VoltLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "KHUNG MÔ PHỎNG THỜI GIAN THỰC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Tỷ lệ 20:9",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoltLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Simulated Game Screen (20:9 aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(20f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E1B4B),
                                Color(0xFF0C0E11)
                            )
                        )
                    )
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                // Background grid pattern simulation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )

                // Simulated Floating HUD Pill
                val bgAlpha = (uiState.bgOpacity / 100f).coerceIn(0.1f, 1f)
                val pillBg = DarkSurfaceLowest.copy(alpha = bgAlpha)
                val pillBorder = accentColor.copy(alpha = 0.4f)

                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .graphicsLayer(
                            scaleX = uiState.scaleLevel.coerceIn(0.5f, 1.3f),
                            scaleY = uiState.scaleLevel.coerceIn(0.5f, 1.3f)
                        )
                        .clip(if (uiState.layoutPreset == HudLayoutPreset.VERTICAL) RoundedCornerShape(14.dp) else CircleShape)
                        .background(pillBg)
                        .border(
                            1.dp,
                            pillBorder,
                            if (uiState.layoutPreset == HudLayoutPreset.VERTICAL) RoundedCornerShape(14.dp) else CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (uiState.layoutPreset == HudLayoutPreset.HORIZONTAL) {
                        // Horizontal layout: ● 59.8 FPS | 🌡️ 41.2°C | 🔋 5.4W | 🎮 68%
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.metricsMap["m_fps"] != false) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                                    Text("59.8 FPS", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (uiState.metricsMap["m_bat_temp"] != false || uiState.metricsMap["m_soc_temp"] != false) {
                                Text("|", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("🌡️ 41.2°C", style = MaterialTheme.typography.labelSmall, color = TechAmber, fontWeight = FontWeight.Bold)
                            }
                            if (uiState.metricsMap["m_watt"] != false) {
                                Text("|", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("🔋 5.4W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            }
                            if (uiState.metricsMap["m_gpu"] != false) {
                                Text("|", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("🎮 68%", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Vertical layout
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (uiState.metricsMap["m_fps"] != false) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accentColor))
                                    Text("59.8 FPS", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (uiState.metricsMap["m_bat_temp"] != false || uiState.metricsMap["m_soc_temp"] != false) {
                                Text("🌡️ 41.2°C", style = MaterialTheme.typography.labelSmall, color = TechAmber)
                            }
                            if (uiState.metricsMap["m_watt"] != false) {
                                Text("🔋 5.4W", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (uiState.metricsMap["m_gpu"] != false) {
                                Text("🎮 68%", style = MaterialTheme.typography.labelSmall, color = accentColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudAppearanceCard(
    uiState: HudSettingsUiState,
    accentColors: List<Color>,
    onLayoutChange: (HudLayoutPreset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    onPollingRateChange: (PollingRatePreset) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "TÙY CHỈNH GIAO DIỆN THANH ĐO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Layout Presets (2 options)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Kiểu bố cục hiển thị",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Horizontal
                    val isHoriz = uiState.layoutPreset == HudLayoutPreset.HORIZONTAL
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isHoriz) DarkSurfaceHigh else DarkSurfaceLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isHoriz) VoltLime.copy(alpha = 0.5f) else DarkBorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onLayoutChange(HudLayoutPreset.HORIZONTAL) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewStream,
                                contentDescription = null,
                                tint = if (isHoriz) VoltLime else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Thanh ngang 1 dòng",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isHoriz) VoltLime else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Option 2: Vertical
                    val isVert = uiState.layoutPreset == HudLayoutPreset.VERTICAL
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isVert) DarkSurfaceHigh else DarkSurfaceLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isVert) VoltLime.copy(alpha = 0.5f) else DarkBorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onLayoutChange(HudLayoutPreset.VERTICAL) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = null,
                                tint = if (isVert) VoltLime else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Xếp dọc",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isVert) VoltLime else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Slider: Kích thước HUD (0.7x - 1.5x)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Kích thước thanh đo",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fx (%d%%)", uiState.scaleLevel, (uiState.scaleLevel * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = VoltLime,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = uiState.scaleLevel,
                    onValueChange = onScaleChange,
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = VoltLime,
                        activeTrackColor = VoltLime,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x (Siêu nhỏ)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0.7x (Nhỏ)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1.0x (Chuẩn)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1.5x (Rộng)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Slider: Độ mờ nền (0% - 100%)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Độ mờ nền (Opacity)",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${uiState.bgOpacity}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = VoltLime,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = uiState.bgOpacity.toFloat(),
                    onValueChange = { onOpacityChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = VoltLime,
                        activeTrackColor = VoltLime,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0% (Trong suốt)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("85% (Chuẩn)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("100% (Đen mờ)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Accent Color Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Màu chữ chủ đạo",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Tự chuyển đỏ khi Pin ≥ 43°C hoặc SoC ≥ 85°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = TechAmber
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accentColors.forEachIndexed { idx, color ->
                            val isSelected = uiState.colorAccentIdx == idx
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        2.dp,
                                        if (isSelected) Color.White else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onColorChange(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF171E00))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Polling Rate Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tốc độ quét cảm biến",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PollingRatePreset.values().forEach { preset ->
                        val isSel = uiState.pollingRate == preset
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) VoltLime else DarkSurfaceLowest,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSel) VoltLime else DarkBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable { onPollingRateChange(preset) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) Color(0xFF171E00) else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetricsListCard(
    metricsMap: Map<String, Boolean>,
    onToggleMetric: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val enabledCount = metricsMap.count { it.value }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CÁC THÔNG SỐ HIỂN THỊ TRÊN THANH ĐO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$enabledCount/13 Kích hoạt",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoltLime,
                    fontWeight = FontWeight.Bold
                )
            }

            // 13 Items
            val metricsList = listOf(
                MetricItemDef("m_fps", "Tốc độ khung hình (FPS)", "Hiển thị real-time FPS đo chính xác 0.1", Icons.Default.Speed),
                MetricItemDef("m_watt", "Công suất điện (Watt)", "Điện năng tiêu thụ phần cứng tức thời (W)", Icons.Default.BatteryChargingFull),
                MetricItemDef("m_gpu", "Mức tải GPU (%)", "Tỷ lệ chiếm dụng nhân đồ họa GPU", Icons.Default.DeveloperBoard),
                MetricItemDef("m_vram", "Bộ nhớ đồ họa VRAM (MB)", "Dung lượng VRAM buffer đang dùng", Icons.Default.Memory),
                MetricItemDef("m_cpu_freq", "Xung nhịp CPU (GHz)", "Tần số xung nhịp cụm nhân CPU", Icons.Default.Speed),
                MetricItemDef("m_bat_temp", "Nhiệt độ Pin (°C)", "Cảm biến nhiệt độ cell pin & mạch sạc", Icons.Default.Thermostat),
                MetricItemDef("m_soc_temp", "Nhiệt độ đỉnh SoC (°C)", "Điểm nóng cao nhất trên die vi xử lý", Icons.Default.Thermostat),
                MetricItemDef("m_cpu_temp", "Nhiệt độ lõi CPU (°C)", "Cảm biến nhiệt độ nhân CPU Prime/Gold", Icons.Default.Thermostat),
                MetricItemDef("m_gpu_temp", "Nhiệt độ lõi GPU (°C)", "Cảm biến nhiệt vùng bóng bán dẫn đồ họa", Icons.Default.Thermostat),
                MetricItemDef("m_ddr_temp", "Nhiệt độ RAM DDR (°C)", "Cảm biến chip nhớ LPDDR5X", Icons.Default.Memory),
                MetricItemDef("m_cam_temp", "Nhiệt độ cụm Camera (°C)", "Nhiệt tản lan sang khu vực mô-đun camera", Icons.Default.CameraAlt),
                MetricItemDef("m_skin_temp", "Nhiệt độ vỏ / bo mạch (°C)", "Nhiệt độ khung viền/mặt lưng tiếp xúc tay", Icons.Default.PhoneAndroid),
                MetricItemDef("m_ram", "Dung lượng RAM", "Mức RAM đang dùng / khả dụng", Icons.Default.Memory)
            )

            metricsList.forEach { item ->
                val isChecked = metricsMap[item.key] ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceLowest)
                        .padding(10.dp),
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
                                .background(DarkSurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isChecked) VoltLime else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Switch(
                        checked = isChecked,
                        onCheckedChange = { onToggleMetric(item.key) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF171E00),
                            checkedTrackColor = VoltLime,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

private data class MetricItemDef(
    val key: String,
    val title: String,
    val desc: String,
    val icon: ImageVector
)

@Composable
private fun HudUtilityActionsRow(
    onResetCoords: () -> Unit,
    onInspectSensors: () -> Unit,
    onAddQsTile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tile 1: Quick Settings
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkSurfaceLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onAddQsTile() }
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddModerator,
                    contentDescription = null,
                    tint = TechAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Control Center",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Tile 2: Reset position
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkSurfaceLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onResetCoords() }
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = VoltLime,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Reset Vị Trí",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Tile 3: Inspect thermal zones
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkSurfaceLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onInspectSensors() }
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = TechRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Soi Cảm Biến",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ThermalSensorsDialog(
    sensors: List<Pair<String, Float>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CẢM BIẾN NHIỆT ZONE",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sensors) { (name, temp) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceLowest)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f°C", temp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (temp >= 43f) TechRed else VoltLime,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = Color(0xFF171E00))
            ) {
                Text("Đóng", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceLow,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun QsTileGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "THÊM QUICK SETTINGS TILE",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Để bật/tắt nhanh HUD trực tiếp từ thanh thông báo mà không cần mở app:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "1. Vuốt thanh thông báo Android xuống 2 lần.\n2. Bấm vào nút Chỉnh sửa / Cây bút.\n3. Kéo ô 'HUD Game' vào bảng phím tắt nhanh.\n4. Xong! Bấm ô này bất cứ lúc nào để bật/tắt HUD nổi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = Color(0xFF171E00))
            ) {
                Text("Đã hiểu", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceLow,
        shape = RoundedCornerShape(22.dp)
    )
}

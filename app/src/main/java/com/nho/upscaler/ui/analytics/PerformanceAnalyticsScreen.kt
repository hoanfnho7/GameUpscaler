package com.nho.upscaler.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nho.upscaler.PerfSample
import com.nho.upscaler.PerfSession
import com.nho.upscaler.ui.theme.TechAmber
import com.nho.upscaler.ui.theme.TechEmerald
import com.nho.upscaler.ui.theme.TechRed
import com.nho.upscaler.ui.theme.VoltLime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PerformanceAnalyticsScreen(
    viewModel: AnalyticsViewModel,
    lang: String = "VI",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val session = uiState.selectedSession

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top App Bar (Back + Title + Export CSV + Delete)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = if (lang == "VI") "Phân Tích Hiệu Năng" else "Performance Analytics",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session != null) {
                    IconButton(onClick = { viewModel.exportSessionCsv(context) }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export CSV",
                            tint = VoltLime
                        )
                    }

                    IconButton(onClick = { viewModel.openDeleteDialog() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Session",
                            tint = TechRed
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        // Scrollable Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (session == null) {
                // Empty state when no sessions recorded yet
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(VoltLime.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = VoltLime,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = if (lang == "VI") "Chưa có dữ liệu phiên đo" else "No Session Data",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (lang == "VI")
                                    "Hãy bật thanh đo HUD và mở game từ app để tự động ghi lại dữ liệu FPS, nhiệt độ và công suất thời gian thực."
                                else
                                    "Enable the floating HUD and launch a game to start recording real-time performance telemetry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Row 1: Game Filter Chips (Horizontal Scroll)
                if (uiState.gameFilters.size > 1) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.gameFilters) { (id, name) ->
                                val isSelected = uiState.selectedGameFilter == id
                                val filterLabel = if (id == "all") (if (lang == "VI") "Tất cả" else "All") else name
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) VoltLime else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) VoltLime else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { viewModel.selectGameFilter(id) }
                                ) {
                                    Text(
                                        text = filterLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) Color(0xFF171E00) else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Row 2: Session List Chips (Horizontal Scroll)
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredSessions) { s ->
                            val isSelected = s.fileName == session.fileName
                            val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(s.timestampMs))
                            val durationMin = s.durationSec / 60
                            val durationSec = s.durationSec % 60
                            val durStr = String.format(Locale.US, "%02d:%02d", durationMin, durationSec)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) VoltLime.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.selectSession(s) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = s.scaleLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) VoltLime else TechAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f FPS", s.avgFps),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = durStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3 KPI Cards Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // KPI 1: FPS
                        KpiCard(
                            modifier = Modifier.weight(1f),
                            label = if (lang == "VI") "ĐỘ MƯỢT FPS" else "AVG FPS",
                            primaryVal = String.format(Locale.US, "%.1f", session.avgFps),
                            secondaryVal = String.format(Locale.US, "1%% Low: %.1f", session.low1Fps),
                            accentColor = VoltLime
                        )

                        // KPI 2: Power
                        KpiCard(
                            modifier = Modifier.weight(1f),
                            label = if (lang == "VI") "CÔNG SUẤT" else "POWER",
                            primaryVal = String.format(Locale.US, "%.1fW", session.avgWatts),
                            secondaryVal = String.format(Locale.US, if (lang == "VI") "Đỉnh: %.1fW" else "Peak: %.1fW", session.maxWatts),
                            accentColor = TechAmber
                        )

                        // KPI 3: Peak Temp
                        KpiCard(
                            modifier = Modifier.weight(1f),
                            label = if (lang == "VI") "ĐỈNH NHIỆT" else "PEAK TEMP",
                            primaryVal = String.format(Locale.US, "%.0f°C", session.peakSocTemp),
                            secondaryVal = String.format(Locale.US, "Pin: %.1f°C", session.peakBatTemp),
                            accentColor = TechRed
                        )
                    }
                }

                // Scrub Readout Banner (Real-time dynamic cursor values)
                item {
                    val scrub = uiState.scrubSample ?: session.samples.firstOrNull()
                    if (scrub != null) {
                        val min = scrub.sec / 60
                        val sec = scrub.sec % 60
                        val timeStr = String.format(Locale.US, "%02d:%02d", min, sec)

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, VoltLime.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = VoltLime,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VoltLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f FPS", scrub.fps),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VoltLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1fW", scrub.watts),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TechAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "SoC %.1f°", scrub.socTemp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TechRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "Pin %.1f°", scrub.batTemp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TechEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Chart 1: FPS Curve
                item {
                    TimeseriesChartCard(
                        title = if (lang == "VI") "TỐC ĐỘ KHUNG HÌNH (FPS)" else "FRAME RATE (FPS)",
                        badgeText = String.format(Locale.US, "%.1f FPS", uiState.scrubSample?.fps ?: session.avgFps),
                        samples = session.samples,
                        valueExtractor = { it.fps },
                        minVal = 0f,
                        maxVal = 144f,
                        lineColor = VoltLime,
                        scrubIndex = uiState.scrubIndex,
                        onScrubFraction = { viewModel.onScrubFractionChange(it) }
                    )
                }

                // Chart 2: Power Curve
                item {
                    val peakW = session.maxWatts.coerceAtLeast(8f)
                    TimeseriesChartCard(
                        title = if (lang == "VI") "CÔNG SUẤT TIÊU THỤ (WATT)" else "POWER CONSUMPTION (WATT)",
                        badgeText = String.format(Locale.US, "%.1f W", uiState.scrubSample?.watts ?: session.avgWatts),
                        samples = session.samples,
                        valueExtractor = { it.watts },
                        minVal = 0f,
                        maxVal = peakW * 1.2f,
                        lineColor = TechAmber,
                        scrubIndex = uiState.scrubIndex,
                        onScrubFraction = { viewModel.onScrubFractionChange(it) }
                    )
                }

                // Chart 3: Temperature Dual-Series Curve (SoC & Battery)
                item {
                    DualSeriesTempChartCard(
                        title = if (lang == "VI") "BIẾN THIÊN NHIỆT ĐỘ (°C)" else "TEMPERATURE CURVES (°C)",
                        samples = session.samples,
                        socTemp = uiState.scrubSample?.socTemp ?: session.peakSocTemp,
                        batTemp = uiState.scrubSample?.batTemp ?: session.peakBatTemp,
                        scrubIndex = uiState.scrubIndex,
                        onScrubFraction = { viewModel.onScrubFractionChange(it) }
                    )
                }

                // Footer gesture hint
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "VI")
                                "Chạm hoặc kéo trên biểu đồ để soi chi tiết từng giây của phiên chơi"
                            else
                                "Touch or drag across graphs to inspect second-by-second telemetry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (uiState.isDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteDialog() },
            title = {
                Text(
                    text = if (lang == "VI") "XÓA PHIÊN LOG" else "DELETE SESSION LOG",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (lang == "VI")
                        "Bạn có chắc chắn muốn xóa dữ liệu phân tích của phiên chơi này không? Dữ liệu đã xóa không thể khôi phục."
                    else
                        "Are you sure you want to delete this recorded session? Deleted data cannot be recovered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteCurrentSession(context) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (lang == "VI") "Xóa vĩnh viễn" else "Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDeleteDialog() }) {
                    Text(if (lang == "VI") "Hủy" else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // CSV Export Successful Dialog
    if (uiState.exportedCsvPath != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportDialog() },
            title = {
                Text(
                    text = if (lang == "VI") "XUẤT CSV THÀNH CÔNG" else "CSV EXPORT SUCCESS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VoltLime,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "VI") "Dữ liệu phiên chơi đã được xuất ra tệp CSV:" else "Session telemetry exported to CSV:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.exportedCsvPath ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissExportDialog() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = Color(0xFF171E00))
                ) {
                    Text(if (lang == "VI") "Đóng" else "Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    label: String,
    primaryVal: String,
    secondaryVal: String,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = primaryVal,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1
            )
            Text(
                text = secondaryVal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimeseriesChartCard(
    title: String,
    badgeText: String,
    samples: List<PerfSample>,
    valueExtractor: (PerfSample) -> Float,
    minVal: Float,
    maxVal: Float,
    lineColor: Color,
    scrubIndex: Int,
    onScrubFraction: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(lineColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = lineColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(samples) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrubFraction(frac)
                        }
                    }
                    .pointerInput(samples) {
                        detectDragGestures { change, _ ->
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrubFraction(frac)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (samples.size < 2) return@Canvas

                    val w = size.width
                    val h = size.height
                    val range = (maxVal - minVal).coerceAtLeast(1f)

                    val path = Path()
                    val fillPath = Path()

                    val stepX = w / (samples.size - 1)

                    samples.forEachIndexed { i, s ->
                        val v = valueExtractor(s).coerceIn(minVal, maxVal)
                        val x = i * stepX
                        val y = h - ((v - minVal) / range) * h

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Draw Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Line
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Scrub Line & Dot
                    if (scrubIndex in samples.indices) {
                        val scrubX = scrubIndex * stepX
                        val scrubVal = valueExtractor(samples[scrubIndex]).coerceIn(minVal, maxVal)
                        val scrubY = h - ((scrubVal - minVal) / range) * h

                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )

                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualSeriesTempChartCard(
    title: String,
    samples: List<PerfSample>,
    socTemp: Float,
    batTemp: Float,
    scrubIndex: Int,
    onScrubFraction: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TechRed))
                        Text(String.format(Locale.US, "SoC %.1f°C", socTemp), style = MaterialTheme.typography.labelSmall, color = TechRed, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TechEmerald))
                        Text(String.format(Locale.US, "Pin %.1f°C", batTemp), style = MaterialTheme.typography.labelSmall, color = TechEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Canvas Chart Dual Lines
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(samples) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrubFraction(frac)
                        }
                    }
                    .pointerInput(samples) {
                        detectDragGestures { change, _ ->
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrubFraction(frac)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (samples.size < 2) return@Canvas

                    val w = size.width
                    val h = size.height
                    val minVal = 25f
                    val maxVal = 85f
                    val range = maxVal - minVal
                    val stepX = w / (samples.size - 1)

                    val socPath = Path()
                    val batPath = Path()

                    samples.forEachIndexed { i, s ->
                        val x = i * stepX
                        val socY = h - ((s.socTemp.coerceIn(minVal, maxVal) - minVal) / range) * h
                        val batY = h - ((s.batTemp.coerceIn(minVal, maxVal) - minVal) / range) * h

                        if (i == 0) {
                            socPath.moveTo(x, socY)
                            batPath.moveTo(x, batY)
                        } else {
                            socPath.lineTo(x, socY)
                            batPath.lineTo(x, batY)
                        }
                    }

                    // Draw SoC Line (Red)
                    drawPath(
                        path = socPath,
                        color = TechRed,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Battery Line (Emerald)
                    drawPath(
                        path = batPath,
                        color = TechEmerald,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Scrub Line
                    if (scrubIndex in samples.indices) {
                        val scrubX = scrubIndex * stepX
                        val s = samples[scrubIndex]
                        val socY = h - ((s.socTemp.coerceIn(minVal, maxVal) - minVal) / range) * h
                        val batY = h - ((s.batTemp.coerceIn(minVal, maxVal) - minVal) / range) * h

                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )

                        drawCircle(color = TechRed, radius = 3.5.dp.toPx(), center = Offset(scrubX, socY))
                        drawCircle(color = TechEmerald, radius = 3.5.dp.toPx(), center = Offset(scrubX, batY))
                    }
                }
            }
        }
    }
}

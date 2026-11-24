package com.nho.upscaler.ui.games

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nho.upscaler.ui.theme.TechAmber
import com.nho.upscaler.ui.theme.TechBlue
import com.nho.upscaler.ui.theme.TechEmerald
import com.nho.upscaler.ui.theme.VoltLime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    viewModel: GameLibraryViewModel,
    lang: String = "VI"
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
    ) {
        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Title
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (lang == "VI") "ĐỘ PHÂN GIẢI & ĐỒ HỌA GAME" else "GAME RESOLUTION & GRAPHICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (lang == "VI") "Kho Game & SSAA" else "Game Library & SSAA",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (lang == "VI")
                            "Cấu hình tỉ lệ SSAA chung hoặc khóa cấu hình riêng cho từng tựa game."
                        else
                            "Configure global SSAA scale or lock custom resolution per game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Auto-launch HUD banner
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (lang == "VI") "Tự động kích hoạt HUD" else "Auto Launch HUD",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (lang == "VI") "Bật thanh đo nổi khi khởi chạy game từ app" else "Enable floating HUD when launching games",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.autoLaunchHud,
                            onCheckedChange = { viewModel.toggleAutoLaunchHud(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            // Global SSAA Resolution Scale Group (4 options)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "VI") "TỶ LỆ TĂNG PHÂN GIẢI (SSAA)" else "UPSCALE RESOLUTION (SSAA)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (lang == "VI") "Mặc định: ${uiState.globalScale}x" else "Default: ${uiState.globalScale}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 4 Options 2x2 Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SsaaOptionCard(
                            modifier = Modifier.weight(1f),
                            scale = 1.10f,
                            label = "1.10x",
                            tag = if (lang == "VI") "Nhẹ" else "Light",
                            desc = if (lang == "VI") "Tiết kiệm pin nhất, nhiệt độ mát mẻ (+2% pin)." else "Best battery life, cool temp (+2% battery).",
                            isSelected = Math.abs(uiState.globalScale - 1.10f) < 0.01f,
                            onClick = { viewModel.setGlobalScale(1.10f, context) }
                        )

                        SsaaOptionCard(
                            modifier = Modifier.weight(1f),
                            scale = 1.15f,
                            label = "1.15x",
                            tag = if (lang == "VI") "Cân bằng" else "Balanced",
                            desc = if (lang == "VI") "Khử răng cưa mịn, cân bằng nhiệt độ và đồ họa." else "Smooth anti-aliasing, balanced thermal & graphics.",
                            isSelected = Math.abs(uiState.globalScale - 1.15f) < 0.01f,
                            onClick = { viewModel.setGlobalScale(1.15f, context) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SsaaOptionCard(
                            modifier = Modifier.weight(1f),
                            scale = 1.20f,
                            label = "1.20x",
                            tag = if (lang == "VI") "Sắc nét" else "Sharp",
                            desc = if (lang == "VI") "Hình ảnh rõ nét, khử răng cưa chi tiết (+6% pin)." else "High clarity, crisp anti-aliasing (+6% battery).",
                            isSelected = Math.abs(uiState.globalScale - 1.20f) < 0.01f,
                            onClick = { viewModel.setGlobalScale(1.20f, context) }
                        )

                        SsaaOptionCard(
                            modifier = Modifier.weight(1f),
                            scale = 1.25f,
                            label = "1.25x",
                            tag = if (lang == "VI") "Tối đa" else "Ultra",
                            desc = if (lang == "VI") "Chuẩn Esports, tối đa độ nét viền góc (+8% pin)." else "Esports grade, max edge supersampling (+8% battery).",
                            isSelected = Math.abs(uiState.globalScale - 1.25f) < 0.01f,
                            onClick = { viewModel.setGlobalScale(1.25f, context) }
                        )
                    }
                }
            }

            // My Games Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (lang == "VI") "GAME CỦA TÔI" else "MY GAMES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TechEmerald.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (lang == "VI") "${uiState.games.size} Đã thêm" else "${uiState.games.size} Added",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Add game button
                    TextButton(
                        onClick = { viewModel.openAppPicker() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lang == "VI") "Thêm Game" else "Add Game",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Search input field
            if (uiState.games.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(
                                text = if (lang == "VI") "Tìm kiếm game đã thêm..." else "Search added games...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )
                }
            }

            // Games List
            if (uiState.games.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = if (lang == "VI") "Chưa có game nào" else "No games added",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (lang == "VI")
                                    "Bấm '+ Thêm Game' ở trên để chọn các game bạn muốn tối ưu và nâng độ phân giải."
                                else
                                    "Tap '+ Add Game' above to select games on your device to optimize.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { viewModel.openAppPicker() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (lang == "VI") "Thêm Game ngay" else "Add Game Now",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else if (uiState.filteredGames.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (lang == "VI") "Không tìm thấy game" else "No games found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (lang == "VI") "Không có game nào khớp với '${uiState.searchQuery}'" else "No games match '${uiState.searchQuery}'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.filteredGames, key = { it.pkg }) { game ->
                    GameListItem(
                        game = game,
                        globalScale = uiState.globalScale,
                        lang = lang,
                        onLaunch = { viewModel.launchGame(context, game) },
                        onTogglePin = { viewModel.togglePin(game.pkg, context) },
                        onOpenScaleDialog = { viewModel.openGameScaleDialog(game) }
                    )
                }
            }
        }
    }

    // Modal Dialog: Game Profile Config (SSAA Scale + FPS Cap + Refresh Rate)
    uiState.selectedGameForScale?.let { game ->
        GameScaleLockDialog(
            game = game,
            currentScale = game.customScale,
            currentFpsCap = game.fpsCap,
            currentRefreshRate = game.refreshRate,
            globalScale = uiState.globalScale,
            lang = lang,
            onDismiss = { viewModel.closeGameScaleDialog() },
            onSaveConfig = { scale, fpsCap, refreshRate ->
                viewModel.setGameProfileConfig(game.pkg, scale, fpsCap, refreshRate, context)
            }
        )
    }

    // Modal Dialog: App Picker (with Smart Engine Detection filter)
    if (uiState.isAppPickerOpen) {
        AppPickerDialog(
            allApps = uiState.allInstalledApps,
            detectedGameCount = uiState.detectedGameCount,
            existingPkgs = uiState.games.map { it.pkg }.toSet(),
            lang = lang,
            onDismiss = { viewModel.closeAppPicker() },
            onAddGame = { pkg -> viewModel.addCustomGame(pkg, context) },
            onAddAllDetected = { viewModel.addAllDetectedGames(context) },
            onRemoveGame = { pkg -> viewModel.removeCustomGame(pkg, context) }
        )
    }
}

@Composable
private fun SsaaOptionCard(
    modifier: Modifier = Modifier,
    scale: Float,
    label: String,
    tag: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                // Radio Indicator
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF171E00))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                minLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GameListItem(
    game: GameProfileItem,
    globalScale: Float,
    lang: String,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenScaleDialog: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App info & launch click
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLaunch() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                DrawableImageView(
                    drawable = game.icon,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val isNativeLocked = Math.abs(game.customScale - 1.0f) < 0.01f
                        val scaleBadgeText = when {
                            isNativeLocked -> "1.00x"
                            game.customScale > 0f -> String.format(Locale.US, "%.2fx", game.customScale)
                            else -> String.format(Locale.US, "%.2fx", globalScale)
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (game.customScale > 0f) TechAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = scaleBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (game.customScale > 0f) TechAmber else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        if (game.fpsCap > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TechEmerald.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${game.fpsCap} FPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TechEmerald,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        if (game.refreshRate > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TechBlue.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${game.refreshRate}Hz",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TechBlue,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        if (game.detectedEngine.isNotEmpty() && game.fpsCap == 0 && game.refreshRate == 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = game.detectedEngine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Actions: Pin star & Sửa button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pin star icon
                IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (game.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (game.isPinned) TechAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Change Scale Button ("Sửa")
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenScaleDialog() }
                ) {
                    Text(
                        text = if (lang == "VI") "Sửa" else "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun getSupportedRefreshRates(context: Context): List<Int> {
    return try {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: return listOf(60, 90, 120)
        val currentMode = display.mode
        val allModes = display.supportedModes ?: return listOf(60, 90, 120)
        val sameResModes = allModes.filter {
            it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
        }
        val targetModes = if (sameResModes.isNotEmpty()) sameResModes else allModes.toList()
        val rates = targetModes
            .map { Math.round(it.refreshRate) }
            .filter { it >= 60 }
            .distinct()
            .sorted()
        if (rates.isNotEmpty()) rates else listOf(60)
    } catch (_: Exception) {
        listOf(60, 90, 120)
    }
}

@Composable
private fun GameScaleLockDialog(
    game: GameProfileItem,
    currentScale: Float,
    currentFpsCap: Int,
    currentRefreshRate: Int,
    globalScale: Float,
    lang: String,
    onDismiss: () -> Unit,
    onSaveConfig: (Float, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val supportedRates = remember(context) { getSupportedRefreshRates(context) }
    val maxHz = remember(supportedRates) { supportedRates.maxOrNull() ?: 60 }

    var selectedScale by remember { mutableStateOf(currentScale) }
    var selectedFps by remember { mutableStateOf(currentFpsCap) }
    var selectedHz by remember { mutableStateOf(currentRefreshRate) }

    val scaleOptions = listOf(
        Pair(0f, if (lang == "VI") "Mặc định (${globalScale}x)" else "Default (${globalScale}x)"),
        Pair(1.00f, "1.00x (Native)"),
        Pair(1.10f, "1.10x"),
        Pair(1.15f, "1.15x"),
        Pair(1.20f, "1.20x"),
        Pair(1.25f, "1.25x")
    )

    val fpsOptions = remember(maxHz) {
        listOf(0) + listOf(30, 45, 60, 90, 120, 144, 165).filter { it <= maxHz }
    }
    val hzOptions = remember(supportedRates) {
        listOf(0) + supportedRates
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (lang == "VI") "CẤU HÌNH RIÊNG CHO GAME" else "PER-GAME PROFILE CONFIG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. SSAA Scale Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (lang == "VI") "1. TỶ LỆ PHÂN GIẢI (SSAA)" else "1. RESOLUTION SCALE (SSAA)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    scaleOptions.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { (value, label) ->
                                val isSel = Math.abs(value - selectedScale) < 0.01f
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedScale = value }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. FPS Cap Section (Android 12+ GameManager)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (lang == "VI") "2. KHÓA FPS (ANDROID 12+ SHIZUKU)" else "2. FPS CAP (ANDROID 12+ SHIZUKU)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    fpsOptions.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { fps ->
                                val isSel = selectedFps == fps
                                val label = if (fps == 0) (if (lang == "VI") "Mặc định" else "Auto") else "$fps FPS"
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) TechEmerald else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) TechEmerald else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedFps = fps }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSel) Color(0xFF032215) else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (rowItems.size < 3) {
                                Spacer(modifier = Modifier.weight((3 - rowItems.size).toFloat()))
                            }
                        }
                    }
                }

                // 3. Refresh Rate Lock Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (lang == "VI") "3. TẦN SỐ QUÉT MÀN HÌNH (TỐI ĐA ${maxHz}HZ)" else "3. DISPLAY REFRESH RATE (MAX ${maxHz}HZ)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    hzOptions.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { hz ->
                                val isSel = selectedHz == hz
                                val label = if (hz == 0) (if (lang == "VI") "Mặc định" else "Auto") else "${hz}Hz"
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) TechBlue else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) TechBlue else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedHz = hz }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSel) Color(0xFF041D2E) else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (rowItems.size < 3) {
                                Spacer(modifier = Modifier.weight((3 - rowItems.size).toFloat()))
                            }
                        }
                    }
                    Text(
                        text = if (lang == "VI")
                            "Tự động khôi phục độ phân giải, FPS & Hz về gốc ngay khi thoát game."
                        else
                            "Automatically restores resolution, FPS cap & refresh rate when game exits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveConfig(selectedScale, selectedFps, selectedHz) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (lang == "VI") "Lưu cấu hình" else "Save Config", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "VI") "Hủy" else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    allApps: List<GameProfileItem>,
    detectedGameCount: Int,
    existingPkgs: Set<String>,
    lang: String,
    onDismiss: () -> Unit,
    onAddGame: (String) -> Unit,
    onAddAllDetected: () -> Unit,
    onRemoveGame: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showDetectedOnly by remember { mutableStateOf(detectedGameCount > 0) }

    val filtered = remember(query, allApps, showDetectedOnly) {
        val base = if (showDetectedOnly) allApps.filter { it.isDetectedGame } else allApps
        if (query.isBlank()) base else {
            val q = query.trim().lowercase(Locale.ROOT)
            base.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                it.pkg.lowercase(Locale.ROOT).contains(q) ||
                it.detectedEngine.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (lang == "VI") "QUÉT ENGINE GAME THÔNG MINH" else "SMART GAME ENGINE SCANNER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (lang == "VI") "Thêm game vào danh sách" else "Add games to your library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filter tabs: Detected Games vs All Apps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (showDetectedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable { showDetectedOnly = true }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (lang == "VI") "Game ($detectedGameCount)" else "Games ($detectedGameCount)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showDetectedOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (!showDetectedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable { showDetectedOnly = false }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (lang == "VI") "Tất cả (${allApps.size})" else "All (${allApps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!showDetectedOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick add all detected games button
                if (showDetectedOnly && detectedGameCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TechEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, TechEmerald.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onAddAllDetected() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = TechEmerald, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lang == "VI") "Thêm nhanh tất cả $detectedGameCount game phát hiện" else "Quick-add all $detectedGameCount detected games",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Search box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(if (lang == "VI") "Tìm game hoặc engine (Unity, Unreal)..." else "Search app or engine...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true
                )

                // List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        val isAdded = existingPkgs.contains(app.pkg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DrawableImageView(
                                    drawable = app.icon,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = app.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (app.detectedEngine.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(TechEmerald.copy(alpha = 0.18f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = app.detectedEngine,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TechEmerald,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = app.pkg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isAdded) {
                                Button(
                                    onClick = { onRemoveGame(app.pkg) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (lang == "VI") "Xóa" else "Remove", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Button(
                                    onClick = { onAddGame(app.pkg) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (lang == "VI") "+ Thêm" else "+ Add", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (lang == "VI") "Xong" else "Done", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun DrawableImageView(
    drawable: Drawable?,
    modifier: Modifier = Modifier
) {
    if (drawable == null) {
        Box(modifier = modifier)
        return
    }

    val bitmap = remember(drawable) {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(b)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            b
        }
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier
    )
}

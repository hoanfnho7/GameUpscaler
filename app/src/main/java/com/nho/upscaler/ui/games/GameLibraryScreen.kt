package com.nho.upscaler.ui.games

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
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
import com.nho.upscaler.ui.theme.DarkBorderSubtle
import com.nho.upscaler.ui.theme.DarkSurfaceHigh
import com.nho.upscaler.ui.theme.DarkSurfaceLow
import com.nho.upscaler.ui.theme.DarkSurfaceLowest
import com.nho.upscaler.ui.theme.TechAmber
import com.nho.upscaler.ui.theme.TechEmerald
import com.nho.upscaler.ui.theme.VoltLime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    viewModel: GameLibraryViewModel
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
                        text = "ĐỘ PHÂN GIẢI & ĐỒ HỌA GAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Kho Game & Khử răng cưa",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tùy chọn tỉ lệ SSAA chung hoặc khóa cấu hình riêng cho từng tựa game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Auto-launch HUD banner
            item {
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
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(VoltLime.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = VoltLime,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Tự động kích hoạt HUD",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Bật thanh đo nổi khi khởi chạy game từ app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.autoLaunchHud,
                            onCheckedChange = { viewModel.toggleAutoLaunchHud(it) },
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

            // Global SSAA Resolution Scale Group (4 options)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TỶ LỆ TĂNG PHÂN GIẢI (SSAA SCALE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Mặc định: ${uiState.globalScale}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = VoltLime,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 4 Options Grid/Column
                    SsaaOptionCard(
                        scale = 1.10f,
                        label = "1.10x",
                        tag = "Nhẹ",
                        desc = "Tiết kiệm pin nhất, nhiệt độ mát mẻ (+2% pin).",
                        icon = Icons.Default.PhotoSizeSelectSmall,
                        isSelected = Math.abs(uiState.globalScale - 1.10f) < 0.01f,
                        onClick = { viewModel.setGlobalScale(1.10f, context) }
                    )

                    SsaaOptionCard(
                        scale = 1.15f,
                        label = "1.15x",
                        tag = "Cân bằng (Khuyên dùng)",
                        desc = "Cân bằng giữa hiệu năng đồ họa và độ nét (+4% pin).",
                        icon = Icons.Default.Tune,
                        isSelected = Math.abs(uiState.globalScale - 1.15f) < 0.01f,
                        onClick = { viewModel.setGlobalScale(1.15f, context) }
                    )

                    SsaaOptionCard(
                        scale = 1.20f,
                        label = "1.20x",
                        tag = "Sắc nét",
                        desc = "Hình ảnh rõ nét, khử răng cưa góc cạnh chi tiết (+6% pin).",
                        icon = Icons.Default.HighQuality,
                        isSelected = Math.abs(uiState.globalScale - 1.20f) < 0.01f,
                        onClick = { viewModel.setGlobalScale(1.20f, context) }
                    )

                    SsaaOptionCard(
                        scale = 1.25f,
                        label = "1.25x",
                        tag = "Tối đa an toàn",
                        desc = "Chuẩn Esports, góc cạnh mượt mà tuyệt đối (+8% pin).",
                        icon = Icons.Default.Speed,
                        isSelected = Math.abs(uiState.globalScale - 1.25f) < 0.01f,
                        onClick = { viewModel.setGlobalScale(1.25f, context) }
                    )
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
                            text = "GAME CỦA TÔI",
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
                                text = "${uiState.games.size} Sẵn sàng",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Add game button
                    TextButton(
                        onClick = { viewModel.openAppPicker() },
                        colors = ButtonDefaults.textButtonColors(contentColor = VoltLime)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Thêm Game",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Search input field
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = {
                        Text(
                            text = "Tìm kiếm game hoặc gói ứng dụng...",
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
                        focusedContainerColor = DarkSurfaceLow,
                        unfocusedContainerColor = DarkSurfaceLow,
                        focusedBorderColor = VoltLime,
                        unfocusedBorderColor = DarkBorderSubtle,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true
                )
            }

            // Games List
            if (uiState.filteredGames.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
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
                                text = "Không tìm thấy game nào phù hợp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.openAppPicker() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VoltLime,
                                    contentColor = Color(0xFF171E00)
                                )
                            ) {
                                Text(
                                    text = "+ Thêm game từ máy",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                items(uiState.filteredGames, key = { it.pkg }) { game ->
                    GameListItem(
                        game = game,
                        globalScale = uiState.globalScale,
                        onLaunch = { viewModel.launchGame(context, game) },
                        onTogglePin = { viewModel.togglePin(game.pkg, context) },
                        onOpenScaleDialog = { viewModel.openGameScaleDialog(game) },
                        onToggleEnabled = { viewModel.toggleGameEnabled(game.pkg, it) }
                    )
                }
            }
        }
    }

    // Modal Dialog: Game Custom Scale Lock
    uiState.selectedGameForScale?.let { game ->
        GameScaleLockDialog(
            game = game,
            currentScale = game.customScale,
            globalScale = uiState.globalScale,
            onDismiss = { viewModel.closeGameScaleDialog() },
            onSelectScale = { scale ->
                viewModel.setGameCustomScale(game.pkg, scale, context)
            }
        )
    }

    // Modal Dialog: App Picker
    if (uiState.isAppPickerOpen) {
        AppPickerDialog(
            allApps = uiState.allInstalledApps,
            existingPkgs = uiState.games.map { it.pkg }.toSet(),
            onDismiss = { viewModel.closeAppPicker() },
            onAddGame = { pkg -> viewModel.addCustomGame(pkg, context) },
            onRemoveGame = { pkg -> viewModel.removeCustomGame(pkg, context) }
        )
    }
}

@Composable
private fun SsaaOptionCard(
    scale: Float,
    label: String,
    tag: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceHigh else DarkSurfaceLow
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) VoltLime.copy(alpha = 0.5f) else DarkBorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) VoltLime.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) VoltLime else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isSelected) VoltLime else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) VoltLime else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFF171E00) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Radio Indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) VoltLime else MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        if (isSelected) VoltLime else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    ),
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

@Composable
private fun GameListItem(
    game: GameProfileItem,
    globalScale: Float,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenScaleDialog: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (game.customScale > 0f) {
                                String.format(Locale.US, "%.2fx SSAA (Khóa)", game.customScale)
                            } else {
                                String.format(Locale.US, "%.2fx SSAA (Mặc định)", globalScale)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (game.customScale > 0f) TechAmber else VoltLime,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Actions: Pin star, Change Scale, Enable Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pin star icon
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (game.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (game.isPinned) TechAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Change Scale Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenScaleDialog() }
                ) {
                    Text(
                        text = "Đổi",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoltLime,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Switch toggle
                Switch(
                    checked = game.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF171E00),
                        checkedTrackColor = VoltLime,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GameScaleLockDialog(
    game: GameProfileItem,
    currentScale: Float,
    globalScale: Float,
    onDismiss: () -> Unit,
    onSelectScale: (Float) -> Unit
) {
    var selectedVal by remember { mutableStateOf(currentScale) }

    val options = listOf(
        Pair(0f, "Mặc định chung (Theo tỉ lệ toàn cục ${globalScale}x)"),
        Pair(1.10f, "Khóa 1.10x (Tiết kiệm pin, nhẹ nhàng)"),
        Pair(1.15f, "Khóa 1.15x (Cân bằng độ nét và nhiệt độ)"),
        Pair(1.20f, "Khóa 1.20x (Sắc nét, khử răng cưa cao)"),
        Pair(1.25f, "Khóa 1.25x (Chuẩn Esports, tối đa GPU)")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "KHÓA TỶ LỆ RIÊNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = TechAmber
                )
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    val isChecked = Math.abs(value - selectedVal) < 0.01f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedVal = value }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = isChecked,
                            onClick = { selectedVal = value },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VoltLime,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isChecked) VoltLime else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectScale(selectedVal) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltLime,
                    contentColor = Color(0xFF171E00)
                )
            ) {
                Text("Lưu cấu hình", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = DarkSurfaceLow,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    allApps: List<GameProfileItem>,
    existingPkgs: Set<String>,
    onDismiss: () -> Unit,
    onAddGame: (String) -> Unit,
    onRemoveGame: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, allApps) {
        if (query.isBlank()) allApps else {
            val q = query.trim().lowercase(Locale.ROOT)
            allApps.filter { it.name.lowercase(Locale.ROOT).contains(q) || it.pkg.lowercase(Locale.ROOT).contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CHỌN ỨNG DỤNG / GAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoltLime
                    )
                    Text(
                        text = "Thêm vào danh sách SSAA",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Tìm ứng dụng...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceLowest,
                        unfocusedContainerColor = DarkSurfaceLowest,
                        focusedBorderColor = VoltLime,
                        unfocusedBorderColor = DarkBorderSubtle,
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
                                .background(DarkSurfaceLowest)
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
                                        .background(DarkSurfaceHigh)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = app.pkg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (isAdded) {
                                Button(
                                    onClick = { onRemoveGame(app.pkg) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkSurfaceHigh,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Xóa", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Button(
                                    onClick = { onAddGame(app.pkg) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VoltLime,
                                        contentColor = Color(0xFF171E00)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Thêm", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                    containerColor = VoltLime,
                    contentColor = Color(0xFF171E00)
                )
            ) {
                Text("Xong", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceLow,
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

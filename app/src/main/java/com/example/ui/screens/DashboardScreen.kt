package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SyncConfig
import com.example.data.model.TelecomRecord
import com.example.ui.theme.CarrierColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun DashboardScreen(
    recordsState: UiState<List<TelecomRecord>>,
    selectedCarrier: String,
    syncConfig: SyncConfig,
    onSelectCarrier: (String) -> Unit,
    onImportCsvClick: () -> Unit,
    onLoadDemoDataset: () -> Unit,
    onQuickSyncClick: () -> Unit,
    onQuickZaloClick: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allRecords = (recordsState as? UiState.Success)?.data.orEmpty()
    val filtered = if (selectedCarrier == "Tất cả") {
        allRecords
    } else {
        allRecords.filter { it.carrier == selectedCarrier }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCommandCard(
                syncConfig = syncConfig,
                totalRecords = allRecords.size,
                onQuickSyncClick = onQuickSyncClick,
                onQuickZaloClick = onQuickZaloClick
            )
        }

        item {
            val carriers = listOf("Tất cả", "Viettel", "VinaPhone", "MobiFone")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(carriers) { carrier ->
                    FilterChip(
                        selected = selectedCarrier == carrier,
                        onClick = { onSelectCarrier(carrier) },
                        label = { Text(carrier) },
                        modifier = Modifier.testTag("dashboard_chip_$carrier")
                    )
                }
            }
        }

        when (recordsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = CarrierColors.ErrorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = recordsState.message,
                            color = CarrierColors.ErrorRed,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (allRecords.isEmpty()) {
                    item {
                        EmptyDashboardCard(
                            driveFolderPath = syncConfig.driveFolderPath,
                            onImportCsvClick = onImportCsvClick,
                            onLoadDemoDataset = onLoadDemoDataset
                        )
                    }
                } else {
                    item {
                        KpiGridSection(records = filtered)
                    }

                    item {
                        CarrierComparisonChartCard(records = allRecords)
                    }

                    item {
                        MarketShareDonutCard(records = allRecords)
                    }

                    item {
                        RecentHighlightsCard(
                            records = filtered.take(4),
                            onViewAllClick = onNavigateToDatabase
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCommandCard(
    syncConfig: SyncConfig,
    totalRecords: Int,
    onQuickSyncClick: () -> Unit,
    onQuickZaloClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0B192C), Color(0xFF15365E), Color(0xFF005F8F))
                )
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncConfig.driveFolderPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBAE6FD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$totalRecords bản ghi",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Trung tâm CSDL Nhà mạng",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Đồng bộ gần nhất: ${syncConfig.lastSyncStatus} (${syncConfig.lastSyncTimeText})",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onQuickSyncClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B4D8),
                        contentColor = Color(0xFF041C32)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_sync_button")
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Đồng bộ Drive", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onQuickZaloClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_zalo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gửi Zalo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboardCard(
    driveFolderPath: String,
    onImportCsvClick: () -> Unit,
    onLoadDemoDataset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Dataset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = "Chưa có dữ liệu nhà mạng",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Nhập tệp CSV từ thư mục $driveFolderPath (hoặc bộ nhớ máy), hoặc nạp dữ liệu mẫu thử nghiệm để xem biểu đồ, bản đồ và phân tích AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onImportCsvClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_import_csv_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chọn tệp CSV từ Google Drive / Thiết bị")
            }
            OutlinedButton(
                onClick = onLoadDemoDataset,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_load_demo_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Nạp bộ dữ liệu mẫu thử nghiệm (Viettel/Vina/Mobi)")
            }
        }
    }
}

@Composable
private fun KpiGridSection(records: List<TelecomRecord>) {
    val totalSubs = records.sumOf { it.subscribers }
    val totalRev = records.sumOf { it.revenueBillionVnd }
    val avgCov = if (records.isNotEmpty()) records.map { it.coverage5gPercent }.average() else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Tổng thuê bao",
                value = formatCompactSubs(totalSubs),
                icon = Icons.Default.Groups,
                accent = Color(0xFF0077B6),
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "Doanh thu (tỷ)",
                value = String.format(Locale.US, "%.1f tỷ", totalRev),
                icon = Icons.Default.AttachMoney,
                accent = Color(0xFF0F766E),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Trạm / Điểm DL",
                value = records.size.toString(),
                icon = Icons.Default.CellTower,
                accent = Color(0xFFD90429),
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                title = "Phủ sóng 5G TB",
                value = String.format(Locale.US, "%.1f%%", avgCov),
                icon = Icons.Default.SignalCellularAlt,
                accent = Color(0xFFEA580C),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CarrierComparisonChartCard(records: List<TelecomRecord>) {
    val carriers = listOf("Viettel", "VinaPhone", "MobiFone")
    val subsByCarrier = carriers.map { c -> records.filter { it.carrier == c }.sumOf { it.subscribers } }
    val revByCarrier = carriers.map { c -> records.filter { it.carrier == c }.sumOf { it.revenueBillionVnd } }
    val maxSubs = (subsByCarrier.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    val maxRev = (revByCarrier.maxOrNull() ?: 1.0).coerceAtLeast(1.0).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "So sánh Viettel • VinaPhone • MobiFone",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Cột đậm: Thuê bao | Cột nhạt: Doanh thu (tỷ VNĐ)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                val groupWidth = size.width / carriers.size
                val barWidth = groupWidth * 0.24f
                val bottomPadding = 12f
                val usableHeight = size.height - bottomPadding

                carriers.forEachIndexed { idx, carrier ->
                    val color = CarrierColors.colorFor(carrier)
                    val groupCenter = groupWidth * idx + groupWidth / 2f

                    val subsRatio = subsByCarrier[idx].toFloat() / maxSubs
                    val subsHeight = (usableHeight * subsRatio).coerceAtLeast(8f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(groupCenter - barWidth - 6f, usableHeight - subsHeight),
                        size = Size(barWidth, subsHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )

                    val revRatio = revByCarrier[idx].toFloat() / maxRev
                    val revHeight = (usableHeight * revRatio).coerceAtLeast(8f)
                    drawRoundRect(
                        color = color.copy(alpha = 0.42f),
                        topLeft = Offset(groupCenter + 6f, usableHeight - revHeight),
                        size = Size(barWidth, revHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                carriers.forEachIndexed { idx, carrier ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CarrierColors.colorFor(carrier))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(carrier, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            text = "${formatCompactSubs(subsByCarrier[idx])} • ${String.format(Locale.US, "%.0f tỷ", revByCarrier[idx])}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketShareDonutCard(records: List<TelecomRecord>) {
    val carriers = listOf("Viettel", "VinaPhone", "MobiFone")
    val totalSubs = records.sumOf { it.subscribers }.coerceAtLeast(1L).toFloat()
    val shares = carriers.map { c ->
        val s = records.filter { it.carrier == c }.sumOf { it.subscribers }
        c to (s / totalSubs)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Canvas(modifier = Modifier.size(108.dp)) {
                var startAngle = -90f
                val strokeWidth = 22f
                shares.forEach { (carrier, ratio) ->
                    val sweep = (ratio * 360f).coerceAtLeast(4f)
                    drawArc(
                        color = CarrierColors.colorFor(carrier),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tỷ trọng thuê bao theo nhà mạng",
                    style = MaterialTheme.typography.titleSmall
                )
                shares.forEach { (carrier, ratio) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CarrierColors.colorFor(carrier))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(carrier, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = String.format(Locale.US, "%.1f%%", ratio * 100f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentHighlightsCard(
    records: List<TelecomRecord>,
    onViewAllClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                Text(
                    text = "Bản ghi mới cập nhật",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Xem tất cả",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .clickable { onViewAllClick() }
                        .padding(4.dp)
                        .testTag("dashboard_view_all_records")
                )
            }

            records.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = CarrierColors.containerFor(r.carrier),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = r.carrier,
                                    color = CarrierColors.colorFor(r.carrier),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = r.province,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${r.stationCode} • Gói ${r.popularPackage} • ${r.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCompactSubs(r.subscribers),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", r.coverage5gPercent)}% 5G",
                            style = MaterialTheme.typography.labelSmall,
                            color = CarrierColors.SuccessGreen
                        )
                    }
                }
            }
        }
    }
}

fun formatCompactSubs(subs: Long): String {
    return when {
        subs >= 1_000_000L -> String.format(Locale.US, "%.2fM", subs / 1_000_000.0)
        subs >= 1_000L -> String.format(Locale.US, "%.1fK", subs / 1_000.0)
        else -> subs.toString()
    }
}

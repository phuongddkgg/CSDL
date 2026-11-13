package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TelecomRecord
import com.example.data.model.VietnamProvinceLookup
import com.example.ui.theme.CarrierColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.SortOption
import java.util.Locale

@Composable
fun DatabaseScreen(
    records: List<TelecomRecord>,
    selectedCarrier: String,
    selectedCategory: String,
    searchQuery: String,
    sortOption: SortOption,
    onSelectCarrier: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (SortOption) -> Unit,
    onSaveRecord: (TelecomRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onImportCsvClick: () -> Unit,
    onExportCsvClick: (List<TelecomRecord>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingRecord by remember { mutableStateOf<TelecomRecord?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRecord = TelecomRecord(
                        carrier = if (selectedCarrier == "Tất cả") "Viettel" else selectedCarrier,
                        province = "Hà Nội",
                        region = "Miền Bắc",
                        category = if (selectedCategory == "Tất cả") "Thuê bao & Doanh thu" else selectedCategory,
                        stationCode = "VTL-NEW-01",
                        subscribers = 1_000_000L,
                        revenueBillionVnd = 300.0,
                        coverage5gPercent = 90.0,
                        popularPackage = "5G135"
                    )
                },
                modifier = Modifier.testTag("add_record_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm bản ghi CSDL")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Tìm theo tỉnh, mã trạm BTS, gói cước...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("database_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Tất cả", "Viettel", "VinaPhone", "MobiFone")) { carrier ->
                    FilterChip(
                        selected = selectedCarrier == carrier,
                        onClick = { onSelectCarrier(carrier) },
                        label = { Text(carrier) }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Tất cả") + VietnamProvinceLookup.validCategories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImportCsvClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("db_import_csv_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhập CSV", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { onExportCsvClick(records) },
                        enabled = records.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("db_export_csv_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xuất CSV", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Text("Sắp xếp: ${sortOption.label}", style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortOptionChange(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không có bản ghi phù hợp với bộ lọc hiện tại.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id.ifBlank { "${it.carrier}_${it.stationCode}_${it.province}" } }) { record ->
                        TelecomRecordCard(
                            record = record,
                            onEdit = { editingRecord = record },
                            onDelete = { if (record.id.isNotBlank()) onDeleteRecord(record.id) }
                        )
                    }
                }
            }
        }
    }

    editingRecord?.let { initial ->
        RecordEditorDialog(
            initial = initial,
            onDismiss = { editingRecord = null },
            onSave = { updated ->
                onSaveRecord(updated)
                editingRecord = null
            }
        )
    }
}

@Composable
private fun TelecomRecordCard(
    record: TelecomRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val brandColor = CarrierColors.colorFor(record.carrier)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("record_card_${record.stationCode}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brandColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CarrierColors.containerFor(record.carrier),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = record.carrier,
                                color = brandColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = record.province,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = record.stationCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${record.region} • ${record.category} • Gói: ${record.popularPackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricBadge("Thuê bao", formatCompactSubs(record.subscribers))
                    MetricBadge("Doanh thu", String.format(Locale.US, "%.1f tỷ", record.revenueBillionVnd))
                    MetricBadge("Phủ 5G", String.format(Locale.US, "%.1f%%", record.coverage5gPercent))
                }

                if (record.notes.isNotBlank()) {
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nguồn: ${record.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa bản ghi", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Xóa bản ghi",
                                tint = CarrierColors.ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RecordEditorDialog(
    initial: TelecomRecord,
    onDismiss: () -> Unit,
    onSave: (TelecomRecord) -> Unit
) {
    var carrier by remember { mutableStateOf(initial.carrier) }
    var province by remember { mutableStateOf(initial.province) }
    var category by remember { mutableStateOf(initial.category) }
    var stationCode by remember { mutableStateOf(initial.stationCode) }
    var subscribersText by remember { mutableStateOf(initial.subscribers.toString()) }
    var revenueText by remember { mutableStateOf(initial.revenueBillionVnd.toString()) }
    var coverageText by remember { mutableStateOf(initial.coverage5gPercent.toString()) }
    var popularPackage by remember { mutableStateOf(initial.popularPackage) }
    var notes by remember { mutableStateOf(initial.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial.id.isBlank()) "Thêm bản ghi CSDL" else "Cập nhật bản ghi")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Nhà mạng", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VietnamProvinceLookup.validCarriers.forEach { c ->
                        FilterChip(
                            selected = carrier == c,
                            onClick = { carrier = c },
                            label = { Text(c) }
                        )
                    }
                }

                OutlinedTextField(
                    value = province,
                    onValueChange = { province = it },
                    label = { Text("Tỉnh / Thành phố") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Danh mục", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VietnamProvinceLookup.validCategories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = stationCode,
                    onValueChange = { stationCode = it },
                    label = { Text("Mã trạm / Mã nút") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = subscribersText,
                    onValueChange = { subscribersText = it },
                    label = { Text("Số lượng thuê bao") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = revenueText,
                        onValueChange = { revenueText = it },
                        label = { Text("Doanh thu (tỷ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = coverageText,
                        onValueChange = { coverageText = it },
                        label = { Text("Phủ 5G (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = popularPackage,
                    onValueChange = { popularPackage = it },
                    label = { Text("Gói cước phổ biến") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Ghi chú") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val geo = VietnamProvinceLookup.findOrDefault(province)
                    onSave(
                        initial.copy(
                            carrier = carrier,
                            province = province.ifBlank { "Hà Nội" },
                            region = geo.region,
                            category = category,
                            stationCode = stationCode.ifBlank { "${carrier.take(3).uppercase()}-01" },
                            subscribers = subscribersText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                            revenueBillionVnd = revenueText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            coverage5gPercent = coverageText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 85.0,
                            popularPackage = popularPackage,
                            latitude = geo.latitude,
                            longitude = geo.longitude,
                            source = if (initial.id.isBlank()) "Nhập thủ công" else initial.source,
                            notes = notes
                        )
                    )
                },
                modifier = Modifier.testTag("save_record_dialog_button")
            ) {
                Text("Lưu CSDL")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SyncConfig
import com.example.data.model.SyncLog
import com.example.data.model.TelecomRecord
import com.example.data.model.ZaloReport
import com.example.data.sync.DailySyncWorker
import com.example.ui.theme.CarrierColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SyncAndZaloScreen(
    syncConfig: SyncConfig,
    records: List<TelecomRecord>,
    syncLogs: List<SyncLog>,
    zaloReports: List<ZaloReport>,
    isBusy: Boolean,
    onSaveConfig: (SyncConfig) -> Unit,
    onSyncNow: (SyncConfig) -> Unit,
    onPickLocalFile: (String) -> Unit,
    onShareToZaloApp: (targetLabel: String, summaryText: String) -> Unit,
    onSendViaWebhook: (SyncConfig, String) -> Unit,
    onCopySummary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var driveFolderPath by remember(syncConfig.driveFolderPath) { mutableStateOf(syncConfig.driveFolderPath) }
    var driveCsvUrl by remember(syncConfig.driveCsvUrl) { mutableStateOf(syncConfig.driveCsvUrl) }
    var autoSyncEnabled by remember(syncConfig.autoSyncEnabled) { mutableStateOf(syncConfig.autoSyncEnabled) }
    var syncHour by remember(syncConfig.syncHour) { mutableFloatStateOf(syncConfig.syncHour.toFloat()) }
    var wifiOnly by remember(syncConfig.wifiOnly) { mutableStateOf(syncConfig.wifiOnly) }
    var zaloTargetLabel by remember(syncConfig.zaloTargetLabel) { mutableStateOf(syncConfig.zaloTargetLabel) }
    var zaloWebhookUrl by remember(syncConfig.zaloWebhookUrl) { mutableStateOf(syncConfig.zaloWebhookUrl) }

    val nowText = remember(records.size) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    }
    var reportDraft by remember {
        mutableStateOf(DailySyncWorker.buildDailyZaloSummary(driveFolderPath, nowText, records))
    }
    LaunchedEffect(records.size, driveFolderPath) {
        reportDraft = DailySyncWorker.buildDailyZaloSummary(driveFolderPath, nowText, records)
    }

    val clipboard = LocalClipboardManager.current

    fun currentConfigSnapshot(): SyncConfig = syncConfig.copy(
        driveFolderPath = driveFolderPath.ifBlank { "My Drive\\Viettel\\CSDL" },
        driveCsvUrl = driveCsvUrl.trim(),
        autoSyncEnabled = autoSyncEnabled,
        syncHour = syncHour.roundToInt().toLong().coerceIn(0L, 23L),
        wifiOnly = wifiOnly,
        zaloTargetLabel = zaloTargetLabel.ifBlank { "Nhóm Điều hành Mạng lưới" },
        zaloWebhookUrl = zaloWebhookUrl.trim()
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đồng bộ Google Drive hàng ngày", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedTextField(
                        value = driveFolderPath,
                        onValueChange = { driveFolderPath = it },
                        label = { Text("Thư mục Google Drive") },
                        supportingText = { Text("Mặc định: My Drive\\Viettel\\CSDL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = driveCsvUrl,
                        onValueChange = { driveCsvUrl = it },
                        label = { Text("Liên kết Google Sheets / Drive CSV") },
                        placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_url_input")
                    )

                    OutlinedButton(
                        onClick = { onPickLocalFile(driveFolderPath) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_drive_file_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chọn tệp CSV trực tiếp từ Google Drive / Thiết bị")
                    }

                    if (syncConfig.localSafUri.isNotBlank()) {
                        Text(
                            text = "Tệp đã liên kết: ${syncConfig.localSafUri.takeLast(42)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CarrierColors.SuccessGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Tự động đồng bộ mỗi ngày", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Khung giờ: ${syncHour.roundToInt()}:00 (WorkManager 24h)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it },
                            modifier = Modifier.testTag("auto_sync_switch")
                        )
                    }

                    if (autoSyncEnabled) {
                        Slider(
                            value = syncHour,
                            onValueChange = { syncHour = it },
                            valueRange = 0f..23f,
                            steps = 22
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chỉ đồng bộ qua Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveConfig(currentConfigSnapshot()) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_sync_config_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lưu lịch")
                        }
                        Button(
                            onClick = { onSyncNow(currentConfigSnapshot()) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_now_button")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đồng bộ ngay")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gửi báo cáo vào mục Zalo", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedTextField(
                        value = zaloTargetLabel,
                        onValueChange = { zaloTargetLabel = it },
                        label = { Text("Tên mục / Nhóm Zalo nhận tin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = zaloWebhookUrl,
                        onValueChange = { zaloWebhookUrl = it },
                        label = { Text("Zalo OA / Webhook URL (tùy chọn tự động)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reportDraft,
                        onValueChange = { reportDraft = it },
                        label = { Text("Nội dung báo cáo sẽ gửi") },
                        minLines = 5,
                        maxLines = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zalo_report_draft_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onShareToZaloApp(zaloTargetLabel, reportDraft) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_zalo_app_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mở Zalo gửi")
                        }

                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(reportDraft))
                                onCopySummary(reportDraft)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao chép")
                        }
                    }

                    if (zaloWebhookUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onSendViaWebhook(currentConfigSnapshot(), reportDraft) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Gửi tự động qua Zalo Webhook")
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nhật ký đồng bộ & Gửi Zalo", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (syncLogs.isEmpty() && zaloReports.isEmpty()) {
            item {
                Text(
                    text = "Chưa có lịch sử đồng bộ hoặc gửi báo cáo nào.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(syncLogs.take(8)) { log ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Drive • ${log.triggeredBy}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = log.status,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (log.status == "Thành công") CarrierColors.SuccessGreen else CarrierColors.ErrorRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${log.sourcePath} (${log.recordsProcessed} dòng) — ${log.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(zaloReports.take(6)) { rep ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${rep.channel} -> ${rep.targetName}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = rep.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = CarrierColors.VinaPhone
                            )
                        }
                        Text(
                            text = rep.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

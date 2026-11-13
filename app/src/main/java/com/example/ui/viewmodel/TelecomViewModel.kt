package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.TelecomAiRepository
import com.example.data.model.SyncConfig
import com.example.data.model.SyncLog
import com.example.data.model.TelecomRecord
import com.example.data.model.ZaloReport
import com.example.data.repository.TelecomRepository
import com.example.data.sync.CsvTelecomParser
import com.example.data.sync.DailySyncWorker
import com.example.data.sync.DriveAndZaloClient
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

enum class SortOption(val label: String) {
    RECENT("Mới nhất"),
    SUBSCRIBERS_DESC("Thuê bao ↓"),
    REVENUE_DESC("Doanh thu ↓"),
    COVERAGE_DESC("Phủ sóng 5G ↓")
}

class TelecomViewModel(
    private val repository: TelecomRepository,
    private val aiRepository: TelecomAiRepository,
    private val currentUserId: String
) : ViewModel() {

    val recordsState: StateFlow<UiState<List<TelecomRecord>>> =
        repository.observeRecords(currentUserId)
            .map<List<TelecomRecord>, UiState<List<TelecomRecord>>> { UiState.Success(it) }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                Log.w(TAG, "Error observing telecom_records", root)
                emit(UiState.Error(root.localizedMessage ?: "Không thể tải CSDL từ Firestore."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val syncConfig: StateFlow<SyncConfig> =
        repository.observeSyncConfig(currentUserId)
            .map { it ?: SyncConfig() }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing sync_config", error)
                emit(SyncConfig())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = SyncConfig()
            )

    val syncLogs: StateFlow<List<SyncLog>> =
        repository.observeSyncLogs(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing sync_logs", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val zaloReports: StateFlow<List<ZaloReport>> =
        repository.observeZaloReports(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing zalo_reports", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    private val _selectedCarrier = MutableStateFlow("Tất cả")
    val selectedCarrier: StateFlow<String> = _selectedCarrier.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Tất cả")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val filteredRecords: StateFlow<List<TelecomRecord>> = combine(
        recordsState,
        _selectedCarrier,
        _selectedCategory,
        _searchQuery,
        _sortOption
    ) { state, carrier, category, query, sort ->
        val base = (state as? UiState.Success)?.data.orEmpty()
        val q = query.trim().lowercase(Locale.ROOT)
        val filtered = base.filter { r ->
            val matchCarrier = carrier == "Tất cả" || r.carrier == carrier
            val matchCat = category == "Tất cả" || r.category == category
            val matchQuery = q.isEmpty() ||
                r.province.lowercase(Locale.ROOT).contains(q) ||
                r.stationCode.lowercase(Locale.ROOT).contains(q) ||
                r.popularPackage.lowercase(Locale.ROOT).contains(q) ||
                r.notes.lowercase(Locale.ROOT).contains(q) ||
                r.carrier.lowercase(Locale.ROOT).contains(q)
            matchCarrier && matchCat && matchQuery
        }
        when (sort) {
            SortOption.RECENT -> filtered
            SortOption.SUBSCRIBERS_DESC -> filtered.sortedByDescending { it.subscribers }
            SortOption.REVENUE_DESC -> filtered.sortedByDescending { it.revenueBillionVnd }
            SortOption.COVERAGE_DESC -> filtered.sortedByDescending { it.coverage5gPercent }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var aiJob: Job? = null

    fun setCarrierFilter(carrier: String) {
        _selectedCarrier.value = carrier
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun saveRecord(record: TelecomRecord) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveRecord(record)
            _isBusy.value = false
            _statusBanner.value = if (res.isSuccess) {
                "Đã lưu bản ghi ${record.carrier} - ${record.province}."
            } else {
                "Lỗi lưu bản ghi: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            val res = repository.deleteRecord(recordId)
            _statusBanner.value = if (res.isSuccess) {
                "Đã xóa bản ghi khỏi CSDL."
            } else {
                "Không thể xóa: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun importFromSafUri(context: Context, uriString: String, folderPath: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val readRes = DriveAndZaloClient.readCsvFromUri(context, uriString)
            readRes.fold(
                onSuccess = { text ->
                    val parsed = CsvTelecomParser.parseCsvText(text, "File Drive/Thiết bị")
                    if (parsed.isEmpty()) {
                        _statusBanner.value = "Tệp không có dòng dữ liệu CSV hợp lệ."
                    } else {
                        val imported = repository.batchImportRecords(parsed).getOrDefault(0)
                        val nowText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                        val cfg = syncConfig.value
                        repository.saveSyncConfig(
                            cfg.copy(
                                driveFolderPath = folderPath.ifBlank { cfg.driveFolderPath },
                                localSafUri = uriString,
                                lastSyncStatus = "Nhập tệp ($imported dòng)",
                                lastSyncTimeText = nowText
                            )
                        )
                        repository.addSyncLog(
                            SyncLog(
                                sourcePath = folderPath.ifBlank { cfg.driveFolderPath },
                                recordsProcessed = imported.toLong(),
                                status = "Thành công",
                                message = "Nhập $imported bản ghi từ tệp CSV đã chọn.",
                                triggeredBy = "Thủ công"
                            )
                        )
                        _statusBanner.value = "Đã nhập thành công $imported bản ghi từ tệp CSV."
                    }
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Không thể đọc tệp CSV."
                }
            )
            _isBusy.value = false
        }
    }

    fun loadDemoDataset() {
        viewModelScope.launch {
            _isBusy.value = true
            val samples = listOf(
                TelecomRecord(
                    carrier = "Viettel", province = "Hà Nội", region = "Miền Bắc",
                    category = "Thuê bao & Doanh thu", stationCode = "VTL-HN-001",
                    subscribers = 4_850_000L, revenueBillionVnd = 1420.5, coverage5gPercent = 95.2,
                    popularPackage = "5G135", latitude = 21.0285, longitude = 105.8542,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Cụm trung tâm Hoàn Kiếm - Cầu Giấy"
                ),
                TelecomRecord(
                    carrier = "VinaPhone", province = "Hà Nội", region = "Miền Bắc",
                    category = "Thuê bao & Doanh thu", stationCode = "VNP-HN-014",
                    subscribers = 2_610_000L, revenueBillionVnd = 780.0, coverage5gPercent = 88.4,
                    popularPackage = "VD90", latitude = 21.0450, longitude = 105.8120,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Khu vực Tây Hồ - Ba Đình"
                ),
                TelecomRecord(
                    carrier = "MobiFone", province = "TP. Hồ Chí Minh", region = "Miền Nam",
                    category = "Gói cước phổ biến", stationCode = "MBF-HCM-008",
                    subscribers = 3_120_000L, revenueBillionVnd = 965.4, coverage5gPercent = 91.0,
                    popularPackage = "MXH120", latitude = 10.8231, longitude = 106.6297,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Quận 1 - Thủ Đức lưu lượng cao"
                ),
                TelecomRecord(
                    carrier = "Viettel", province = "TP. Hồ Chí Minh", region = "Miền Nam",
                    category = "Trạm BTS & Sóng", stationCode = "VTL-HCM-022",
                    subscribers = 5_640_000L, revenueBillionVnd = 1690.2, coverage5gPercent = 96.5,
                    popularPackage = "V120B", latitude = 10.7769, longitude = 106.7009,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Cụm trạm 5G SA trung tâm"
                ),
                TelecomRecord(
                    carrier = "VinaPhone", province = "Đà Nẵng", region = "Miền Trung",
                    category = "Trạm BTS & Sóng", stationCode = "VNP-DN-005",
                    subscribers = 980_000L, revenueBillionVnd = 295.0, coverage5gPercent = 89.6,
                    popularPackage = "D159V", latitude = 16.0544, longitude = 108.2022,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Cụm ven biển Sơn Trà - Hải Châu"
                ),
                TelecomRecord(
                    carrier = "Viettel", province = "Kiên Giang", region = "Miền Nam",
                    category = "Trạm BTS & Sóng", stationCode = "VTL-KG-009",
                    subscribers = 1_180_000L, revenueBillionVnd = 335.8, coverage5gPercent = 86.2,
                    popularPackage = "SD90", latitude = 10.0125, longitude = 105.0809,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Rạch Giá - Phú Quốc tuyến du lịch"
                ),
                TelecomRecord(
                    carrier = "MobiFone", province = "Cần Thơ", region = "Miền Nam",
                    category = "Thuê bao & Doanh thu", stationCode = "MBF-CT-003",
                    subscribers = 845_000L, revenueBillionVnd = 248.0, coverage5gPercent = 84.5,
                    popularPackage = "C90N", latitude = 10.0452, longitude = 105.7469,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Trung tâm ĐBSCL"
                ),
                TelecomRecord(
                    carrier = "VinaPhone", province = "Hải Phòng", region = "Miền Bắc",
                    category = "Gói cước phổ biến", stationCode = "VNP-HP-011",
                    subscribers = 1_040_000L, revenueBillionVnd = 312.4, coverage5gPercent = 87.3,
                    popularPackage = "YOLO100M", latitude = 20.8449, longitude = 106.6881,
                    source = "Dữ liệu mẫu thử nghiệm", notes = "Khu công nghiệp cảng biển"
                )
            )
            val res = repository.batchImportRecords(samples)
            _isBusy.value = false
            _statusBanner.value = if (res.isSuccess) {
                "Đã nạp ${res.getOrDefault(0)} bản ghi mẫu thử nghiệm (Viettel, VinaPhone, MobiFone)."
            } else {
                "Lỗi nạp dữ liệu mẫu: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun saveSyncConfiguration(context: Context, config: SyncConfig) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveSyncConfig(config)
            if (res.isSuccess) {
                DailySyncWorker.scheduleDailySync(
                    context = context,
                    enabled = config.autoSyncEnabled,
                    wifiOnly = config.wifiOnly
                )
                _statusBanner.value = if (config.autoSyncEnabled) {
                    "Đã lưu cấu hình & bật tự động đồng bộ mỗi ngày (${config.syncHour}:00)."
                } else {
                    "Đã lưu cấu hình đồng bộ (chế độ thủ công)."
                }
            } else {
                _statusBanner.value = "Lỗi lưu cấu hình: ${res.exceptionOrNull()?.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun syncNowFromConfig(context: Context, config: SyncConfig) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveSyncConfig(config)
            val nowText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

            val csvResult = when {
                config.driveCsvUrl.isNotBlank() -> DriveAndZaloClient.fetchCsvFromUrl(config.driveCsvUrl)
                config.localSafUri.isNotBlank() -> DriveAndZaloClient.readCsvFromUri(context, config.localSafUri)
                else -> Result.failure(
                    IllegalStateException("Vui lòng nhập liên kết Google Sheets/Drive CSV hoặc chọn tệp CSV từ Drive.")
                )
            }

            csvResult.fold(
                onSuccess = { csvText ->
                    val parsed = CsvTelecomParser.parseCsvText(
                        csvText = csvText,
                        sourceLabel = "Drive: ${config.driveFolderPath}"
                    )
                    if (parsed.isEmpty()) {
                        repository.addSyncLog(
                            SyncLog(
                                sourcePath = config.driveFolderPath,
                                recordsProcessed = 0L,
                                status = "Thất bại",
                                message = "Không tìm thấy dòng dữ liệu hợp lệ trong tệp CSV.",
                                triggeredBy = "Thủ công"
                            )
                        )
                        _statusBanner.value = "Tệp không có dữ liệu hợp lệ để đồng bộ."
                    } else {
                        val count = repository.batchImportRecords(parsed).getOrDefault(0)
                        repository.saveSyncConfig(
                            config.copy(
                                lastSyncStatus = "Thành công ($count dòng)",
                                lastSyncTimeText = nowText
                            )
                        )
                        repository.addSyncLog(
                            SyncLog(
                                sourcePath = config.driveFolderPath,
                                recordsProcessed = count.toLong(),
                                status = "Thành công",
                                message = "Đồng bộ thành công $count bản ghi từ ${config.driveFolderPath}.",
                                triggeredBy = "Thủ công"
                            )
                        )
                        _statusBanner.value = "Đã đồng bộ $count bản ghi từ ${config.driveFolderPath}."
                    }
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Lỗi đồng bộ dữ liệu."
                    repository.saveSyncConfig(
                        config.copy(lastSyncStatus = "Lỗi đồng bộ", lastSyncTimeText = nowText)
                    )
                    repository.addSyncLog(
                        SyncLog(
                            sourcePath = config.driveFolderPath,
                            recordsProcessed = 0L,
                            status = "Thất bại",
                            message = msg.take(300),
                            triggeredBy = "Thủ công"
                        )
                    )
                    _statusBanner.value = msg
                }
            )
            _isBusy.value = false
        }
    }

    fun sendZaloWebhookReport(config: SyncConfig, summaryText: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = DriveAndZaloClient.sendZaloWebhook(
                webhookUrl = config.zaloWebhookUrl,
                targetLabel = config.zaloTargetLabel,
                message = summaryText
            )
            repository.addZaloReport(
                ZaloReport(
                    targetName = config.zaloTargetLabel.ifBlank { "Zalo Webhook" },
                    channel = "Zalo Webhook",
                    summaryText = summaryText,
                    status = if (res.isSuccess) "Đã gửi" else "Lỗi"
                )
            )
            _isBusy.value = false
            _statusBanner.value = if (res.isSuccess) {
                "Đã gửi báo cáo tới Zalo Webhook."
            } else {
                "Lỗi gửi Webhook: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun recordZaloShareAction(targetName: String, channel: String, summaryText: String) {
        viewModelScope.launch {
            repository.addZaloReport(
                ZaloReport(
                    targetName = targetName.ifBlank { "Zalo" },
                    channel = channel,
                    summaryText = summaryText,
                    status = "Đã gửi"
                )
            )
            _statusBanner.value = "Đã ghi nhận gửi báo cáo qua $channel."
        }
    }

    fun runAiAnalysis(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            val currentRecords = (recordsState.value as? UiState.Success)?.data.orEmpty()
            val folder = syncConfig.value.driveFolderPath
            val builder = StringBuilder()
            try {
                withTimeout(60_000L) {
                    aiRepository.streamAnalysis(currentRecords, folder, trimmed).collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            builder.append(chunk)
                            _aiState.value = AiUiState.Streaming(builder.toString())
                        }
                    }
                }
                val finalAnswer = builder.toString()
                if (finalAnswer.isNotBlank()) {
                    _aiState.value = AiUiState.Success(finalAnswer)
                } else {
                    _aiState.value = AiUiState.Error("Mô hình AI không trả về nội dung.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI thinking timed out", e)
                _aiState.value = AiUiState.Error("Quá trình suy luận AI vượt quá 60 giây. Vui lòng thử câu hỏi gọn hơn.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI Logic error", e)
                _aiState.value = AiUiState.Error(e.localizedMessage ?: "Không thể kết nối Firebase AI Logic.")
            }
        }
    }

    private companion object {
        const val TAG = "TelecomViewModel"
    }
}

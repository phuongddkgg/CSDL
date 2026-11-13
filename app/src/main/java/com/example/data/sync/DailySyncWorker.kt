package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.model.SyncConfig
import com.example.data.model.SyncLog
import com.example.data.model.TelecomRecord
import com.example.data.model.ZaloReport
import com.example.data.repository.TelecomRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val triggeredBy = inputData.getString(KEY_TRIGGERED_BY) ?: "Tự động hàng ngày"
        val user = Firebase.auth.currentUser
        if (user == null) {
            Log.w(TAG, "Skipping sync: user not signed in")
            return Result.success()
        }

        val repository = TelecomRepository(applicationContext)
        val config = repository.getSyncConfigOnce().getOrNull() ?: SyncConfig()
        val nowText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val csvResult: kotlin.Result<String> = when {
            config.driveCsvUrl.isNotBlank() -> DriveAndZaloClient.fetchCsvFromUrl(config.driveCsvUrl)
            config.localSafUri.isNotBlank() -> DriveAndZaloClient.readCsvFromUri(applicationContext, config.localSafUri)
            else -> kotlin.Result.failure(
                IllegalStateException("Chưa liên kết URL Google Sheets/Drive hoặc tệp CSV trong ${config.driveFolderPath}.")
            )
        }

        return csvResult.fold(
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
                            message = "Tệp CSV không chứa bản ghi hợp lệ.",
                            triggeredBy = triggeredBy
                        )
                    )
                    repository.saveSyncConfig(
                        config.copy(lastSyncStatus = "Tệp trống", lastSyncTimeText = nowText)
                    )
                    Result.failure()
                } else {
                    val imported = repository.batchImportRecords(parsed).getOrDefault(0)
                    repository.addSyncLog(
                        SyncLog(
                            sourcePath = config.driveFolderPath,
                            recordsProcessed = imported.toLong(),
                            status = "Thành công",
                            message = "Đã đồng bộ $imported bản ghi từ ${config.driveFolderPath}",
                            triggeredBy = triggeredBy
                        )
                    )
                    repository.saveSyncConfig(
                        config.copy(lastSyncStatus = "Thành công ($imported dòng)", lastSyncTimeText = nowText)
                    )

                    if (config.zaloWebhookUrl.isNotBlank()) {
                        val allRecords = repository.getRecordsOnce().getOrDefault(parsed)
                        val summary = buildDailyZaloSummary(config.driveFolderPath, nowText, allRecords)
                        val webhookRes = DriveAndZaloClient.sendZaloWebhook(
                            config.zaloWebhookUrl,
                            config.zaloTargetLabel,
                            summary
                        )
                        repository.addZaloReport(
                            ZaloReport(
                                targetName = config.zaloTargetLabel.ifBlank { "Zalo Webhook" },
                                channel = "Zalo Webhook",
                                summaryText = summary,
                                status = if (webhookRes.isSuccess) "Đã gửi" else "Lỗi"
                            )
                        )
                    }
                    Result.success(workDataOf(KEY_RECORDS_COUNT to imported))
                }
            },
            onFailure = { error ->
                val msg = error.localizedMessage ?: "Không thể đọc nguồn đồng bộ."
                repository.addSyncLog(
                    SyncLog(
                        sourcePath = config.driveFolderPath,
                        recordsProcessed = 0L,
                        status = "Thất bại",
                        message = msg.take(300),
                        triggeredBy = triggeredBy
                    )
                )
                repository.saveSyncConfig(
                    config.copy(lastSyncStatus = "Lỗi đồng bộ", lastSyncTimeText = nowText)
                )
                Result.failure(workDataOf(KEY_ERROR_MSG to msg))
            }
        )
    }

    companion object {
        private const val TAG = "DailySyncWorker"
        private const val PERIODIC_WORK_NAME = "telecom_csdl_daily_drive_sync"
        private const val IMMEDIATE_WORK_NAME = "telecom_csdl_immediate_drive_sync"
        const val KEY_TRIGGERED_BY = "triggered_by"
        const val KEY_RECORDS_COUNT = "records_count"
        const val KEY_ERROR_MSG = "error_msg"

        fun scheduleDailySync(context: Context, enabled: Boolean, wifiOnly: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TRIGGERED_BY to "Tự động hàng ngày"))
                .build()

            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TRIGGERED_BY to "Thủ công"))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun buildDailyZaloSummary(
            folderPath: String,
            timeText: String,
            records: List<TelecomRecord>
        ): String {
            val grouped = records.groupBy { it.carrier }
            val totalSubs = records.sumOf { it.subscribers }
            val totalRev = records.sumOf { it.revenueBillionVnd }
            val avgCov = if (records.isNotEmpty()) records.map { it.coverage5gPercent }.average() else 0.0

            val sb = StringBuilder()
            sb.appendLine("📊 BÁO CÁO CSDL NHÀ MẠNG ($timeText)")
            sb.appendLine("📁 Nguồn Drive: $folderPath")
            sb.appendLine("• Tổng bản ghi: ${records.size}")
            sb.appendLine("• Tổng thuê bao: ${String.format(Locale.US, "%,d", totalSubs)}")
            sb.appendLine("• Tổng doanh thu: ${String.format(Locale.US, "%.1f", totalRev)} tỷ VNĐ")
            sb.appendLine("• Phủ sóng 5G TB: ${String.format(Locale.US, "%.1f", avgCov)}%")
            sb.appendLine("--- Chi tiết theo nhà mạng ---")
            listOf("Viettel", "VinaPhone", "MobiFone").forEach { carrier ->
                val list = grouped[carrier].orEmpty()
                val subs = list.sumOf { it.subscribers }
                val rev = list.sumOf { it.revenueBillionVnd }
                sb.appendLine("• $carrier: ${list.size} mục | ${String.format(Locale.US, "%,d", subs)} TB | ${String.format(Locale.US, "%.1f", rev)} tỷ")
            }
            return sb.toString().trim()
        }
    }
}

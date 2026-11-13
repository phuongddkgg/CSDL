package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DriveAndZaloClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun normalizeGoogleDriveCsvUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""

        // Google Sheets link -> CSV export
        val sheetRegex = Regex("""https://docs\.google\.com/spreadsheets/d/([a-zA-Z0-9_-]+)""")
        sheetRegex.find(trimmed)?.let { match ->
            val sheetId = match.groupValues[1]
            return "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
        }

        // Google Drive file link -> direct download
        val driveFileRegex = Regex("""https://drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
        driveFileRegex.find(trimmed)?.let { match ->
            val fileId = match.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }

        return trimmed
    }

    suspend fun fetchCsvFromUrl(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val url = normalizeGoogleDriveCsvUrl(rawUrl)
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Chưa cấu hình liên kết CSV từ Google Drive / Google Sheets."))
        }
        runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: Không thể tải dữ liệu từ URL đã cấu hình.")
                }
                val body = response.body?.string() ?: error("Nội dung tải về trống.")
                if (body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
                    body.trimStart().startsWith("<html", ignoreCase = true)
                ) {
                    error("Liên kết trả về trang HTML (tệp Drive chưa bật quyền chia sẻ công khai hoặc không phải CSV).")
                }
                body
            }
        }
    }

    suspend fun sendZaloWebhook(webhookUrl: String, targetLabel: String, message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanUrl = webhookUrl.trim()
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Chưa cấu hình Zalo Webhook URL."))
            }
            runCatching {
                val payload = JSONObject().apply {
                    put("target", targetLabel)
                    put("message", message)
                    put("text", message)
                    put("source", "Telecom CSDL")
                }.toString()
                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Webhook trả về mã lỗi HTTP ${response.code}")
                    }
                }
            }
        }

    fun isZaloInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.zing.zalo", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchZaloShare(context: Context, message: String): String {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (isZaloInstalled(context)) {
            shareIntent.setPackage("com.zing.zalo")
            context.startActivity(shareIntent)
            "Zalo App"
        } else {
            val chooser = Intent.createChooser(shareIntent, "Gửi báo cáo CSDL tới Zalo / Ứng dụng").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            "Zalo / Chia sẻ hệ thống"
        }
    }

    fun readCsvFromUri(context: Context, uriString: String): Result<String> {
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Không thể mở tệp đã chọn từ bộ nhớ / Google Drive.")
        }
    }
}

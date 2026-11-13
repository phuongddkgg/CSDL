package com.example.data.ai

import com.example.data.model.TelecomRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

class TelecomAiRepository {

    private val model by lazy {
        val config = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
            }
        }
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-4-carbon",
            generationConfig = config
        )
    }

    fun streamAnalysis(
        records: List<TelecomRecord>,
        driveFolderPath: String,
        userQuery: String
    ): Flow<String> {
        val prompt = buildPrompt(records, driveFolderPath, userQuery)
        return model.generateContentStream(prompt)
            .map { chunk -> chunk.text.orEmpty() }
    }

    private fun buildPrompt(
        records: List<TelecomRecord>,
        driveFolderPath: String,
        userQuery: String
    ): String {
        val summaryHeader = buildString {
            appendLine("Bạn là chuyên gia phân tích dữ liệu viễn thông Việt Nam (Viettel, VinaPhone, MobiFone).")
            appendLine("Hãy trả lời bằng tiếng Việt rõ ràng, súc tích, có số liệu cụ thể dựa trên dữ liệu hiện có trong cơ sở dữ liệu đã đồng bộ của người dùng.")
            appendLine("Thư mục đồng bộ Google Drive cấu hình: $driveFolderPath")
            appendLine("Tổng số bản ghi hiện tại: ${records.size}")
            if (records.isEmpty()) {
                appendLine("Hiện chưa có bản ghi nào trong CSDL của người dùng.")
            } else {
                val byCarrier = records.groupBy { it.carrier }
                byCarrier.forEach { (carrier, list) ->
                    val subs = list.sumOf { it.subscribers }
                    val rev = list.sumOf { it.revenueBillionVnd }
                    val cov = list.map { it.coverage5gPercent }.average()
                    appendLine(
                        "- $carrier: ${list.size} bản ghi | Thuê bao: ${String.format(Locale.US, "%,d", subs)} | " +
                            "Doanh thu: ${String.format(Locale.US, "%.2f", rev)} tỷ VNĐ | Phủ sóng 5G TB: ${String.format(Locale.US, "%.1f", cov)}%"
                    )
                }
                appendLine("\nChi tiết tối đa 40 bản ghi gần nhất:")
                records.take(40).forEach { r ->
                    appendLine(
                        "• [${r.carrier}] ${r.province} (${r.region}) | ${r.category} | Trạm: ${r.stationCode} | " +
                            "TB: ${r.subscribers} | DT: ${r.revenueBillionVnd} tỷ | 5G: ${r.coverage5gPercent}% | " +
                            "Gói: ${r.popularPackage} | Nguồn: ${r.source} | Ghi chú: ${r.notes}"
                    )
                }
            }
            appendLine("\nYêu cầu phân tích của người dùng:")
            appendLine(userQuery)
        }
        return summaryHeader
    }
}

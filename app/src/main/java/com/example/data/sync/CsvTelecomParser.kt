package com.example.data.sync

import com.example.data.model.TelecomRecord
import com.example.data.model.VietnamProvinceLookup
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object CsvTelecomParser {

    fun parseCsv(inputStream: InputStream, sourceLabel: String = "Google Drive CSV"): List<TelecomRecord> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = reader.readLines().filter { it.isNotBlank() }
        return parseLines(lines, sourceLabel)
    }

    fun parseCsvText(csvText: String, sourceLabel: String = "Google Drive CSV"): List<TelecomRecord> {
        val lines = csvText.lines().filter { it.isNotBlank() }
        return parseLines(lines, sourceLabel)
    }

    private fun parseLines(lines: List<String>, sourceLabel: String): List<TelecomRecord> {
        if (lines.isEmpty()) return emptyList()
        val firstCells = splitCsvRow(lines.first()).map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = firstCells.any {
            it.contains("carrier") || it.contains("nha_mang") || it.contains("nhà mạng") ||
                it.contains("province") || it.contains("tinh") || it.contains("tỉnh")
        }

        val headerIndex = if (hasHeader) {
            mapHeaderIndices(firstCells)
        } else {
            null
        }

        val dataLines = if (hasHeader) lines.drop(1) else lines
        val results = mutableListOf<TelecomRecord>()

        for ((idx, line) in dataLines.withIndex()) {
            val cells = splitCsvRow(line)
            if (cells.size < 2) continue

            val rawCarrier = getCell(cells, headerIndex?.get("carrier"), 0)
            val carrier = normalizeCarrier(rawCarrier)
            val rawProvince = getCell(cells, headerIndex?.get("province"), 1).ifBlank { "Hà Nội" }
            val geo = VietnamProvinceLookup.findOrDefault(rawProvince)

            val rawRegion = getCell(cells, headerIndex?.get("region"), 2)
            val region = normalizeRegion(rawRegion, geo.region)

            val rawCategory = getCell(cells, headerIndex?.get("category"), 3)
            val category = normalizeCategory(rawCategory)

            val stationCode = getCell(cells, headerIndex?.get("stationCode"), 4)
                .ifBlank { "${carrier.take(3).uppercase(Locale.ROOT)}-${idx + 1}" }
                .take(60)

            val subscribers = getCell(cells, headerIndex?.get("subscribers"), 5)
                .replace(",", "")
                .replace(".", "")
                .trim()
                .toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L

            val revenue = getCell(cells, headerIndex?.get("revenue"), 6)
                .replace(",", ".")
                .trim()
                .toDoubleOrNull()
                ?.coerceAtLeast(0.0) ?: 0.0

            val coverage = getCell(cells, headerIndex?.get("coverage"), 7)
                .replace("%", "")
                .replace(",", ".")
                .trim()
                .toDoubleOrNull()
                ?.coerceIn(0.0, 100.0) ?: 85.0

            val popularPackage = getCell(cells, headerIndex?.get("package"), 8)
                .ifBlank { defaultPackageFor(carrier) }
                .take(80)

            val lat = getCell(cells, headerIndex?.get("lat"), 9)
                .replace(",", ".")
                .trim()
                .toDoubleOrNull()
                ?.takeIf { it in -90.0..90.0 } ?: geo.latitude

            val lng = getCell(cells, headerIndex?.get("lng"), 10)
                .replace(",", ".")
                .trim()
                .toDoubleOrNull()
                ?.takeIf { it in -180.0..180.0 } ?: geo.longitude

            val notes = getCell(cells, headerIndex?.get("notes"), 11).take(500)

            results.add(
                TelecomRecord(
                    carrier = carrier,
                    province = rawProvince.take(100),
                    region = region,
                    category = category,
                    stationCode = stationCode,
                    subscribers = subscribers,
                    revenueBillionVnd = revenue,
                    coverage5gPercent = coverage,
                    popularPackage = popularPackage,
                    latitude = lat,
                    longitude = lng,
                    source = sourceLabel.take(80),
                    notes = notes
                )
            )
        }
        return results
    }

    fun exportToCsv(records: List<TelecomRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("carrier,province,region,category,station_code,subscribers,revenue_billion_vnd,coverage_5g_percent,popular_package,latitude,longitude,notes")
        for (r in records) {
            sb.appendLine(
                listOf(
                    csvEscape(r.carrier),
                    csvEscape(r.province),
                    csvEscape(r.region),
                    csvEscape(r.category),
                    csvEscape(r.stationCode),
                    r.subscribers.toString(),
                    String.format(Locale.US, "%.2f", r.revenueBillionVnd),
                    String.format(Locale.US, "%.1f", r.coverage5gPercent),
                    csvEscape(r.popularPackage),
                    String.format(Locale.US, "%.4f", r.latitude),
                    String.format(Locale.US, "%.4f", r.longitude),
                    csvEscape(r.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("\n", " ").replace("\r", " ")
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    private fun mapHeaderIndices(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        headers.forEachIndexed { index, h ->
            when {
                h.contains("carrier") || h.contains("nha_mang") || h.contains("nhà mạng") -> map["carrier"] = index
                h.contains("province") || h.contains("tinh") || h.contains("tỉnh") -> map["province"] = index
                h.contains("region") || h.contains("khu_vuc") || h.contains("khu vực") -> map["region"] = index
                h.contains("category") || h.contains("danh_muc") || h.contains("danh mục") -> map["category"] = index
                h.contains("station") || h.contains("ma_tram") || h.contains("mã trạm") -> map["stationCode"] = index
                h.contains("sub") || h.contains("thue_bao") || h.contains("thuê bao") -> map["subscribers"] = index
                h.contains("rev") || h.contains("doanh_thu") || h.contains("doanh thu") -> map["revenue"] = index
                h.contains("cov") || h.contains("5g") || h.contains("phu_song") || h.contains("phủ sóng") -> map["coverage"] = index
                h.contains("pack") || h.contains("goi") || h.contains("gói") -> map["package"] = index
                h == "lat" || h.contains("latitude") || h.contains("vi_do") -> map["lat"] = index
                h == "lng" || h == "lon" || h.contains("longitude") || h.contains("kinh_do") -> map["lng"] = index
                h.contains("note") || h.contains("ghi_chu") || h.contains("ghi chú") -> map["notes"] = index
            }
        }
        return map
    }

    private fun getCell(cells: List<String>, mappedIdx: Int?, fallbackIdx: Int): String {
        val idx = mappedIdx ?: fallbackIdx
        return if (idx in cells.indices) cells[idx].trim() else ""
    }

    private fun normalizeCarrier(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("vina") || lower.contains("vnpt") -> "VinaPhone"
            lower.contains("mobi") -> "MobiFone"
            else -> "Viettel"
        }
    }

    private fun normalizeRegion(raw: String, defaultRegion: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("bắc") || lower.contains("bac") || lower.contains("north") -> "Miền Bắc"
            lower.contains("trung") || lower.contains("central") -> "Miền Trung"
            lower.contains("nam") || lower.contains("south") -> "Miền Nam"
            else -> defaultRegion
        }
    }

    private fun normalizeCategory(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("bts") || lower.contains("trạm") || lower.contains("sóng") -> "Trạm BTS & Sóng"
            lower.contains("gói") || lower.contains("pack") -> "Gói cước phổ biến"
            else -> "Thuê bao & Doanh thu"
        }
    }

    private fun defaultPackageFor(carrier: String): String = when (carrier) {
        "VinaPhone" -> "VD90"
        "MobiFone" -> "MXH120"
        else -> "5G135"
    }

    private fun splitCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TelecomRecord(
    @DocumentId val id: String = "",
    val carrier: String = "Viettel",
    val province: String = "",
    val region: String = "Miền Bắc",
    val category: String = "Thuê bao & Doanh thu",
    val stationCode: String = "",
    val subscribers: Long = 0L,
    val revenueBillionVnd: Double = 0.0,
    val coverage5gPercent: Double = 0.0,
    val popularPackage: String = "",
    val latitude: Double = 21.0285,
    val longitude: Double = 105.8542,
    val source: String = "Nhập thủ công",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncConfig(
    @DocumentId val id: String = "",
    val driveFolderPath: String = "My Drive\\Viettel\\CSDL",
    val driveCsvUrl: String = "",
    val localSafUri: String = "",
    val autoSyncEnabled: Boolean = true,
    val syncHour: Long = 7L,
    val wifiOnly: Boolean = true,
    val zaloWebhookUrl: String = "",
    val zaloTargetLabel: String = "Nhóm Điều hành Mạng lưới",
    val lastSyncStatus: String = "Chưa đồng bộ",
    val lastSyncTimeText: String = "--",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncLog(
    @DocumentId val id: String = "",
    val sourcePath: String = "My Drive\\Viettel\\CSDL",
    val recordsProcessed: Long = 0L,
    val status: String = "Thành công",
    val message: String = "",
    val triggeredBy: String = "Thủ công",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ZaloReport(
    @DocumentId val id: String = "",
    val targetName: String = "",
    val channel: String = "Zalo App",
    val summaryText: String = "",
    val status: String = "Đã gửi",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ProvinceGeo(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double
)

object VietnamProvinceLookup {
    val provinces = listOf(
        ProvinceGeo("Hà Nội", "Miền Bắc", 21.0285, 105.8542),
        ProvinceGeo("Hải Phòng", "Miền Bắc", 20.8449, 106.6881),
        ProvinceGeo("Quảng Ninh", "Miền Bắc", 21.0064, 107.2925),
        ProvinceGeo("Bắc Ninh", "Miền Bắc", 21.1861, 106.0763),
        ProvinceGeo("Thái Nguyên", "Miền Bắc", 21.5672, 105.8252),
        ProvinceGeo("Thanh Hóa", "Miền Trung", 19.8067, 105.7852),
        ProvinceGeo("Nghệ An", "Miền Trung", 18.6796, 105.6813),
        ProvinceGeo("Huế", "Miền Trung", 16.4637, 107.5909),
        ProvinceGeo("Đà Nẵng", "Miền Trung", 16.0544, 108.2022),
        ProvinceGeo("Khánh Hòa", "Miền Trung", 12.2388, 109.1967),
        ProvinceGeo("Đắk Lắk", "Miền Trung", 12.6667, 108.0500),
        ProvinceGeo("Lâm Đồng", "Miền Nam", 11.9404, 108.4583),
        ProvinceGeo("TP. Hồ Chí Minh", "Miền Nam", 10.8231, 106.6297),
        ProvinceGeo("Bình Dương", "Miền Nam", 11.1667, 106.6667),
        ProvinceGeo("Đồng Nai", "Miền Nam", 10.9453, 106.8243),
        ProvinceGeo("Cần Thơ", "Miền Nam", 10.0452, 105.7469),
        ProvinceGeo("An Giang", "Miền Nam", 10.5216, 105.1259),
        ProvinceGeo("Kiên Giang", "Miền Nam", 10.0125, 105.0809),
        ProvinceGeo("Cà Mau", "Miền Nam", 9.1527, 105.1961)
    )

    fun findOrDefault(provinceName: String): ProvinceGeo {
        val normalized = provinceName.trim().lowercase()
        return provinces.firstOrNull {
            normalized.contains(it.name.lowercase()) || it.name.lowercase().contains(normalized)
        } ?: ProvinceGeo(provinceName.ifBlank { "Hà Nội" }, "Miền Bắc", 21.0285, 105.8542)
    }

    val validCarriers = listOf("Viettel", "VinaPhone", "MobiFone")
    val validRegions = listOf("Miền Bắc", "Miền Trung", "Miền Nam")
    val validCategories = listOf("Thuê bao & Doanh thu", "Trạm BTS & Sóng", "Gói cước phổ biến")
}

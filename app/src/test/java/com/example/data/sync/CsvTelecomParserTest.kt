package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTelecomParserTest {

    @Test
    fun parseCsvText_withVietnameseHeaders_parsesAllFields() {
        val csv = """
            nha_mang,tinh,khu_vuc,danh_muc,ma_tram,thue_bao,doanh_thu,phu_song_5g,goi_cuoc,lat,lng,ghi_chu
            Viettel,Kiên Giang,Miền Nam,Trạm BTS & Sóng,VTL-KG-01,1200000,340.5,88.5,5G135,10.0125,105.0809,Phú Quốc
            VinaPhone,Hà Nội,Miền Bắc,Thuê bao & Doanh thu,VNP-HN-02,2500000,720.0,91.0,VD90,21.0285,105.8542,Trung tâm
        """.trimIndent()

        val records = CsvTelecomParser.parseCsvText(csv, "Google Drive CSV")
        assertEquals(2, records.size)
        assertEquals("Viettel", records[0].carrier)
        assertEquals("Kiên Giang", records[0].province)
        assertEquals(1_200_000L, records[0].subscribers)
        assertEquals(340.5, records[0].revenueBillionVnd, 0.01)
        assertEquals(88.5, records[0].coverage5gPercent, 0.01)

        val exported = CsvTelecomParser.exportToCsv(records)
        assertTrue(exported.contains("VTL-KG-01"))
        assertTrue(exported.contains("VinaPhone"))
    }

    @Test
    fun normalizeGoogleDriveCsvUrl_convertsSheetsAndDriveUrls() {
        val sheetsUrl = "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOp/edit#gid=0"
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOp/export?format=csv",
            DriveAndZaloClient.normalizeGoogleDriveCsvUrl(sheetsUrl)
        )

        val driveUrl = "https://drive.google.com/file/d/9XyZ12345/view?usp=sharing"
        assertEquals(
            "https://drive.google.com/uc?export=download&id=9XyZ12345",
            DriveAndZaloClient.normalizeGoogleDriveCsvUrl(driveUrl)
        )
    }
}

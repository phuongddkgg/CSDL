package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SyncConfig
import com.example.data.model.SyncLog
import com.example.data.model.TelecomRecord
import com.example.data.model.ZaloReport
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TelecomRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveRecord_validPayload_createsAndUpdatesRecord() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TelecomRepository(firestore, auth)

        val record = TelecomRecord(
            carrier = "Viettel",
            province = "Hà Nội",
            region = "Miền Bắc",
            category = "Thuê bao & Doanh thu",
            stationCode = "VTL-HN-001",
            subscribers = 3_200_000L,
            revenueBillionVnd = 950.5,
            coverage5gPercent = 94.0,
            popularPackage = "5G135",
            latitude = 21.0285,
            longitude = 105.8542,
            source = "Google Drive CSV",
            notes = "Kiểm thử tích hợp"
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.saveRecord(record) }
        assertTrue(createResult.isSuccess)
        val docId = createResult.getOrThrow()
        assertTrue(docId.isNotBlank())

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeRecords(uid).first { list -> list.any { it.id == docId } }
        }
        val saved = emitted.first { it.id == docId }
        assertEquals("Viettel", saved.carrier)
        assertEquals(3_200_000L, saved.subscribers)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveRecord(saved.copy(subscribers = 3_350_000L))
        }
        assertTrue(updateResult.isSuccess)

        withTimeout(DEFAULT_TIMEOUT_MS) { repository.deleteRecord(docId) }
        Unit
    }

    @Test
    fun syncConfig_andLogs_andZaloReports_persistCorrectly() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TelecomRepository(firestore, auth)

        val configResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveSyncConfig(
                SyncConfig(
                    driveFolderPath = "My Drive\\Viettel\\CSDL",
                    autoSyncEnabled = true,
                    syncHour = 8L,
                    wifiOnly = true,
                    zaloTargetLabel = "Ban Điều Hành"
                )
            )
        }
        assertTrue(configResult.isSuccess)

        val logResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addSyncLog(
                SyncLog(
                    sourcePath = "My Drive\\Viettel\\CSDL",
                    recordsProcessed = 5L,
                    status = "Thành công",
                    message = "Đồng bộ thử nghiệm",
                    triggeredBy = "Thủ công"
                )
            )
        }
        assertTrue(logResult.isSuccess)

        val zaloResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addZaloReport(
                ZaloReport(
                    targetName = "Ban Điều Hành",
                    channel = "Zalo App",
                    summaryText = "Báo cáo tổng hợp Viettel/VinaPhone/MobiFone",
                    status = "Đã gửi"
                )
            )
        }
        assertTrue(zaloResult.isSuccess)

        val loadedConfig = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getSyncConfigOnce() }
        assertNotNull(loadedConfig.getOrNull())
        assertEquals("My Drive\\Viettel\\CSDL", loadedConfig.getOrNull()?.driveFolderPath)
        assertTrue(uid.isNotBlank())
    }

    @Test
    fun crossUserAccess_isDeniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TelecomRepository(firestore, auth)
        val recordId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveRecord(
                TelecomRecord(
                    carrier = "VinaPhone",
                    province = "Kiên Giang",
                    region = "Miền Nam",
                    stationCode = "VNP-KG-01",
                    subscribers = 820_000L,
                    revenueBillionVnd = 210.0,
                    coverage5gPercent = 82.0,
                    popularPackage = "VD90",
                    latitude = 10.0125,
                    longitude = 105.0809,
                    source = "Nhập thủ công"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("telecom_records").document(recordId)
                    .get().await()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun observeRecords_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = TelecomRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeRecords("some_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated observeRecords")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_telecom@test.com"
        const val BOB_EMAIL = "bob_telecom@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

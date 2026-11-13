package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SyncConfig
import com.example.data.model.SyncLog
import com.example.data.model.TelecomRecord
import com.example.data.model.ZaloReport
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TelecomRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Vui lòng đăng nhập Google trước khi truy cập CSDL.")
    }

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    fun observeRecords(userId: String): Flow<List<TelecomRecord>> = flow {
        val path = "users/$userId/telecom_records"
        emitAll(
            userRoot(userId).collection("telecom_records")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TelecomRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeSyncConfig(userId: String): Flow<SyncConfig?> = flow {
        val path = "users/$userId/sync_configs/default"
        emitAll(
            userRoot(userId).collection("sync_configs").document("default")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SyncConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeSyncLogs(userId: String): Flow<List<SyncLog>> = flow {
        val path = "users/$userId/sync_logs"
        emitAll(
            userRoot(userId).collection("sync_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SyncLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeZaloReports(userId: String): Flow<List<ZaloReport>> = flow {
        val path = "users/$userId/zalo_reports"
        emitAll(
            userRoot(userId).collection("zalo_reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ZaloReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun getRecordsOnce(): Result<List<TelecomRecord>> {
        val uid = requireUserId()
        val path = "users/$uid/telecom_records"
        return try {
            val snapshot = userRoot(uid).collection("telecom_records").get().await()
            val list = snapshot.toObjects(
                TelecomRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            Result.failure(e)
        }
    }

    suspend fun getSyncConfigOnce(): Result<SyncConfig?> {
        val uid = requireUserId()
        val path = "users/$uid/sync_configs/default"
        return try {
            val snap = userRoot(uid).collection("sync_configs").document("default").get().await()
            Result.success(
                if (snap.exists()) {
                    snap.toObject(SyncConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveRecord(record: TelecomRecord): Result<String> {
        val uid = requireUserId()
        val col = userRoot(uid).collection("telecom_records")
        val isCreate = record.id.isBlank()
        val docRef = if (isCreate) col.document() else col.document(record.id)
        return try {
            if (isCreate) {
                val payload = mapOf(
                    "carrier" to record.carrier,
                    "province" to record.province.take(100),
                    "region" to record.region,
                    "category" to record.category,
                    "stationCode" to record.stationCode.take(60),
                    "subscribers" to record.subscribers.coerceAtLeast(0L),
                    "revenueBillionVnd" to record.revenueBillionVnd.coerceAtLeast(0.0),
                    "coverage5gPercent" to record.coverage5gPercent.coerceIn(0.0, 100.0),
                    "popularPackage" to record.popularPackage.take(80),
                    "latitude" to record.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to record.longitude.coerceIn(-180.0, 180.0),
                    "source" to record.source.ifBlank { "Nhập thủ công" }.take(80),
                    "notes" to record.notes.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val existingSnap = docRef.get().await()
                val existingCreatedAt = existingSnap.getTimestamp("createdAt")
                if (existingCreatedAt == null) {
                    val payload = mapOf(
                        "carrier" to record.carrier,
                        "province" to record.province.take(100),
                        "region" to record.region,
                        "category" to record.category,
                        "stationCode" to record.stationCode.take(60),
                        "subscribers" to record.subscribers.coerceAtLeast(0L),
                        "revenueBillionVnd" to record.revenueBillionVnd.coerceAtLeast(0.0),
                        "coverage5gPercent" to record.coverage5gPercent.coerceIn(0.0, 100.0),
                        "popularPackage" to record.popularPackage.take(80),
                        "latitude" to record.latitude.coerceIn(-90.0, 90.0),
                        "longitude" to record.longitude.coerceIn(-180.0, 180.0),
                        "source" to record.source.ifBlank { "Nhập thủ công" }.take(80),
                        "notes" to record.notes.take(500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(payload).await()
                } else {
                    val updates = mapOf(
                        "carrier" to record.carrier,
                        "province" to record.province.take(100),
                        "region" to record.region,
                        "category" to record.category,
                        "stationCode" to record.stationCode.take(60),
                        "subscribers" to record.subscribers.coerceAtLeast(0L),
                        "revenueBillionVnd" to record.revenueBillionVnd.coerceAtLeast(0.0),
                        "coverage5gPercent" to record.coverage5gPercent.coerceIn(0.0, 100.0),
                        "popularPackage" to record.popularPackage.take(80),
                        "latitude" to record.latitude.coerceIn(-90.0, 90.0),
                        "longitude" to record.longitude.coerceIn(-180.0, 180.0),
                        "source" to record.source.ifBlank { "Nhập thủ công" }.take(80),
                        "notes" to record.notes.take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updates).await()
                }
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun batchImportRecords(records: List<TelecomRecord>): Result<Int> {
        val uid = requireUserId()
        val col = userRoot(uid).collection("telecom_records")
        return try {
            var count = 0
            records.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { record ->
                    val docRef = col.document()
                    val payload = mapOf(
                        "carrier" to record.carrier,
                        "province" to record.province.take(100),
                        "region" to record.region,
                        "category" to record.category,
                        "stationCode" to record.stationCode.take(60),
                        "subscribers" to record.subscribers.coerceAtLeast(0L),
                        "revenueBillionVnd" to record.revenueBillionVnd.coerceAtLeast(0.0),
                        "coverage5gPercent" to record.coverage5gPercent.coerceIn(0.0, 100.0),
                        "popularPackage" to record.popularPackage.take(80),
                        "latitude" to record.latitude.coerceIn(-90.0, 90.0),
                        "longitude" to record.longitude.coerceIn(-180.0, 180.0),
                        "source" to record.source.ifBlank { "Google Drive CSV" }.take(80),
                        "notes" to record.notes.take(500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                    count++
                }
                batch.commit().await()
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteRecord(recordId: String): Result<Unit> {
        val uid = requireUserId()
        require(recordId.isNotBlank())
        val docRef = userRoot(uid).collection("telecom_records").document(recordId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveSyncConfig(config: SyncConfig): Result<Unit> {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("sync_configs").document("default")
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "driveFolderPath" to config.driveFolderPath.ifBlank { "My Drive\\Viettel\\CSDL" }.take(200),
                    "driveCsvUrl" to config.driveCsvUrl.take(500),
                    "localSafUri" to config.localSafUri.take(500),
                    "autoSyncEnabled" to config.autoSyncEnabled,
                    "syncHour" to config.syncHour.coerceIn(0L, 23L),
                    "wifiOnly" to config.wifiOnly,
                    "zaloWebhookUrl" to config.zaloWebhookUrl.take(500),
                    "zaloTargetLabel" to config.zaloTargetLabel.take(120),
                    "lastSyncStatus" to config.lastSyncStatus.take(80),
                    "lastSyncTimeText" to config.lastSyncTimeText.take(60),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "driveFolderPath" to config.driveFolderPath.ifBlank { "My Drive\\Viettel\\CSDL" }.take(200),
                    "driveCsvUrl" to config.driveCsvUrl.take(500),
                    "localSafUri" to config.localSafUri.take(500),
                    "autoSyncEnabled" to config.autoSyncEnabled,
                    "syncHour" to config.syncHour.coerceIn(0L, 23L),
                    "wifiOnly" to config.wifiOnly,
                    "zaloWebhookUrl" to config.zaloWebhookUrl.take(500),
                    "zaloTargetLabel" to config.zaloTargetLabel.take(120),
                    "lastSyncStatus" to config.lastSyncStatus.take(80),
                    "lastSyncTimeText" to config.lastSyncTimeText.take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun addSyncLog(log: SyncLog): Result<String> {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("sync_logs").document()
        return try {
            val payload = mapOf(
                "sourcePath" to log.sourcePath.ifBlank { "My Drive\\Viettel\\CSDL" }.take(200),
                "recordsProcessed" to log.recordsProcessed.coerceAtLeast(0L),
                "status" to log.status.ifBlank { "Thành công" }.take(40),
                "message" to log.message.take(300),
                "triggeredBy" to log.triggeredBy.ifBlank { "Thủ công" }.take(40),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun addZaloReport(report: ZaloReport): Result<String> {
        val uid = requireUserId()
        val docRef = userRoot(uid).collection("zalo_reports").document()
        return try {
            val payload = mapOf(
                "targetName" to report.targetName.ifBlank { "Nhóm Điều hành" }.take(120),
                "channel" to report.channel.ifBlank { "Zalo App" }.take(40),
                "summaryText" to report.summaryText.take(3000),
                "status" to report.status.ifBlank { "Đã gửi" }.take(40),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }
}

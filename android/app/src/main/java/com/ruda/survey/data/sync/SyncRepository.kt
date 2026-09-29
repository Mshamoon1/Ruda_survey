package com.ruda.survey.data.sync

import androidx.room.withTransaction
import com.google.gson.Gson
import com.ruda.survey.data.local.*
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.data.repository.SurveyRepositoryImpl
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.utils.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

data class SyncResult(
    val synced: Int, val failed: Int, val conflicts: Int = 0, val skipped: Int = 0,
    val authenticationRequired: Boolean = false, val retryNeeded: Boolean = false
)

class SyncRepository(
    private val api: SurveyApi,
    private val dao: SyncDao,
    private val tokenManager: TokenManager,
    private val database: SurveyDatabase? = null
) {
    companion object { private val processor = Mutex() }
    private val gson = Gson()

    fun payload(entry: SyncQueueEntry): QueuedSurveyPayload? = runCatching {
        gson.fromJson(entry.dataJson, QueuedSurveyPayload::class.java)
            .takeIf { it.formatVersion == 2 && !it.ownerId.isNullOrBlank() && it.item != null }
    }.getOrNull()

    suspend fun processQueue(): SyncResult = withContext(Dispatchers.IO) { processor.withLock {
        val entries = dao.getAllEntries()
        if (entries.isEmpty()) return@withLock SyncResult(0, 0)
        val user = tokenManager.getAuthenticatedUserId()
        if (user == null || !tokenManager.isOfflineAccessAllowed() || tokenManager.isOnlineAuthenticationRequired()) {
            return@withLock SyncResult(0, 0, authenticationRequired = true)
        }
        val authorization = "Bearer " + (tokenManager.getAccessToken()
            ?: return@withLock SyncResult(0, 0, authenticationRequired = true))
        val db = database ?: return@withLock SyncResult(0, 0, skipped = entries.size)
        val surveys = SurveyRepositoryImpl(api, tokenManager, dao, db)
        var synced = 0
        var failed = 0
        var conflicts = 0
        var skipped = 0
        var retry = false
        val blocked = mutableSetOf<String>()
        val acknowledged = mutableMapOf<String, SurveyItem>()

        for (entry in entries) {
            val data = payload(entry)
            // Old payloads have no reliable owner and may represent misclassified updates.
            if (data == null) {
                if (entry.status != SyncQueueEntry.STATUS_SYNCED && entry.nextRetryAt != Long.MAX_VALUE) {
                    dao.markFailed(entry.id, "Legacy operation requires review before synchronization", Long.MAX_VALUE)
                }
                skipped++
                continue
            }
            if (data.ownerId != user) { skipped++; continue }
            if (entry.status == SyncQueueEntry.STATUS_SYNCED) {
                data.acknowledgedItem?.let { acknowledged[entry.parcelCode] = it }
                continue
            }
            if (tokenManager.getAuthenticatedUserId() != user || !tokenManager.isOfflineAccessAllowed()) {
                return@withLock SyncResult(synced, failed, conflicts, skipped, authenticationRequired = true)
            }
            if (entry.parcelCode in blocked) { skipped++; continue }
            if (entry.status == SyncQueueEntry.STATUS_IN_PROGRESS) {
                // A killed request may have committed. Server idempotency is not yet confirmed.
                dao.markConflict(entry.id, "Interrupted delivery. Verify the server record before retrying.", "DELIVERY_UNCONFIRMED")
                surveys.localSurveys().find { it.id == data.item.id }?.let {
                    surveys.cache(it.copy(syncStatus = "CONFLICT"), "local")
                }
                conflicts++; blocked.add(entry.parcelCode); continue
            }
            if (entry.status == SyncQueueEntry.STATUS_CONFLICT || entry.nextRetryAt == Long.MAX_VALUE) {
                blocked.add(entry.parcelCode); skipped++; continue
            }
            if (entry.nextRetryAt > System.currentTimeMillis()) {
                blocked.add(entry.parcelCode); retry = true; continue
            }
            if (dao.claim(entry.id) != 1) continue
            var mutationStarted = false
            try {
                val current = surveys.localSurveys().find { it.id == data.item.id }
                    ?: error("Local survey is missing; queue retained")
                val base = if (data.baseItem?.syncStatus == "SYNCED") data.baseItem
                    else acknowledged[entry.parcelCode] ?: data.baseItem
                val serverId = acknowledged[entry.parcelCode]?.id ?: current.serverId ?: data.baseItem?.serverId
                var etag: String? = null
                val isCreate = entry.operationType == SyncQueueEntry.OP_SURVEY_CREATE
                require(isCreate || entry.operationType == SyncQueueEntry.OP_SURVEY_UPDATE) { "Unsupported operation; queue retained" }
                if (!isCreate) {
                    require(!serverId.isNullOrBlank()) { "Waiting for the create operation's server ID" }
                    val response = api.getSurveyById(serverId)
                    if (!response.isSuccessful) throw HttpException(response)
                    val map = response.body()?.data as? Map<*, *> ?: error("Invalid survey response")
                    val remote = surveys.mapToSurveyItem(map)
                    if (base == null || comparable(base) != comparable(remote)) {
                        dao.markConflict(entry.id, gson.toJson(remote), "SERVER_CHANGED")
                        surveys.cache(current.copy(syncStatus = "CONFLICT"), "local")
                        conflicts++; blocked.add(entry.parcelCode); continue
                    }
                    etag = response.headers()["ETag"]
                }
                // This API accepts evidence within the survey multipart request.
                fun read(path: String?): ByteArray? = path?.let { File(it).readBytes() }
                val upload = data.item.copy(
                    id = if (isCreate) data.item.id else serverId!!,
                    image1Bytes = read(data.item.image1LocalPath?.takeIf { isCreate || it != data.baseItem?.image1LocalPath }),
                    image2Bytes = read(data.item.image2LocalPath?.takeIf { isCreate || it != data.baseItem?.image2LocalPath }),
                    landOwnerDocBytes = read(data.item.documentLocalPath?.takeIf { isCreate || it != data.baseItem?.documentLocalPath })
                )
                check(tokenManager.getAuthenticatedUserId() == user && tokenManager.hasTokens()) {
                    "Session changed before upload"
                }
                mutationStarted = true
                val remote = if (isCreate) surveys.uploadCreate(upload, entry.clientUuid, authorization).getOrThrow()
                    else surveys.uploadUpdate(upload, entry.clientUuid, authorization, etag).getOrThrow()
                check(remote.id.isNotBlank()) { "Server accepted request without returning its ID" }
                check(tokenManager.getAuthenticatedUserId() == user) { "Account changed during synchronization" }
                db.withTransaction {
                    val latest = surveys.localSurveys().first { it.id == data.item.id }
                    val hasLater = dao.getAllEntries().any { it.id > entry.id && it.parcelCode == entry.parcelCode && it.status != SyncQueueEntry.STATUS_SYNCED && payload(it)?.ownerId == user }
                    val reconciled = if (hasLater) latest.copy(serverId = remote.id)
                    else remote.copy(id = latest.id, serverId = remote.id,
                        workedOnByUserId = latest.workedOnByUserId,
                        image1LocalPath = latest.image1LocalPath, image2LocalPath = latest.image2LocalPath,
                        documentLocalPath = latest.documentLocalPath, imageMetadata = latest.imageMetadata,
                        clientUuid = latest.clientUuid, landOwnerDocName = latest.landOwnerDocName)
                    surveys.cache(reconciled, if (hasLater) "local" else "server")
                    dao.updateData(entry.id, gson.toJson(data.copy(acknowledgedItem = remote)))
                    dao.markSynced(entry.id)
                    listOf(data.item.image1LocalPath, data.item.image2LocalPath, data.item.documentLocalPath)
                        .filterNotNull().forEach { db.surveyDao().markPathUploaded(it) }
                }
                acknowledged[entry.parcelCode] = remote
                synced++
            } catch (e: CancellationException) {
                if (!mutationStarted) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    dao.updateStatus(entry.id, SyncQueueEntry.STATUS_PENDING)
                }
                throw e
            } catch (e: Exception) {
                val code = (e as? HttpException)?.code()
                blocked.add(entry.parcelCode)
                when {
                    code == 401 -> {
                        dao.updateStatus(entry.id, SyncQueueEntry.STATUS_PENDING)
                        tokenManager.requireOnlineAuthentication()
                        return@withLock SyncResult(synced, failed, conflicts, skipped, authenticationRequired = true)
                    }
                    code == 409 || code == 412 -> {
                        dao.markConflict(entry.id, (e as HttpException).response()?.errorBody()?.string() ?: "Server revision conflict", "REVISION_CONFLICT")
                        conflicts++
                    }
                    mutationStarted && (e is java.io.IOException || code == null || code >= 500) &&
                        e !is java.net.UnknownHostException && e !is java.net.ConnectException && e !is IllegalArgumentException -> {
                        dao.markConflict(entry.id, "Delivery is uncertain. Local data is safe; verify the server before retrying.", "DELIVERY_UNCONFIRMED")
                        conflicts++
                    }
                    code == 429 || code != null && code >= 500 || e is java.net.UnknownHostException || e is java.net.ConnectException ||
                        !mutationStarted && e is java.io.IOException && e !is java.io.FileNotFoundException -> {
                        dao.markFailed(entry.id, e.message ?: "Network unavailable", RetryPolicy.calculateNextRetryAt(entry.retryCount))
                        retry = retry || RetryPolicy.shouldRetry(entry.retryCount)
                        failed++
                    }
                    else -> {
                        dao.markFailed(entry.id, e.message ?: "Operation needs review", Long.MAX_VALUE)
                        failed++
                    }
                }
                if (tokenManager.getAuthenticatedUserId() == user) {
                    surveys.localSurveys().find { it.id == data.item.id }?.let {
                        val state = dao.getById(entry.id)?.status ?: SyncQueueEntry.STATUS_FAILED
                        surveys.cache(it.copy(syncStatus = state), "local")
                    }
                }
            }
        }
        SyncResult(synced, failed, conflicts, skipped, retryNeeded = retry)
    } }

    private fun comparable(item: SurveyItem) = item.copy(id = "", serverId = null, syncStatus = "", workedOnByUserId = null,
        image1LocalPath = null, image2LocalPath = null, documentLocalPath = null,
        image1Bytes = null, image2Bytes = null, landOwnerDocBytes = null,
        landOwnerDocName = null, imageMetadata = emptyList(), clientUuid = null, editBase = null)

    suspend fun enqueueSurvey(entry: SyncQueueEntry): Long = dao.insert(entry)
    suspend fun getPendingCount(): Int = dao.getPendingItems().size
    suspend fun hasPendingItems(): Boolean = dao.getPendingItems().isNotEmpty()
    suspend fun clearSynced() = dao.deleteSynced()
}

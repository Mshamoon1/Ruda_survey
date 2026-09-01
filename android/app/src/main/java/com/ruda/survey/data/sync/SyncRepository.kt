package com.ruda.survey.data.sync

import com.google.gson.Gson
import com.ruda.survey.data.dto.*
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

data class SyncResult(
    val synced: Int,
    val failed: Int,
    val conflicts: Int = 0,
    val skipped: Int = 0
)

class SyncException(
    message: String,
    val errorCode: String? = null,
    val httpCode: Int = 0
) : Exception(message)

class SyncRepository(
    private val api: SurveyApi,
    private val dao: SyncDao,
    private val tokenManager: TokenManager,
    private val gson: Gson = Gson()
) {

    suspend fun enqueueRevision(
        parcelCode: String,
        data: Map<String, Any?>,
        changeReason: String?,
        clientUuid: String,
        parentRevisionNo: Int?
    ): Long {
        val allowed = setOf(
            "rd_value", "latitude", "longitude", "package_no", "village",
            "owner_name", "father_name", "cnic_no", "khasra_number", "mauza_number",
            "contact_number", "land_owner_doc", "electricity_connection_name", "land_area",
            "structure_status", "structure_name", "length_ft", "width_ft",
            "area_sqft", "construction_nature"
        )
        val filteredData = data.filterKeys { it in allowed }

        val existing = dao.getActiveByClientUuid(clientUuid)
        if (existing != null) {
            val updatedRequest = RevisionCreateRequest(
                client_uuid = clientUuid,
                data = filteredData,
                change_reason = changeReason,
                parent_revision_no = parentRevisionNo
            )
            dao.updateData(existing.id, gson.toJson(updatedRequest))
            if (existing.status == SyncQueueEntry.STATUS_CONFLICT || existing.status == SyncQueueEntry.STATUS_FAILED) {
                dao.resetForRetry(existing.id)
            }
            return existing.id
        }

        val request = RevisionCreateRequest(
            client_uuid = clientUuid,
            data = filteredData,
            change_reason = changeReason,
            parent_revision_no = parentRevisionNo
        )

        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = parcelCode,
            clientUuid = clientUuid,
            dataJson = gson.toJson(request)
        )
        return dao.insert(entry)
    }

    suspend fun enqueueImage(
        parcelCode: String,
        revisionNo: Int,
        imageType: String,
        filePath: String,
        clientUuid: String = UUID.randomUUID().toString()
    ): Long {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_IMAGE_UPLOAD,
            parcelCode = parcelCode,
            clientUuid = clientUuid,
            revisionNo = revisionNo,
            filePath = filePath,
            imageType = imageType,
            dataJson = gson.toJson(mapOf("image_type" to imageType))
        )
        return dao.insert(entry)
    }

    suspend fun processQueue(): SyncResult {
        val items = dao.getReadyItems()
        var synced = 0
        var failed = 0
        var conflicts = 0
        var skipped = 0

        for (item in items) {
            if (tokenManager.getAccessToken() == null) {
                break
            }

            try {
                dao.markInProgress(item.id)
                when (item.operationType) {
                    SyncQueueEntry.OP_REVISION_CREATE -> processRevision(item)
                    SyncQueueEntry.OP_IMAGE_UPLOAD -> processImage(item)
                }
                dao.markSynced(item.id)
                synced++
            } catch (e: Exception) {
                val errorClass = handleSyncError(item, e)
                when (errorClass) {
                    ErrorClass.CONFLICT -> conflicts++
                    else -> failed++
                }
            }
        }

        return SyncResult(synced, failed, conflicts, skipped)
    }

    private suspend fun processRevision(item: SyncQueueEntry) {
        val request = gson.fromJson(item.dataJson, RevisionCreateRequest::class.java)
        val response = api.createRevision(item.parcelCode, request)

        if (!response.isSuccessful) {
            val error = parseError(response)
            throw SyncException(
                error?.message ?: "Revision sync failed",
                error?.code,
                response.code()
            )
        }
    }

    private suspend fun processImage(item: SyncQueueEntry) {
        val filePath = item.filePath ?: throw SyncException("No file path", "INVALID_IMAGE")
        val file = File(filePath)
        if (!file.exists()) {
            throw SyncException("Image file not found: $filePath", "FILE_NOT_FOUND")
        }

        val imageBytes = file.readBytes()
        if (imageBytes.isEmpty()) {
            throw SyncException("Image file is empty", "INVALID_IMAGE")
        }

        val mediaType = when {
            filePath.endsWith(".png", true) -> "image/png"
            else -> "image/jpeg"
        }

        val requestFile = imageBytes.toRequestBody(mediaType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val typePart = (item.imageType ?: "FRONT").toRequestBody("text/plain".toMediaTypeOrNull())

        val response = api.uploadImage(
            item.parcelCode,
            item.revisionNo ?: 0,
            typePart,
            filePart
        )

        if (!response.isSuccessful) {
            val error = parseError(response)
            throw SyncException(
                error?.message ?: "Image upload failed",
                error?.code,
                response.code()
            )
        }
    }

    private suspend fun handleSyncError(item: SyncQueueEntry, e: Exception): ErrorClass {
        val errorCode = (e as? SyncException)?.errorCode
        val errorMessage = e.message ?: "Unknown error"
        val errorClass = RetryPolicy.classifyError(errorCode)

        when (errorClass) {
            ErrorClass.CONFLICT -> {
                dao.markConflict(item.id, errorMessage, errorCode ?: "CONFLICT")
            }
            ErrorClass.PERMANENT -> {
                dao.updateStatus(item.id, SyncQueueEntry.STATUS_FAILED)
            }
            ErrorClass.AUTH -> {
                dao.updateStatus(item.id, SyncQueueEntry.STATUS_FAILED)
            }
            ErrorClass.TRANSIENT -> {
                if (RetryPolicy.shouldRetry(item.retryCount)) {
                    val nextRetryAt = RetryPolicy.calculateNextRetryAt(item.retryCount)
                    dao.markFailed(item.id, errorMessage, nextRetryAt)
                } else {
                    dao.updateStatus(item.id, SyncQueueEntry.STATUS_FAILED)
                }
            }
        }

        return errorClass
    }

    private fun parseError(response: retrofit2.Response<*>): ApiErrorDetail? {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody != null) {
                gson.fromJson(errorBody, ApiErrorResponse::class.java).error
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPendingCount(): Int {
        return dao.getPendingItems().size
    }

    suspend fun hasPendingItems(): Boolean {
        return dao.getPendingItems().isNotEmpty()
    }

    suspend fun clearSynced() {
        dao.deleteSynced()
    }

    suspend fun retryFailed() {
        val items = dao.getPendingItems()
        for (item in items) {
            if (item.status == SyncQueueEntry.STATUS_FAILED) {
                dao.resetForRetry(item.id)
            }
        }
    }

    suspend fun resolveConflict(id: Long, newData: Map<String, Any?>, changeReason: String?) {
        val item = dao.getById(id) ?: return
        if (item.status != SyncQueueEntry.STATUS_CONFLICT) return

        val request = RevisionCreateRequest(
            client_uuid = item.clientUuid,
            data = newData,
            change_reason = changeReason
        )
        dao.updateData(id, gson.toJson(request))
        dao.resetForRetry(id)
    }
}

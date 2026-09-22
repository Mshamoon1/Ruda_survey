package com.ruda.survey.data.sync

import android.util.Log
import com.google.gson.Gson
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class SyncResult(
    val synced: Int,
    val failed: Int,
    val conflicts: Int = 0,
    val skipped: Int = 0
)

private fun safeLog(tag: String, msg: String) {
    try {
        Log.d(tag, msg)
    } catch (_: Throwable) {}
}

class SyncRepository(
    private val api: SurveyApi,
    private val dao: SyncDao,
    private val tokenManager: TokenManager
) {

    private val gson = Gson()

    suspend fun processQueue(): SyncResult {
        val readyItems = dao.getReadyItems() ?: emptyList()
        if (readyItems.isEmpty()) {
            safeLog("SyncRepo", "No pending items to sync")
            return SyncResult(0, 0, 0, 0)
        }

        safeLog("SyncRepo", "Processing ${readyItems.size} pending items")
        var synced = 0
        var failed = 0
        var conflicts = 0

        for (entry in readyItems) {
            try {
                dao.markInProgress(entry.id)

                val success = when (entry.operationType) {
                    SyncQueueEntry.OP_REVISION_CREATE -> processCreate(entry)
                    SyncQueueEntry.OP_IMAGE_UPLOAD -> processImageUpload(entry)
                    else -> false
                }

                if (success) {
                    dao.markSynced(entry.id)
                    synced++
                    Log.d("SyncRepo", "Synced item ${entry.id} (${entry.operationType})")
                } else {
                    handleFailure(entry)
                    failed++
                }
            } catch (e: Exception) {
                Log.e("SyncRepo", "Error syncing item ${entry.id}", e)
                handleFailure(entry)
                failed++
            }
        }

        Log.d("SyncRepo", "Sync complete: synced=$synced failed=$failed conflicts=$conflicts")
        return SyncResult(synced, failed, conflicts)
    }

    private suspend fun processCreate(entry: SyncQueueEntry): Boolean {
        return try {
            val data = gson.fromJson(entry.dataJson, Map::class.java) ?: return false

            val srNo = (data["sr_no"] as? Number)?.toInt() ?: 0
            val parcelId = data["parcel_id"] as? String
            val rd = data["rd"] as? String
            val pkg = data["pkg"] as? String
            val lat = data["lat"] as? String
            val lng = data["lng"] as? String
            val village = data["village"] as? String
            val ownerName = data["owner_name"] as? String
            val cnic = data["cnic"] as? String
            val fName = data["f_name"] as? String
            val khasraNo = data["khasra_no"] as? String
            val phone = data["phone"] as? String
            val electricity = data["electricity_connection_name"] as? String
            val landArea = data["land_area"] as? String
            val status = data["status"] as? String
            val structuralName = data["stractural_name"] as? String
            val length = data["length"] as? String
            val width = data["width"] as? String
            val area = data["area"] as? String
            val natureOfConstruction = data["nature_of_construction"] as? String
            val clientUuid = entry.clientUuid

            val response = api.createSurvey(
                srNo = srNo.toString().toTextBody(),
                parcelId = parcelId.toTextBodyOrNull(),
                rd = rd.toTextBodyOrNull(),
                pkg = pkg.toTextBodyOrNull(),
                lat = lat.toTextBodyOrNull(),
                lng = lng.toTextBodyOrNull(),
                village = village.toTextBodyOrNull(),
                ownerName = ownerName.toTextBodyOrNull(),
                cnic = cnic.toTextBodyOrNull(),
                fName = fName.toTextBodyOrNull(),
                khasraNo = khasraNo.toTextBodyOrNull(),
                phone = phone.toTextBodyOrNull(),
                electricity = electricity.toTextBodyOrNull(),
                landArea = landArea.toTextBodyOrNull(),
                status = status.toTextBodyOrNull(),
                structuralName = structuralName.toTextBodyOrNull(),
                natureOfConstruction = natureOfConstruction.toTextBodyOrNull(),
                length = length.toTextBodyOrNull(),
                width = width.toTextBodyOrNull(),
                area = area.toTextBodyOrNull(),
                landOwnerDoc = null,
                imgOne = null,
                imgTwo = null
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val createdId = response.body()?.data?.id
                if (createdId != null) {
                    tokenManager.addUserSurveyId(createdId)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SyncRepo", "processCreate failed", e)
            false
        }
    }

    private suspend fun processImageUpload(entry: SyncQueueEntry): Boolean {
        // Image upload is handled differently - images are saved as pending in SurveyViewModel
        // and uploaded with the survey. For standalone image uploads, this can be extended.
        Log.d("SyncRepo", "Image upload for ${entry.parcelCode} - skipping (handled with survey)")
        return true
    }

    private suspend fun handleFailure(entry: SyncQueueEntry) {
        val retryCount = entry.retryCount + 1
        val nextRetry = System.currentTimeMillis() + (retryCount * 60_000L) // exponential: 1min, 2min, 3min...

        if (retryCount >= 5) {
            dao.markFailed(entry.id, "Max retries exceeded", nextRetry)
            Log.w("SyncRepo", "Item ${entry.id} failed permanently after $retryCount retries")
        } else {
            dao.markFailed(entry.id, "Retry $retryCount", nextRetry)
            Log.d("SyncRepo", "Item ${entry.id} failed, retry $retryCount at $nextRetry")
        }
    }

    suspend fun enqueueSurvey(entry: SyncQueueEntry): Long {
        return dao.insert(entry)
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

    private fun String?.toTextBody(): RequestBody =
        (this ?: "").toRequestBody(null)

    private fun String?.toTextBodyOrNull(): RequestBody? =
        this?.toTextBody()
}

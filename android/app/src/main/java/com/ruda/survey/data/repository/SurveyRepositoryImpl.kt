package com.ruda.survey.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import com.google.gson.Gson
import com.ruda.survey.data.dto.*
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.domain.model.*
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.TokenManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class SurveyRepositoryImpl(
    private val api: SurveyApi,
    private val tokenManager: TokenManager,
    private val syncDao: SyncDao? = null,
    private val database: com.ruda.survey.data.local.SurveyDatabase? = null,
    private val filesDir: java.io.File? = null,
    private val isOnline: () -> Boolean = { true },
    private val scheduleSync: () -> Unit = {}
) : SurveyRepository {

    private val gson = Gson()

    override fun filterMySurveys(items: List<SurveyItem>): List<SurveyItem> {
        val user = tokenManager.getAuthenticatedUserId() ?: return emptyList()
        val workedIds = tokenManager.getUserSurveyIds()
        return items.filter {
            it.workedOnByUserId == user || it.id in workedIds || it.serverId in workedIds
        }
    }

    override suspend fun getBackendSurveyTotal(forceRefresh: Boolean): Result<Int?> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                requireSession()
                val user = owner()
                val cached = tokenManager.getBackendSurveyTotal(user)
                if (!isOnline() || !forceRefresh) return@withContext Result.success(cached)
                try {
                    val response = api.getAllSurveys()
                    check(owner() == user) { "Account changed" }
                    val body = response.body()
                    if (!response.isSuccessful || body?.success != true) throw retrofit2.HttpException(response)
                    val total = body.getTotalCount().coerceAtLeast(0)
                    cacheServerItems(body.data.map { it.toDomain() }, user)
                    tokenManager.saveBackendSurveyTotal(user, total)
                    Result.success(total)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (cached != null && tokenManager.getAuthenticatedUserId() == user) Result.success(cached)
                    else Result.failure(e)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }

    override fun observeLocalSurveys(): kotlinx.coroutines.flow.Flow<List<SurveyItem>> {
        val db = database ?: return kotlinx.coroutines.flow.emptyFlow()
        val user = owner()
        return db.surveyDao().observeSurveysForUser(user).map { rows ->
            if (tokenManager.getAuthenticatedUserId() != user || !tokenManager.isOfflineAccessAllowed()) emptyList()
            else rows.map { gson.fromJson(it.fieldsJson, SurveyItem::class.java) }
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    // The existing cache stores one snapshot per owner and stable record ID.
    private fun owner(): String = tokenManager.getAuthenticatedUserId()
        ?: error("Online authentication is required")

    private fun requireSession() {
        if (database != null) check(tokenManager.isOfflineAccessAllowed()) {
            "Please connect and sign in for today's offline survey session."
        }
    }

    internal suspend fun localSurveys(user: String? = tokenManager.getAuthenticatedUserId()): List<SurveyItem> {
        val db = database ?: return emptyList()
        return db.surveyDao().getSurveysForUser(requireNotNull(user)).map {
            gson.fromJson(it.fieldsJson, SurveyItem::class.java)
        }
    }

    internal suspend fun cache(item: SurveyItem, source: String = "server", user: String = owner()) {
        val db = database ?: return
        val type = "survey:$user:${item.id}"
        db.withTransaction {
            db.surveyDao().deleteSurveyIdentity(user, type)
            db.surveyDao().insertSurvey(com.ruda.survey.data.local.CachedSurvey(
                parcelCode = item.parcelId.ifBlank { item.id }, surveyType = type,
                source = source, revisionNo = 0, fieldsJson = gson.toJson(item),
                surveyor = user
            ))
        }
    }

    private suspend fun cacheServerItems(items: List<SurveyItem>, requestOwner: String?) {
        val db = database ?: return
        val user = requireNotNull(requestOwner)
        db.withTransaction {
            val existing = localSurveys(user).associateBy { it.serverId ?: it.id }
            val rows = items.mapNotNull { item ->
                val local = existing[item.id]
                if (local != null && local.syncStatus != "SYNCED") return@mapNotNull null
                val saved = item.copy(id = local?.id ?: item.id, serverId = item.id,
                    workedOnByUserId = local?.workedOnByUserId)
                com.ruda.survey.data.local.CachedSurvey(
                    parcelCode = saved.parcelId.ifBlank { saved.id },
                    surveyType = "survey:$user:${saved.id}", source = "server", revisionNo = 0,
                    fieldsJson = gson.toJson(saved), surveyor = user)
            }
            // Avoid scanning the entire cache once for every downloaded survey.
            rows.chunked(400).forEach { batch ->
                db.surveyDao().deleteSurveyIdentities(user, batch.map { it.surveyType })
                db.surveyDao().insertSurveys(batch)
            }
        }
    }

    override suspend fun getAllSurveys(forceRefresh: Boolean): Result<List<SurveyItem>> = try {
        requireSession()
        val requestOwner = tokenManager.getAuthenticatedUserId()
        val local = localSurveys()
        if (!isOnline() || (!forceRefresh && local.isNotEmpty())) {
            Result.success(local)
        } else {
            val response = api.getAllSurveys()
            check(database == null || requestOwner == tokenManager.getAuthenticatedUserId()) { "Account changed" }
            if (!response.isSuccessful || response.body()?.success != true) throw retrofit2.HttpException(response)
            val items = response.body()!!.data.map { it.toDomain() }
            requestOwner?.let { tokenManager.saveBackendSurveyTotal(it, response.body()!!.getTotalCount().coerceAtLeast(0)) }
            cacheServerItems(items, requestOwner)
            Result.success(if (database == null) items else localSurveys())
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        val local = if (database != null && tokenManager.isOfflineAccessAllowed()) localSurveys() else emptyList()
        if (local.isNotEmpty()) Result.success(local) else Result.failure(e)
    }

    override suspend fun getSurveyById(id: String): Result<SurveyItem> =
        lookup({ it.id == id || it.serverId == id }) {
            api.getSurveyById(localSurveys().find { it.id == id }?.serverId ?: id)
        }

    override suspend fun getSurveyBySrNo(srNo: Int): Result<SurveyItem> =
        lookup({ it.srNo == srNo }) { api.getSurveyBySrNo(srNo) }

    private suspend fun lookup(
        matches: (SurveyItem) -> Boolean,
        fetch: suspend () -> retrofit2.Response<SurveyDataWrapper>
    ): Result<SurveyItem> = try {
        requireSession()
        val requestOwner = tokenManager.getAuthenticatedUserId()
        val local = localSurveys().find(matches)
        if (local != null && (!isOnline() || local.syncStatus != "SYNCED")) {
            Result.success(local)
        } else if (!isOnline()) {
            Result.failure(Exception("This survey is not available on this device. Connect to download it first."))
        } else {
            try {
                val response = fetch()
                check(database == null || requestOwner == tokenManager.getAuthenticatedUserId()) { "Account changed" }
                if (!response.isSuccessful || response.body()?.success != true) throw retrofit2.HttpException(response)
                val data = response.body()?.data as? Map<*, *> ?: error("Invalid survey response")
                val item = mapToSurveyItem(data)
                cacheServerItems(listOf(item), requestOwner)
                Result.success(if (database == null) item else localSurveys().first(matches))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (local != null && (database == null || requestOwner == tokenManager.getAuthenticatedUserId() && tokenManager.isOfflineAccessAllowed()))
                    Result.success(local) else Result.failure(e)
            }
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    }
    override suspend fun createSurvey(item: SurveyItem): Result<SurveyItem> = saveLocal(item, true)

    override suspend fun getDraft(): SurveyItem? {
        requireSession()
        return database?.surveyDao()?.getDraft("draft:${owner()}")?.let {
            gson.fromJson(it.fieldsJson, SurveyItem::class.java)
        }
    }

    override suspend fun saveDraft(item: SurveyItem): Result<SurveyItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            requireSession()
            val user = owner()
            val saved = persistEvidence(item.copy(clientUuid = item.clientUuid ?: java.util.UUID.randomUUID().toString()))
            check(owner() == user) { "Account changed" }
            checkNotNull(database).surveyDao().insertDraft(com.ruda.survey.data.local.DraftSurvey(
                parcelCode = "draft:$user", baseRevisionNo = 0, fieldsJson = gson.toJson(saved),
                clientUuid = saved.clientUuid!!
            ))
            Result.success(saved)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun persistEvidence(item: SurveyItem): SurveyItem {
        val directory = java.io.File(checkNotNull(filesDir), "survey_evidence/${java.util.UUID.randomUUID()}")
        fun store(bytes: ByteArray?, name: String, existing: String?): String? {
            if (bytes == null) return existing
            check(directory.mkdirs() || directory.isDirectory) { "Cannot create evidence storage" }
            val file = java.io.File(directory, name)
            java.io.FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
            return file.absolutePath
        }
        return item.copy(
            image1LocalPath = store(item.image1Bytes, "imgOne.jpg", item.image1LocalPath),
            image2LocalPath = store(item.image2Bytes, "imgTwo.jpg", item.image2LocalPath),
            documentLocalPath = store(item.landOwnerDocBytes, "document", item.documentLocalPath),
            editBase = item.editBase?.copy(image1Bytes = null, image2Bytes = null, landOwnerDocBytes = null, editBase = null),
            image1Bytes = null, image2Bytes = null, landOwnerDocBytes = null)
    }

    private suspend fun saveLocal(item: SurveyItem, create: Boolean): Result<SurveyItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            requireSession()
            val user = owner()
            val db = checkNotNull(database) { "Local survey storage unavailable" }
            require(item.srNo > 0) { "Enter a valid serial number" }
            val operationId = if (create) item.clientUuid ?: java.util.UUID.randomUUID().toString() else java.util.UUID.randomUUID().toString()
            val id = if (create) "local_$operationId" else item.id
            val previous = localSurveys(user).find { it.id == id }
            if (!create) requireNotNull(previous) { "Download this survey before editing it offline" }
            var saved = persistEvidence(item).copy(
                id = id, serverId = previous?.serverId ?: item.serverId,
                clientUuid = item.clientUuid ?: operationId,
                editBase = null,
                workedOnByUserId = user,
                syncStatus = if (create) "PENDING_CREATE" else "PENDING_UPDATE"
            )
            db.withTransaction {
                requireSession()
                check(owner() == user) { "Account changed; please save again" }
                if (create && db.syncDao().getByClientUuid(operationId) != null) {
                    saved = localSurveys(user).first { it.id == id }
                    return@withTransaction
                }
                cache(saved, "local", user)
                db.syncDao().insert(SyncQueueEntry(
                    operationType = if (create) SyncQueueEntry.OP_SURVEY_CREATE else SyncQueueEntry.OP_SURVEY_UPDATE,
                    parcelCode = id, clientUuid = operationId,
                    dataJson = gson.toJson(com.ruda.survey.data.local.QueuedSurveyPayload(user, saved, item.editBase ?: previous))
                ))
                listOf("imgOne" to saved.image1LocalPath, "imgTwo" to saved.image2LocalPath,
                    "document" to saved.documentLocalPath).forEach { (type, path) ->
                    if (path != null) db.surveyDao().insertImage(com.ruda.survey.data.local.CachedImage(
                        parcelCode = id, revisionNo = 0, imageType = type, filePath = path,
                        fileSize = java.io.File(path).length()))
                }
                db.surveyDao().deleteDraft("draft:$user")
            }
            runCatching { tokenManager.addUserSurveyId(saved.id) }
            // A scheduling error must not turn an already committed local save into failure.
            runCatching { scheduleSync() }
            Result.success(saved)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    internal suspend fun uploadCreate(item: SurveyItem, operationId: String? = null, authorization: String? = null): Result<SurveyItem> {
        return try {
            val response = api.createSurvey(
                srNo = item.srNo.toString().toTextBody(),
                parcelId = item.parcelId.toTextBodyOrNull(),
                rd = item.rd.toTextBodyOrNull(),
                pkg = item.pkg.toTextBodyOrNull(),
                lat = item.lat.toString().toTextBody(),
                lng = item.lng.toString().toTextBody(),
                village = item.village.toTextBodyOrNull(),
                ownerName = item.ownerName.toTextBodyOrNull(),
                cnic = item.cnic.toTextBodyOrNull(),
                fName = item.fName.toTextBodyOrNull(),
                khasraNo = item.khasraNo.toTextBodyOrNull(),
                phone = item.phone.toTextBodyOrNull(),
                electricity = item.electricityConnectionName.toTextBodyOrNull(),
                landArea = item.landArea.toTextBodyOrNull(),
                status = item.status.toTextBodyOrNull(),
                structuralName = item.structuralName.toTextBodyOrNull(),
                length = item.length.toTextBodyOrNull(),
                width = item.width.toTextBodyOrNull(),
                area = item.area.toTextBodyOrNull(),
                natureOfConstruction = item.natureOfConstruction.toTextBodyOrNull(),
                landOwnerDoc = item.landOwnerDocBytes?.toImagePart("land_owner_doc", item.landOwnerDocName ?: "doc.pdf"),
                imgOne = item.image1Bytes?.toImagePart("imgOne", "imgOne.jpg"),
                imgTwo = item.image2Bytes?.toImagePart("imgTwo", "imgTwo.jpg"),
                operationId = operationId, authorization = authorization
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                if (body.success && body.data != null) {
                    val createdItem = body.data.toDomain()

                    Result.success(createdItem)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                Result.failure(retrofit2.HttpException(response))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateSurvey(item: SurveyItem): Result<SurveyItem> = saveLocal(item, false)

    internal suspend fun uploadUpdate(item: SurveyItem, operationId: String? = null, authorization: String? = null, ifMatch: String? = null): Result<SurveyItem> {
        return try {
            val imgOnePart = item.image1Bytes?.toImagePart("imgOne", "imgOne.jpg")
            val imgTwoPart = item.image2Bytes?.toImagePart("imgTwo", "imgTwo.jpg")
            val docPart = item.landOwnerDocBytes?.toImagePart("land_owner_doc", item.landOwnerDocName ?: "doc.pdf")
            val response = api.updateSurvey(
                id = item.id,
                srNo = item.srNo.toString().toTextBody(),
                parcelId = item.parcelId.toTextBodyOrNull(),
                rd = item.rd.toTextBodyOrNull(),
                pkg = item.pkg.toTextBodyOrNull(),
                lat = item.lat.toString().toTextBody(),
                lng = item.lng.toString().toTextBody(),
                village = item.village.toTextBodyOrNull(),
                ownerName = item.ownerName.toTextBodyOrNull(),
                cnic = item.cnic.toTextBodyOrNull(),
                fName = item.fName.toTextBodyOrNull(),
                khasraNo = item.khasraNo.toTextBodyOrNull(),
                phone = item.phone.toTextBodyOrNull(),
                electricity = item.electricityConnectionName.toTextBodyOrNull(),
                landArea = item.landArea.toTextBodyOrNull(),
                status = item.status.toTextBodyOrNull(),
                structuralName = item.structuralName.toTextBodyOrNull(),
                natureOfConstruction = item.natureOfConstruction.toTextBodyOrNull(),
                length = item.length.toTextBodyOrNull(),
                width = item.width.toTextBodyOrNull(),
                area = item.area.toTextBodyOrNull(),
                landOwnerDoc = docPart,
                imgOne = imgOnePart,
                imgTwo = imgTwoPart,
                operationId = operationId, authorization = authorization, ifMatch = ifMatch
            )
            if (response.isSuccessful) {
                val responseBody = response.body()!!
                if (responseBody.success && responseBody.data != null) {
                    val updatedItem = responseBody.data.toDomain()

                    Result.success(updatedItem)
                } else {
                    Result.failure(Exception(responseBody.message))
                }
            } else {
                Result.failure(retrofit2.HttpException(response))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun deleteSurvey(id: String): Result<Unit> {
        return try {
            requireSession()
            val local = localSurveys().find { it.id == id }
            check(local == null || local.syncStatus == "SYNCED") { "Cannot delete a survey with unsynced work" }
            val response = api.deleteSurvey(local?.serverId ?: id)
            if (response.isSuccessful) {
                if (database != null) database.surveyDao().deleteSurveyIdentity(owner(), "survey:${owner()}:$id")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override fun getAuthToken(): String? = tokenManager.getAccessToken()

    override fun isLoggedIn(): Boolean = tokenManager.isOfflineAccessAllowed()

    override fun saveSurveyId(id: String) {
        tokenManager.saveSurveyId(id)
    }

    override fun getSurveyId(): String? = tokenManager.getSurveyId()

    override fun clearSurveyId() {
        tokenManager.clearSurveyId()
    }

    private fun String?.toTextBody(): RequestBody =
        (this ?: "").toRequestBody(null)

    private fun String?.toTextBodyOrNull(): RequestBody? =
        this?.toRequestBody(null)

    private fun ByteArray.toImagePart(fieldName: String, fileName: String): MultipartBody.Part {
        val mediaType = when {
            fileName.endsWith(".pdf", true) -> "application/pdf".toMediaTypeOrNull()
            fileName.endsWith(".png", true) -> "image/png".toMediaTypeOrNull()
            else -> "image/jpeg".toMediaTypeOrNull()
        }
        val body = this.toRequestBody(mediaType)
        return MultipartBody.Part.createFormData(fieldName, fileName, body)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun mapToSurveyItem(data: Map<*, *>): SurveyItem {
        val coords = data["coordinates"]
        val topLat = data["lat"] ?: data["latitude"]
        val topLng = data["lng"] ?: data["longitude"]
        val (parsedLat, parsedLng) = extractCoordinates(coords, topLat, topLng)

        val ident = data["identification"] as? Map<*, *> ?: emptyMap<String, Any>()
        val area = data["covered_area"] as? Map<*, *> ?: emptyMap<String, Any>()

        val rawStatus = data["status"]?.toString() ?: ""
        val rawNature = data["nature_of_construction"]?.toString() ?: ""
        safeLog("SurveyRepo", "mapToSurveyItem raw status='$rawStatus' nature='$rawNature'")

        return SurveyItem(
            id = data["_id"]?.toString() ?: "",
            srNo = (data["sr_no"] as? Number)?.toInt() ?: 0,
            parcelId = data["parcel_id"]?.toString() ?: "",
            rd = data["rd"]?.toString() ?: "",
            pkg = data["pkg"]?.toString() ?: "",
            village = data["village"]?.toString() ?: "",
            status = rawStatus.lowercase(),
            structuralName = data["stractural_name"]?.toString() ?: "",
            natureOfConstruction = rawNature.lowercase(),
            imgOne = data["imgOne"]?.toString() ?: "",
            imgTwo = data["imgTwo"]?.toString() ?: "",
            lat = parsedLat,
            lng = parsedLng,
            ownerName = ident["owner_name"]?.toString() ?: "",
            fName = ident["f_name"]?.toString() ?: "",
            cnic = ident["cnic"]?.toString() ?: "",
            khasraNo = ident["khasra_no"]?.toString() ?: "",
            phone = ident["phone"]?.toString() ?: "",
            landOwnerDoc = ident["land_owner_doc"]?.toString() ?: "",
            electricityConnectionName = ident["electricity_connection_name"]?.toString() ?: "",
            landArea = ident["land_area"]?.toString() ?: "",
            length = area["length"]?.toString() ?: "",
            width = area["width"]?.toString() ?: "",
            area = area["area"]?.toString() ?: ""
        )
    }

}

private fun sanitizeLatLng(lat: Double?, lng: Double?): Pair<Double, Double> {
    val l1 = lat ?: 0.0
    val l2 = lng ?: 0.0
    if (l1 == 0.0 && l2 == 0.0) return Pair(0.0, 0.0)
    if (Math.abs(l1) > 50.0 && Math.abs(l2) <= 50.0) {
        return Pair(l2, l1)
    }
    return Pair(l1, l2)
}

private fun extractCoordinates(coordinatesRaw: Any?, topLat: Any?, topLng: Any?): Pair<Double, Double> {
    fun toDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    val tLat = toDouble(topLat)
    val tLng = toDouble(topLng)
    if (tLat != null && tLng != null && (tLat != 0.0 || tLng != 0.0)) {
        return sanitizeLatLng(tLat, tLng)
    }

    if (coordinatesRaw != null) {
        when (coordinatesRaw) {
            is CoordinatesDto -> {
                val cLat = coordinatesRaw.lat
                val cLng = coordinatesRaw.lng
                if (cLat != null && cLng != null && (cLat != 0.0 || cLng != 0.0)) {
                    return sanitizeLatLng(cLat, cLng)
                }
            }
            is Map<*, *> -> {
                val cLat = toDouble(coordinatesRaw["lat"] ?: coordinatesRaw["latitude"])
                val cLng = toDouble(coordinatesRaw["lng"] ?: coordinatesRaw["longitude"] ?: coordinatesRaw["long"])
                if (cLat != null && cLng != null && (cLat != 0.0 || cLng != 0.0)) {
                    return sanitizeLatLng(cLat, cLng)
                }
                val geoCoords = coordinatesRaw["coordinates"]
                if (geoCoords is List<*> && geoCoords.size >= 2) {
                    val gLng = toDouble(geoCoords[0])
                    val gLat = toDouble(geoCoords[1])
                    if (gLat != null && gLng != null) {
                        return sanitizeLatLng(gLat, gLng)
                    }
                }
            }
            is List<*> -> {
                if (coordinatesRaw.size >= 2) {
                    val first = toDouble(coordinatesRaw[0])
                    val second = toDouble(coordinatesRaw[1])
                    if (first != null && second != null) {
                        return sanitizeLatLng(first, second)
                    }
                }
            }
        }
    }

    return sanitizeLatLng(tLat, tLng)
}

private fun safeLog(tag: String, msg: String) {
    try {
        Log.d(tag, msg)
    } catch (_: Throwable) {}
}

private fun SurveyItemDto.toDomain(): SurveyItem {
    safeLog("SurveyRepo", "toDomain status='${status}' nature='${nature_of_construction}'")
    val (parsedLat, parsedLng) = extractCoordinates(coordinates, lat, lng)
    return SurveyItem(
        id = id,
        srNo = sr_no,
        parcelId = parcel_id ?: "",
        rd = rd ?: "",
        pkg = pkg ?: "",
        village = village ?: "",
        status = (status ?: "").lowercase(),
        structuralName = structuralName ?: "",
        natureOfConstruction = (nature_of_construction ?: "").lowercase(),
        imgOne = imgOne ?: "",
        imgTwo = imgTwo ?: "",
        lat = parsedLat,
        lng = parsedLng,
        ownerName = identification?.owner_name ?: "",
        fName = identification?.f_name ?: "",
        cnic = identification?.cnic ?: "",
        khasraNo = identification?.khasra_no ?: "",
        phone = identification?.phone ?: "",
        landOwnerDoc = identification?.land_owner_doc ?: "",
        electricityConnectionName = identification?.electricity_connection_name ?: "",
        landArea = identification?.land_area ?: "",
        length = covered_area?.length ?: "",
        width = covered_area?.width ?: "",
        area = covered_area?.area ?: ""
    )
}

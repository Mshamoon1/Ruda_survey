package com.ruda.survey.demo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ruda.survey.domain.model.*
import com.ruda.survey.domain.repository.SurveyRepository
import java.io.File
import java.security.MessageDigest

class DemoDataRepository(private val context: Context) : SurveyRepository {

    private val dao: DemoDao = DemoDatabase.getInstance(context).demoDao()
    private val gson: Gson = Gson()
    private val prefs by lazy {
        context.getSharedPreferences("demo_session", Context.MODE_PRIVATE)
    }

    init {
        kotlinx.coroutines.runBlocking {
            DemoDataSeeder(context).seedIfNeeded()
        }
    }

    override suspend fun login(username: String, password: String): Result<AuthState> {
        return try {
            val authState = when {
                username == DemoModeConfig.DEMO_USERNAME &&
                        password == DemoModeConfig.DEMO_PASSWORD -> {
                    prefs.edit()
                        .putBoolean("logged_in", true)
                        .putString("username", DemoModeConfig.DEMO_USERNAME)
                        .putString("display_name", DemoModeConfig.DEMO_DISPLAY_NAME)
                        .putString("role", DemoModeConfig.DEMO_ROLE)
                        .apply()
                    AuthState(
                        isLoggedIn = true,
                        username = DemoModeConfig.DEMO_DISPLAY_NAME,
                        role = DemoModeConfig.DEMO_ROLE
                    )
                }
                username == DemoModeConfig.DEMO_USERNAME_2 &&
                        password == DemoModeConfig.DEMO_PASSWORD_2 -> {
                    prefs.edit()
                        .putBoolean("logged_in", true)
                        .putString("username", DemoModeConfig.DEMO_USERNAME_2)
                        .putString("display_name", DemoModeConfig.DEMO_DISPLAY_NAME_2)
                        .putString("role", DemoModeConfig.DEMO_ROLE_2)
                        .apply()
                    AuthState(
                        isLoggedIn = true,
                        username = DemoModeConfig.DEMO_DISPLAY_NAME_2,
                        role = DemoModeConfig.DEMO_ROLE_2
                    )
                }
                else -> {
                    AuthState(isLoggedIn = false, username = null, role = null)
                }
            }
            if (authState.isLoggedIn) {
                Result.success(authState)
            } else {
                Result.failure(IllegalArgumentException("Invalid credentials"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            prefs.edit().clear().apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getParcelInfo(parcelCode: String): Result<ParcelInfo> {
        return try {
            val parcel = dao.getParcel(parcelCode)
                ?: return Result.failure(NoSuchElementException("Parcel not found: $parcelCode"))
            val latestRevision = dao.getLatestRevision(parcelCode)
            val currentRevisionInfo = latestRevision?.let {
                val payload: Map<String, Any?> = gson.fromJson(
                    it.full_payload_json,
                    object : TypeToken<Map<String, Any?>>() {}.type
                )
                val changes: Map<String, Map<String, Any?>> = gson.fromJson(
                    it.changes_json,
                    object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
                )
                CurrentRevisionInfo(
                    revisionNo = it.revision_no,
                    fullPayload = payload,
                    changedFields = changes.keys.toList(),
                    status = it.status
                )
            }
            Result.success(
                ParcelInfo(
                    parcelCode = parcel.parcel_code,
                    sourceNid = parcel.source_nid,
                    village = parcel.village,
                    tehsil = parcel.tehsil,
                    district = parcel.district,
                    ownerNameCurrent = parcel.owner_name_current,
                    masterLineCount = parcel.master_line_count,
                    revisionNo = parcel.current_revision_no,
                    source = parcel.source,
                    currentRevision = currentRevisionInfo,
                    khasraNumber = parcel.khasra_number,
                    mauzaNumber = parcel.mauza_number
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentSurvey(parcelCode: String): Result<SurveyData> {
        return try {
            val parcel = dao.getParcel(parcelCode)
                ?: return Result.failure(NoSuchElementException("Parcel not found: $parcelCode"))

            val originalFields: Map<String, Any?> = gson.fromJson(
                parcel.original_fields_json,
                object : TypeToken<Map<String, Any?>>() {}.type
            )

            val currentFields: Map<String, Any?> = gson.fromJson(
                parcel.current_fields_json,
                object : TypeToken<Map<String, Any?>>() {}.type
            )

            val latestRevision = dao.getLatestRevision(parcelCode)
            val fields = if (latestRevision != null) {
                val revisionPayload: Map<String, Any?> = gson.fromJson(
                    latestRevision.full_payload_json,
                    object : TypeToken<Map<String, Any?>>() {}.type
                )
                originalFields + revisionPayload
            } else {
                currentFields
            }

            val images = dao.getImages(parcelCode, parcel.current_revision_no)
                .map { it.toImageInfo() }

            Result.success(
                SurveyData(
                    parcelCode = parcel.parcel_code,
                    source = parcel.source,
                    revisionNo = parcel.current_revision_no,
                    fields = fields,
                    original = originalFields,
                    images = images,
                    surveyor = latestRevision?.client_uuid,
                    changedAt = latestRevision?.created_at?.toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOriginalSurvey(parcelCode: String): Result<SurveyData> {
        return try {
            val parcel = dao.getParcel(parcelCode)
                ?: return Result.failure(NoSuchElementException("Parcel not found: $parcelCode"))

            val originalFields: Map<String, Any?> = gson.fromJson(
                parcel.original_fields_json,
                object : TypeToken<Map<String, Any?>>() {}.type
            )

            val images = dao.getAllImagesForParcel(parcelCode)
                .map { it.toImageInfo() }

            Result.success(
                SurveyData(
                    parcelCode = parcel.parcel_code,
                    source = parcel.source,
                    revisionNo = 0,
                    fields = originalFields,
                    original = originalFields,
                    images = images,
                    surveyor = null,
                    changedAt = null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createRevision(
        parcelCode: String,
        data: Map<String, Any?>,
        changeReason: String?,
        clientUuid: String,
        parentRevisionNo: Int?
    ): Result<RevisionResult> {
        return try {
            val parcel = dao.getParcel(parcelCode)
                ?: return Result.failure(NoSuchElementException("Parcel not found: $parcelCode"))

            val originalFields: Map<String, Any?> = gson.fromJson(
                parcel.original_fields_json,
                object : TypeToken<Map<String, Any?>>() {}.type
            )

            val newRevisionNo = parcel.current_revision_no + 1

            val diff = mutableMapOf<String, Map<String, Any?>>()
            for ((key, newValue) in data) {
                val oldValue = originalFields[key]
                if (oldValue != newValue) {
                    diff[key] = mapOf("old" to oldValue, "new" to newValue)
                }
            }

            val mergedPayload = originalFields + data

            val revision = DemoRevision(
                parcel_code = parcelCode,
                revision_no = newRevisionNo,
                full_payload_json = gson.toJson(mergedPayload),
                changes_json = gson.toJson(diff),
                change_reason = changeReason ?: "",
                client_uuid = clientUuid,
                status = "synced",
                created_at = System.currentTimeMillis()
            )
            dao.insertRevision(revision)

            val updatedParcel = parcel.copy(
                current_revision_no = newRevisionNo,
                current_fields_json = gson.toJson(mergedPayload)
            )
            dao.updateParcel(updatedParcel)

            Result.success(
                RevisionResult(
                    replayed = false,
                    revisionNo = newRevisionNo,
                    status = "synced",
                    diff = diff,
                    fullPayload = mergedPayload
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadImage(
        parcelCode: String,
        revisionNo: Int,
        imageType: String,
        imageBytes: ByteArray,
        fileName: String,
        latitude: Double?,
        longitude: Double?,
        accuracy: Float?,
        areaName: String?,
        capturedAt: Long?,
        pointId: String?,
        sequenceNo: Int,
        qrPayload: String?,
        stampedBytes: ByteArray?
    ): Result<ImageInfo> {
        return try {
            val parcel = dao.getParcel(parcelCode)
                ?: return Result.failure(NoSuchElementException("Parcel not found: $parcelCode"))

            val imageDir = File(context.filesDir, "demo_images/$parcelCode")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }

            val imageFile = File(imageDir, fileName)
            imageFile.writeBytes(imageBytes)

            if (stampedBytes != null) {
                val stampedFile = File(imageDir, "stamped_$fileName")
                stampedFile.writeBytes(stampedBytes)
            }

            val checksum = sha256Hex(imageBytes)

            val demoImage = DemoImage(
                parcel_code = parcelCode,
                revision_no = revisionNo,
                image_type = imageType,
                file_path = imageFile.absolutePath,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                area_name = areaName,
                captured_at = capturedAt,
                content_type = guessContentType(fileName),
                file_size = imageBytes.size.toLong(),
                qr_payload = qrPayload,
                created_at = System.currentTimeMillis()
            )
            dao.insertImage(demoImage)

            Result.success(
                ImageInfo(
                    imageType = imageType,
                    checksumSha256 = checksum,
                    storageKey = imageFile.absolutePath,
                    contentType = guessContentType(fileName),
                    fileSize = imageBytes.size.toLong(),
                    uploadedAt = System.currentTimeMillis().toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurveySheet(parcelCode: String): Result<SurveyData> {
        return try {
            val parcel = dao.getParcel(parcelCode)
                ?: return Result.failure(NoSuchElementException("Parcel not found: $parcelCode"))

            val originalFields: Map<String, Any?> = gson.fromJson(
                parcel.original_fields_json,
                object : TypeToken<Map<String, Any?>>() {}.type
            )

            val currentFields: Map<String, Any?> = gson.fromJson(
                parcel.current_fields_json,
                object : TypeToken<Map<String, Any?>>() {}.type
            )

            val latestRevision = dao.getLatestRevision(parcelCode)
            val fields = if (latestRevision != null) {
                val revisionPayload: Map<String, Any?> = gson.fromJson(
                    latestRevision.full_payload_json,
                    object : TypeToken<Map<String, Any?>>() {}.type
                )
                originalFields + revisionPayload
            } else {
                currentFields
            }

            val images = dao.getAllImagesForParcel(parcelCode)
                .map { it.toImageInfo() }

            Result.success(
                SurveyData(
                    parcelCode = parcel.parcel_code,
                    source = parcel.source,
                    revisionNo = parcel.current_revision_no,
                    fields = fields,
                    original = originalFields,
                    images = images,
                    surveyor = latestRevision?.client_uuid,
                    changedAt = latestRevision?.created_at?.toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadPdf(parcelCode: String): Result<ByteArray> {
        return try {
            val bytes = DemoPdfGenerator(context).generate(parcelCode)
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadExcel(parcelCode: String): Result<ByteArray> {
        return Result.failure(UnsupportedOperationException("Excel export is not available in demo mode"))
    }

    override suspend fun searchParcels(
        village: String?,
        tehsil: String?,
        ownerName: String?,
        khasraNumber: String?,
        mauzaNumber: String?
    ): Result<List<SearchParcel>> {
        return try {
            val parcels = dao.searchParcels(village, tehsil, ownerName, khasraNumber, mauzaNumber)
            val results = parcels.map { parcel ->
                SearchParcel(
                    parcelCode = parcel.parcel_code,
                    khasraNumber = parcel.khasra_number,
                    mauzaNumber = parcel.mauza_number,
                    ownerName = parcel.owner_name_current,
                    village = parcel.village,
                    tehsil = parcel.tehsil,
                    district = parcel.district,
                    sourceNid = parcel.source_nid
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSearchOptions(tehsil: String?): Result<Pair<List<String>, List<String>>> {
        return try {
            val tehsils = dao.getTehsils()
            val villages = if (tehsil != null) {
                dao.getVillagesByTehsil(tehsil)
            } else {
                dao.getVillages()
            }
            Result.success(Pair(villages, tehsils))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchOwnerSuggestions(query: String): Result<List<OwnerSuggestion>> {
        return try {
            val names = dao.searchOwnerNames(query)
            val suggestions = names.map { name ->
                OwnerSuggestion(ownerName = name, recordCount = dao.getOwnerRecordCount(name))
            }
            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun lookupBySerialNumber(srNo: Int): Result<List<SrNoLookupResult>> {
        return try {
            // Demo mode: search by parcel_code pattern since sr_no isn't stored
            val parcelCode = "RUDA-P14-${srNo.toString().padStart(5, '0')}"
            val parcel = dao.getParcel(parcelCode)
            if (parcel != null) {
                Result.success(listOf(
                    SrNoLookupResult(
                        parcelCode = parcel.parcel_code,
                        village = parcel.village,
                        tehsil = parcel.tehsil,
                        district = parcel.district,
                        ownerName = parcel.owner_name_current,
                        masterLineCount = parcel.master_line_count
                    )
                ))
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAuthToken(): String? {
        return if (isLoggedIn()) "demo-token" else null
    }

    override fun isLoggedIn(): Boolean {
        return prefs.getBoolean("logged_in", false)
    }

    private fun DemoImage.toImageInfo(): ImageInfo {
        return ImageInfo(
            imageType = image_type,
            checksumSha256 = null,
            storageKey = file_path,
            contentType = content_type,
            fileSize = file_size,
            uploadedAt = created_at.toString()
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun guessContentType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".webp", true) -> "image/webp"
            else -> "image/jpeg"
        }
    }
}

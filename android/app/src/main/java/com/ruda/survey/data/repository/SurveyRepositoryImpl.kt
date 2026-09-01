package com.ruda.survey.data.repository

import com.ruda.survey.data.dto.*
import com.ruda.survey.data.local.SurveyDao
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager
import com.ruda.survey.domain.model.*
import com.ruda.survey.domain.repository.SurveyRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class SurveyRepositoryImpl(
    private val api: SurveyApi,
    private val dao: SurveyDao,
    private val tokenManager: TokenManager,
    private val gson: com.google.gson.Gson = com.google.gson.Gson()
) : SurveyRepository {

    override suspend fun login(username: String, password: String): Result<AuthState> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveTokens(body.access, body.refresh)
                Result.success(AuthState(
                    isLoggedIn = true,
                    username = body.user.username,
                    role = body.user.role
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val refresh = tokenManager.getRefreshToken()
            if (refresh != null) {
                api.logout(LogoutRequest(refresh))
            }
            tokenManager.clearTokens()
            Result.success(Unit)
        } catch (e: Exception) {
            tokenManager.clearTokens()
            Result.success(Unit)
        }
    }

    override suspend fun getParcelInfo(parcelCode: String): Result<ParcelInfo> {
        return try {
            val response = api.parcelLookup(parcelCode)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(ParcelInfo(
                    parcelCode = body.parcel.parcel_code,
                    sourceNid = body.parcel.source_nid,
                    village = body.parcel.village,
                    tehsil = body.parcel.tehsil,
                    district = body.parcel.district,
                    ownerNameCurrent = body.parcel.owner_name_current,
                    masterLineCount = body.original.size,
                    revisionNo = body.revision_no,
                    source = body.source,
                    currentRevision = body.current_revision?.let {
                        CurrentRevisionInfo(
                            revisionNo = it.revision_no,
                            fullPayload = it.full_payload,
                            changedFields = it.changed_fields,
                            status = it.status
                        )
                    },
                    khasraNumber = body.parcel.khasra_number,
                    mauzaNumber = body.parcel.mauza_number
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Parcel not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentSurvey(parcelCode: String): Result<SurveyData> {
        return try {
            val response = api.currentData(parcelCode)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(SurveyData(
                    parcelCode = parcelCode,
                    source = body.source,
                    revisionNo = body.revision_no,
                    fields = body.data,
                    original = emptyMap(),
                    images = emptyList(),
                    surveyor = null,
                    changedAt = null
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to load survey"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOriginalSurvey(parcelCode: String): Result<SurveyData> {
        return try {
            val response = api.originalData(parcelCode)
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                val body = response.body()!!
                val lines = body["lines"] as? List<Map<String, Any?>> ?: emptyList()
                val fields = if (lines.isNotEmpty()) lines.first() else emptyMap()
                Result.success(SurveyData(
                    parcelCode = parcelCode,
                    source = "master",
                    revisionNo = 0,
                    fields = fields,
                    original = fields,
                    images = emptyList(),
                    surveyor = null,
                    changedAt = null
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to load original data"))
            }
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
            // Filter to only fields the backend accepts for revisions
            val allowed = setOf(
                "rd_value", "latitude", "longitude", "package_no", "village",
                "owner_name", "father_name", "cnic_no", "khasra_number", "mauza_number",
                "contact_number", "land_owner_doc", "electricity_connection_name", "land_area",
                "structure_status", "structure_name", "length_ft", "width_ft",
                "area_sqft", "construction_nature"
            )
            val filteredData = data.filterKeys { it in allowed }

            val request = RevisionCreateRequest(
                client_uuid = clientUuid,
                data = filteredData,
                change_reason = changeReason,
                parent_revision_no = parentRevisionNo
            )
            val response = api.createRevision(parcelCode, request)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(RevisionResult(
                    replayed = body.replayed,
                    revisionNo = body.revision_no,
                    status = body.status,
                    diff = body.diff,
                    fullPayload = body.full_payload
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to create revision"))
            }
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
            val uploadBytes = stampedBytes ?: imageBytes
            val mediaType = if (fileName.endsWith(".png")) {
                "image/png".toMediaTypeOrNull()
            } else {
                "image/jpeg".toMediaTypeOrNull()
            }
            val requestFile = uploadBytes.toRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)
            val typePart = imageType.toRequestBody("text/plain".toMediaTypeOrNull())

            val latPart = latitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val lngPart = longitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val accPart = accuracy?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val areaPart = areaName?.toRequestBody("text/plain".toMediaTypeOrNull())
            val captPart = capturedAt?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                    .format(java.util.Date(it))
                    .toRequestBody("text/plain".toMediaTypeOrNull())
            }
            val pointPart = pointId?.toRequestBody("text/plain".toMediaTypeOrNull())
            val seqPart = sequenceNo.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val qrPart = qrPayload?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadImage(
                parcelCode, revisionNo, typePart, filePart,
                latPart, lngPart, accPart, areaPart, captPart, pointPart, seqPart, qrPart
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(ImageInfo(
                    imageType = body.image_type,
                    checksumSha256 = body.checksum_sha256,
                    storageKey = body.storage_key,
                    contentType = body.content_type,
                    fileSize = body.file_size,
                    uploadedAt = null
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Image upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurveySheet(parcelCode: String): Result<SurveyData> {
        return try {
            val response = api.surveySheet(parcelCode)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(SurveyData(
                    parcelCode = parcelCode,
                    source = body.source,
                    revisionNo = body.current_revision_no ?: 0,
                    fields = body.fields,
                    original = body.original,
                    images = body.images.map {
                        ImageInfo(it.image_type, it.checksum_sha256, it.file_path,
                                  it.content_type, it.file_size, it.uploaded_at)
                    },
                    surveyor = body.surveyor,
                    changedAt = body.changed_at
                ))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to load sheet"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadPdf(parcelCode: String): Result<ByteArray> {
        return try {
            val response = api.surveyPdf(parcelCode)
            if (response.isSuccessful) {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body()!!.bytes()
                }
                Result.success(bytes)
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to download PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadExcel(parcelCode: String): Result<ByteArray> {
        return try {
            val response = api.surveyExport(parcelCode)
            if (response.isSuccessful) {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body()!!.bytes()
                }
                Result.success(bytes)
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to download Excel"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchParcels(village: String?, tehsil: String?, ownerName: String?, khasraNumber: String?, mauzaNumber: String?): Result<List<SearchParcel>> {
        return try {
            val response = api.searchSurveys(village, tehsil, ownerName, khasraNumber, mauzaNumber)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(body.results.map { result ->
                    SearchParcel(
                        parcelCode = result.parcel_code,
                        khasraNumber = result.khasra_number,
                        mauzaNumber = result.mauza_number,
                        ownerName = result.owner_name,
                        village = result.village,
                        tehsil = result.tehsil,
                        district = result.district,
                        sourceNid = result.source_nid
                    )
                })
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSearchOptions(tehsil: String?): Result<Pair<List<String>, List<String>>> {
        return try {
            val response = api.searchOptions(tehsil)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(Pair(body.villages, body.tehsils))
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to load search options"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchOwnerSuggestions(query: String): Result<List<com.ruda.survey.domain.model.OwnerSuggestion>> {
        return try {
            val response = api.searchOwners(query)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(body.results.map { com.ruda.survey.domain.model.OwnerSuggestion(it.owner_name, it.record_count) })
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Failed to search owners"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun lookupBySerialNumber(srNo: Int): Result<List<com.ruda.survey.domain.model.SrNoLookupResult>> {
        return try {
            val response = api.lookupBySrNo(srNo)
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(body.parcels.map { parcel ->
                    com.ruda.survey.domain.model.SrNoLookupResult(
                        parcelCode = parcel.parcel_code,
                        village = parcel.village,
                        tehsil = parcel.tehsil,
                        district = parcel.district,
                        ownerName = parcel.owner_name,
                        masterLineCount = parcel.master_line_count
                    )
                })
            } else {
                val error = parseError(response)
                Result.failure(Exception(error?.message ?: "Serial number lookup failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAuthToken(): String? = tokenManager.getAccessToken()

    override fun isLoggedIn(): Boolean = tokenManager.hasTokens()

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
}

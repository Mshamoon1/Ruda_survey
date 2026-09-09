package com.ruda.survey.data.repository

import android.util.Log
import com.ruda.survey.data.dto.*
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
    private val tokenManager: TokenManager
) : SurveyRepository {

    override suspend fun getAllSurveys(): Result<List<SurveyItem>> {
        return try {
            val response = api.getAllSurveys()
            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(body.data.map { it.toDomain() })
            } else {
                Result.failure(Exception("Failed to load surveys"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurveyById(id: String): Result<SurveyItem> {
        return try {
            val response = api.getSurveyById(id)
            if (response.isSuccessful) {
                val body = response.body()!!
                val data = body.data as? Map<*, *>
                if (data != null) {
                    Result.success(mapToSurveyItem(data))
                } else {
                    Result.failure(Exception("Invalid response format"))
                }
            } else {
                Result.failure(Exception("Survey not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurveyBySrNo(srNo: Int): Result<SurveyItem> {
        return try {
            val response = api.getSurveyBySrNo(srNo)
            if (response.isSuccessful) {
                val body = response.body()!!
                val data = body.data as? Map<*, *>
                if (data != null) {
                    Result.success(mapToSurveyItem(data))
                } else {
                    Result.failure(Exception("Invalid response format"))
                }
            } else {
                Result.failure(Exception("Survey not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSurvey(item: SurveyItem): Result<SurveyItem> {
        return try {
            Log.d("SurveyRepo", "createSurvey status='${item.status}' nature='${item.natureOfConstruction}' img1=${item.image1Bytes != null} img2=${item.image2Bytes != null} doc=${item.landOwnerDocBytes != null} docUrl='${item.landOwnerDoc}'")
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
                landOwnerDoc = item.landOwnerDocBytes?.toImagePart("land_owner_doc", item.landOwnerDocName ?: "doc.pdf")
                    ?: item.landOwnerDoc.takeIf { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("https") }?.let { text ->
                        val body = text.toRequestBody(null)
                        MultipartBody.Part.createFormData("land_owner_doc", "doc.txt", body)
                    },
                imgOne = item.image1Bytes?.toImagePart("imgOne", "imgOne.jpg"),
                imgTwo = item.image2Bytes?.toImagePart("imgTwo", "imgTwo.jpg")
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                if (body.success && body.data != null) {
                    Result.success(body.data.toDomain())
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Create failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSurvey(item: SurveyItem): Result<SurveyItem> {
        return try {
            Log.d("SurveyRepo", "updateSurvey status='${item.status}' nature='${item.natureOfConstruction}' img1Bytes=${item.image1Bytes?.size} img2Bytes=${item.image2Bytes?.size} docBytes=${item.landOwnerDocBytes?.size} docUrl='${item.landOwnerDoc}'")
            val imgOnePart = item.image1Bytes?.toImagePart("imgOne", "imgOne.jpg")
            val imgTwoPart = item.image2Bytes?.toImagePart("imgTwo", "imgTwo.jpg")
            val docPart = item.landOwnerDocBytes?.toImagePart("land_owner_doc", item.landOwnerDocName ?: "doc.pdf")
                ?: item.landOwnerDoc.takeIf { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("https") }?.let { text ->
                    val body = text.toRequestBody(null)
                    MultipartBody.Part.createFormData("land_owner_doc", "doc.txt", body)
                }
            Log.d("SurveyRepo", "updateSurvey parts: imgOnePart=${imgOnePart != null} imgTwoPart=${imgTwoPart != null} docPart=${docPart != null}")
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
                imgTwo = imgTwoPart
            )
            if (response.isSuccessful) {
                val responseBody = response.body()!!
                if (responseBody.success && responseBody.data != null) {
                    Result.success(responseBody.data.toDomain())
                } else {
                    Result.failure(Exception(responseBody.message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("SurveyRepo", "updateSurvey failed: code=${response.code()} body=$errorBody")
                Result.failure(Exception(errorBody ?: "Update failed"))
            }
        } catch (e: Exception) {
            Log.e("SurveyRepo", "updateSurvey exception", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSurvey(id: String): Result<Unit> {
        return try {
            val response = api.deleteSurvey(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAuthToken(): String? = tokenManager.getAccessToken()

    override fun isLoggedIn(): Boolean = tokenManager.hasTokens()

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
    private fun mapToSurveyItem(data: Map<*, *>): SurveyItem {
        val coords = data["coordinates"] as? Map<*, *> ?: emptyMap<String, Any>()
        val ident = data["identification"] as? Map<*, *> ?: emptyMap<String, Any>()
        val area = data["covered_area"] as? Map<*, *> ?: emptyMap<String, Any>()

        val rawStatus = data["status"]?.toString() ?: ""
        val rawNature = data["nature_of_construction"]?.toString() ?: ""
        Log.d("SurveyRepo", "mapToSurveyItem raw status='$rawStatus' nature='$rawNature'")

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
            lat = (coords["lat"] as? Number)?.toDouble() ?: 0.0,
            lng = (coords["lng"] as? Number)?.toDouble() ?: 0.0,
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

private fun SurveyItemDto.toDomain(): SurveyItem {
    Log.d("SurveyRepo", "toDomain status='${status}' nature='${nature_of_construction}'")
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
        lat = coordinates?.lat ?: 0.0,
        lng = coordinates?.lng ?: 0.0,
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

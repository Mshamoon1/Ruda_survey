package com.ruda.survey.domain.repository

import com.ruda.survey.domain.model.*

interface SurveyRepository {
    suspend fun login(username: String, password: String): Result<AuthState>
    suspend fun logout(): Result<Unit>
    suspend fun getParcelInfo(parcelCode: String): Result<ParcelInfo>
    suspend fun getCurrentSurvey(parcelCode: String): Result<SurveyData>
    suspend fun getOriginalSurvey(parcelCode: String): Result<SurveyData>
    suspend fun createRevision(
        parcelCode: String,
        data: Map<String, Any?>,
        changeReason: String?,
        clientUuid: String,
        parentRevisionNo: Int?
    ): Result<RevisionResult>
    suspend fun uploadImage(
        parcelCode: String,
        revisionNo: Int,
        imageType: String,
        imageBytes: ByteArray,
        fileName: String,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null,
        areaName: String? = null,
        capturedAt: Long? = null,
        pointId: String? = null,
        sequenceNo: Int = 1,
        qrPayload: String? = null,
        stampedBytes: ByteArray? = null
    ): Result<ImageInfo>
    suspend fun getSurveySheet(parcelCode: String): Result<SurveyData>
    suspend fun downloadPdf(parcelCode: String): Result<ByteArray>
    suspend fun downloadExcel(parcelCode: String): Result<ByteArray>
    suspend fun searchParcels(village: String?, tehsil: String?, ownerName: String? = null, khasraNumber: String? = null, mauzaNumber: String? = null): Result<List<SearchParcel>>
    suspend fun searchOwnerSuggestions(query: String): Result<List<OwnerSuggestion>>
    suspend fun lookupBySerialNumber(srNo: Int): Result<List<SrNoLookupResult>>
    suspend fun getSearchOptions(tehsil: String? = null): Result<Pair<List<String>, List<String>>>
    fun getAuthToken(): String?
    fun isLoggedIn(): Boolean
}

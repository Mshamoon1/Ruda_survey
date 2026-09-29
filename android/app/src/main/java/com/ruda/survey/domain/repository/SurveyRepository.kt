package com.ruda.survey.domain.repository

import com.ruda.survey.domain.model.*

interface SurveyRepository {
    fun filterMySurveys(items: List<SurveyItem>): List<SurveyItem> = emptyList()
    suspend fun getBackendSurveyTotal(forceRefresh: Boolean = false): Result<Int?> = Result.success(null)
    fun observeLocalSurveys(): kotlinx.coroutines.flow.Flow<List<SurveyItem>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun saveDraft(item: SurveyItem): Result<SurveyItem> = Result.failure(UnsupportedOperationException("Draft storage is unavailable"))
    suspend fun getDraft(): SurveyItem? = null
    suspend fun getAllSurveys(forceRefresh: Boolean = false): Result<List<SurveyItem>>
    suspend fun getSurveyById(id: String): Result<SurveyItem>
    suspend fun getSurveyBySrNo(srNo: Int): Result<SurveyItem>
    suspend fun findSurvey(query: String): Result<SurveyItem> {
        query.trim().toIntOrNull()?.let { return getSurveyBySrNo(it) }
        return getAllSurveys().mapCatching { items ->
            val found = items.filter { it.parcelId.equals(query.trim(), true) || it.khasraNo.equals(query.trim(), true) }
            require(found.size <= 1) { "Multiple surveys match. Search using the serial number." }
            found.singleOrNull() ?: error("No matching survey is available on this device. Connect to download it first.")
        }
    }
    suspend fun createSurvey(item: SurveyItem): Result<SurveyItem>
    suspend fun updateSurvey(item: SurveyItem): Result<SurveyItem>
    suspend fun deleteSurvey(id: String): Result<Unit>
    fun getAuthToken(): String?
    fun isLoggedIn(): Boolean
    fun saveSurveyId(id: String)
    fun getSurveyId(): String?
    fun clearSurveyId()
}

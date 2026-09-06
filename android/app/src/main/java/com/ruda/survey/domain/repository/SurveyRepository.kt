package com.ruda.survey.domain.repository

import com.ruda.survey.domain.model.*

interface SurveyRepository {
    suspend fun getAllSurveys(): Result<List<SurveyItem>>
    suspend fun getSurveyById(id: String): Result<SurveyItem>
    suspend fun getSurveyBySrNo(srNo: Int): Result<SurveyItem>
    suspend fun createSurvey(item: SurveyItem): Result<SurveyItem>
    suspend fun updateSurvey(item: SurveyItem): Result<SurveyItem>
    suspend fun deleteSurvey(id: String): Result<Unit>
    fun getAuthToken(): String?
    fun isLoggedIn(): Boolean
    fun saveSurveyId(id: String)
    fun getSurveyId(): String?
    fun clearSurveyId()
}

package com.ruda.survey.utils

interface TokenManager {
    fun saveTokens(access: String, refresh: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clearTokens()
    fun hasTokens(): Boolean

    fun saveSurveyId(id: String)
    fun getSurveyId(): String?
    fun clearSurveyId()

    fun addUserSurveyId(id: String)
    fun addUserSurveyIds(ids: Collection<String>)
    fun getUserSurveyIds(): Set<String>
    fun clearUserSurveyIds()

    fun getNewSurveyCount(): Int
    fun incrementNewSurveyCount()
    fun resetNewSurveyCount()

    fun saveUserEmail(email: String)
    fun getUserEmail(): String?
    fun saveUserName(name: String)
    fun getUserName(): String?
}

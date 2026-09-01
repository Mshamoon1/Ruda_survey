package com.ruda.survey.utils

interface TokenManager {
    fun saveTokens(access: String, refresh: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clearTokens()
    fun hasTokens(): Boolean
}

package com.ruda.survey.domain.repository

import com.ruda.survey.domain.model.AuthState

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<AuthState>
    suspend fun logout(): Result<Unit>
    fun isLoggedIn(): Boolean
    fun getAuthToken(): String?
}

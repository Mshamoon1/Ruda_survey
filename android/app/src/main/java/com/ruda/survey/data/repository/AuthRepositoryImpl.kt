package com.ruda.survey.data.repository

import com.ruda.survey.data.dto.LoginRequest
import com.ruda.survey.data.remote.AuthApi
import com.ruda.survey.domain.model.AuthState
import com.ruda.survey.domain.repository.AuthRepository
import com.ruda.survey.utils.TokenManager

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<AuthState> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.user != null && body.token != null) {
                    tokenManager.saveTokens(body.token, "")
                    Result.success(AuthState(
                        isLoggedIn = true,
                        username = body.user.user_name,
                        role = body.user.role
                    ))
                } else {
                    val message = body?.message ?: "Login failed"
                    Result.failure(Exception(message))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = if (!errorBody.isNullOrBlank()) {
                    try {
                        val json = com.google.gson.Gson().fromJson(errorBody, Map::class.java)
                        json["message"]?.toString() ?: response.message()
                    } catch (e: Exception) {
                        response.message()
                    }
                } else {
                    response.message()
                }
                Result.failure(Exception(errorMessage.ifEmpty { "Login failed" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        tokenManager.clearTokens()
        return Result.success(Unit)
    }

    override fun isLoggedIn(): Boolean = tokenManager.hasTokens()

    override fun getAuthToken(): String? = tokenManager.getAccessToken()
}

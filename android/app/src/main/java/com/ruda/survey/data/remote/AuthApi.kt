package com.ruda.survey.data.remote

import com.ruda.survey.data.dto.LoginRequest
import com.ruda.survey.data.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}

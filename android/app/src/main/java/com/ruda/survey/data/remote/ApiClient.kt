package com.ruda.survey.data.remote

import android.content.Context
import com.ruda.survey.BuildConfig
import com.ruda.survey.data.dto.RefreshResponse
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ApiClient {
    private const val BASE_URL = BuildConfig.API_BASE_URL
    private val gson = com.google.gson.Gson()
    private val refreshLock = ReentrantLock()

    fun createApi(context: Context): SurveyApi {
        val tokenManager = SecureTokenManager(context)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()
            val isLoginRequest = url.contains("/auth/login/")
            val isRefreshRequest = url.contains("/auth/refresh/")
            val token = if (!isLoginRequest && !isRefreshRequest) tokenManager.getAccessToken() else null
            val request = if (token != null) {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val tokenAuthenticator = Authenticator { _, response ->
            if (response.code == 401 && !response.request.url.toString().contains("/auth/")) {
                refreshLock.withLock {
                    val refreshToken = tokenManager.getRefreshToken() ?: return@withLock null

                    val currentToken = tokenManager.getAccessToken()
                    val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                    if (currentToken != null && currentToken != failedToken) {
                        return@withLock response.request.newBuilder()
                            .header("Authorization", "Bearer $currentToken")
                            .build()
                    }

                    try {
                        val refreshRequest = okhttp3.Request.Builder()
                            .url(BASE_URL + "auth/refresh/")
                            .post(
                                okhttp3.RequestBody.create(
                                    "application/json".toMediaType(),
                                    """{"refresh":"$refreshToken"}"""
                                )
                            )
                            .build()

                        val refreshClient = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .build()

                        val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                        if (refreshResponse.isSuccessful) {
                            val body = refreshResponse.body?.string() ?: return@withLock null
                            val json = gson.fromJson(body, RefreshResponse::class.java)
                            tokenManager.saveTokens(json.access, refreshToken)
                            response.request.newBuilder()
                                .header("Authorization", "Bearer ${json.access}")
                                .build()
                        } else {
                            tokenManager.clearTokens()
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            } else {
                null
            }
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logging)
        }

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SurveyApi::class.java)
    }
}

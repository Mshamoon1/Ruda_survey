package com.ruda.survey.data.remote

import android.content.Context
import com.ruda.survey.BuildConfig
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = BuildConfig.API_BASE_URL

    @Volatile private var cachedAuthApi: AuthApi? = null
    @Volatile private var cachedSurveyApi: SurveyApi? = null

    fun createAuthApi(context: Context): AuthApi {
        cachedAuthApi?.let { return it }
        synchronized(this) {
            cachedAuthApi?.let { return it }
            val api = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(buildClient(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi::class.java)
            cachedAuthApi = api
            return api
        }
    }

    fun createSurveyApi(context: Context): SurveyApi {
        cachedSurveyApi?.let { return it }
        synchronized(this) {
            cachedSurveyApi?.let { return it }
            val api = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(buildClient(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SurveyApi::class.java)
            cachedSurveyApi = api
            return api
        }
    }

    private fun buildClient(context: Context): OkHttpClient {
        val tokenManager = SecureTokenManager.getInstance(context)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()
            val isLoginRequest = url.contains("/login")
            val isRegisterRequest = url.contains("/register")
            val token = if (!isLoginRequest && !isRegisterRequest) tokenManager.getAccessToken() else null
            val request = if (token != null) {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }
}

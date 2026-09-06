package com.ruda.survey.data.remote

import android.content.Context
import com.ruda.survey.BuildConfig
import com.ruda.survey.data.repository.AuthRepositoryImpl
import com.ruda.survey.data.repository.SurveyRepositoryImpl
import com.ruda.survey.demo.DemoDataRepository
import com.ruda.survey.domain.repository.AuthRepository
import com.ruda.survey.domain.repository.SurveyRepository

object RepositoryFactory {

    fun create(context: Context): SurveyRepository = createSurveyRepository(context)

    fun createSurveyRepository(context: Context): SurveyRepository {
        return if (BuildConfig.DEMO_MODE) {
            DemoDataRepository(context)
        } else {
            val api = ApiClient.createSurveyApi(context.applicationContext)
            val tokenManager = SecureTokenManager(context.applicationContext)
            SurveyRepositoryImpl(api, tokenManager)
        }
    }

    fun createAuthRepository(context: Context): AuthRepository {
        val api = ApiClient.createAuthApi(context.applicationContext)
        val tokenManager = SecureTokenManager(context.applicationContext)
        return AuthRepositoryImpl(api, tokenManager)
    }
}

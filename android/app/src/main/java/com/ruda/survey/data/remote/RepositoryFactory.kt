package com.ruda.survey.data.remote

import android.content.Context
import com.ruda.survey.BuildConfig
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.repository.SurveyRepositoryImpl
import com.ruda.survey.demo.DemoDataRepository
import com.ruda.survey.domain.repository.SurveyRepository

object RepositoryFactory {

    fun create(context: Context): SurveyRepository {
        return if (BuildConfig.DEMO_MODE) {
            DemoDataRepository(context)
        } else {
            val api = ApiClient.createApi(context.applicationContext)
            val db = SurveyDatabase.getInstance(context.applicationContext)
            val tokenManager = SecureTokenManager(context.applicationContext)
            SurveyRepositoryImpl(api, db.surveyDao(), tokenManager)
        }
    }
}

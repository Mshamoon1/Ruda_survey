package com.ruda.survey.data.remote

import android.content.Context
import com.ruda.survey.BuildConfig
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.repository.AuthRepositoryImpl
import com.ruda.survey.data.repository.SurveyRepositoryImpl
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.demo.DemoDataRepository
import com.ruda.survey.domain.repository.AuthRepository
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.TokenManager

object RepositoryFactory {

    @Volatile private var cachedSurveyRepo: SurveyRepository? = null
    @Volatile private var cachedAuthRepo: AuthRepository? = null
    @Volatile private var cachedSyncRepo: SyncRepository? = null
    @Volatile private var cachedTokenManager: TokenManager? = null

    fun getTokenManager(context: Context): TokenManager {
        cachedTokenManager?.let { return it }
        synchronized(this) {
            cachedTokenManager?.let { return it }
            val mgr = if (BuildConfig.DEMO_MODE) {
                com.ruda.survey.demo.DemoTokenManager(context.applicationContext)
            } else {
                SecureTokenManager.getInstance(context.applicationContext)
            }
            cachedTokenManager = mgr
            return mgr
        }
    }

    fun create(context: Context): SurveyRepository = createSurveyRepository(context)

    fun createSurveyRepository(context: Context): SurveyRepository {
        cachedSurveyRepo?.let { return it }
        synchronized(this) {
            cachedSurveyRepo?.let { return it }
            val tokenManager = getTokenManager(context)
            val repo = if (BuildConfig.DEMO_MODE) {
                DemoDataRepository(context, tokenManager)
            } else {
                val api = ApiClient.createSurveyApi(context.applicationContext)
                val db = SurveyDatabase.getInstance(context.applicationContext)
                val syncDao = db.syncDao()
                SurveyRepositoryImpl(api, tokenManager, syncDao)
            }
            cachedSurveyRepo = repo
            return repo
        }
    }

    fun createAuthRepository(context: Context): AuthRepository {
        cachedAuthRepo?.let { return it }
        synchronized(this) {
            cachedAuthRepo?.let { return it }
            val api = ApiClient.createAuthApi(context.applicationContext)
            val tokenManager = getTokenManager(context)
            val repo = AuthRepositoryImpl(api, tokenManager)
            cachedAuthRepo = repo
            return repo
        }
    }

    fun createSyncRepository(context: Context): SyncRepository {
        cachedSyncRepo?.let { return it }
        synchronized(this) {
            cachedSyncRepo?.let { return it }
            val tokenManager = getTokenManager(context)
            val db = SurveyDatabase.getInstance(context.applicationContext)
            val syncDao = db.syncDao()
            val api = ApiClient.createSurveyApi(context.applicationContext)
            val repo = SyncRepository(api, syncDao, tokenManager)
            cachedSyncRepo = repo
            return repo
        }
    }
}

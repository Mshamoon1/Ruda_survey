package com.ruda.survey

import android.app.Application
import kotlinx.coroutines.launch
import androidx.work.Configuration
import androidx.work.WorkManager
import com.ruda.survey.data.sync.SyncWorker

class RudaSurveyApp : Application(), Configuration.Provider {
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        SyncWorker.enqueuePeriodic(this)
        applicationScope.launch {
            com.ruda.survey.data.sync.ConnectivityObserver(this@RudaSurveyApp).observe().collect { online ->
                if (online && !BuildConfig.DEMO_MODE) SyncWorker.enqueueImmediate(this@RudaSurveyApp)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

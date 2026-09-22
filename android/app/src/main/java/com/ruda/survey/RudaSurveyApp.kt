package com.ruda.survey

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.ruda.survey.data.sync.SyncWorker
import org.osmdroid.config.Configuration as OsmConfig

class RudaSurveyApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        OsmConfig.getInstance().load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this))
        OsmConfig.getInstance().userAgentValue = packageName
        SyncWorker.enqueuePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

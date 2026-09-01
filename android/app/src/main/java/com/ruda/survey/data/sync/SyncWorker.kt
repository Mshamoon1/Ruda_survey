package com.ruda.survey.data.sync

import android.content.Context
import androidx.work.*
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.remote.ApiClient
import com.ruda.survey.data.remote.SecureTokenManager
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = SurveyDatabase.getInstance(applicationContext)
            val repository = SyncRepository(
                api = ApiClient.createApi(applicationContext),
                dao = database.syncDao(),
                tokenManager = SecureTokenManager(applicationContext)
            )

            val result = repository.processQueue()

            if (result.failed > 0 && result.synced == 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "ruda_sync"
        const val PERIODIC_WORK_NAME = "ruda_sync_periodic"

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("sync")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("sync_periodic")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun getPendingWorkInfo(context: Context): List<WorkInfo> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
        }
    }
}

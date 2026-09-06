package com.ruda.survey.data.sync

import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager

data class SyncResult(
    val synced: Int,
    val failed: Int,
    val conflicts: Int = 0,
    val skipped: Int = 0
)

class SyncRepository(
    private val api: SurveyApi,
    private val dao: SyncDao,
    private val tokenManager: TokenManager
) {

    suspend fun processQueue(): SyncResult {
        return SyncResult(0, 0, 0, 0)
    }

    suspend fun getPendingCount(): Int {
        return dao.getPendingItems().size
    }

    suspend fun hasPendingItems(): Boolean {
        return dao.getPendingItems().isNotEmpty()
    }

    suspend fun clearSynced() {
        dao.deleteSynced()
    }
}

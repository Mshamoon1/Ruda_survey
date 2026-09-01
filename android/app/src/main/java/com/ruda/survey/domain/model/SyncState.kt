package com.ruda.survey.domain.model

data class SyncState(
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val inProgressCount: Int = 0,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false,
    val lastError: String? = null,
    val isOnline: Boolean = true
) {
    val hasPendingItems: Boolean get() = pendingCount > 0
    val hasConflicts: Boolean get() = conflictCount > 0
    val canSync: Boolean get() = isOnline && !isSyncing
    val totalActionable: Int get() = pendingCount + conflictCount
}

sealed class SyncOutcome {
    data class Success(val synced: Int) : SyncOutcome()
    data class Partial(val synced: Int, val failed: Int, val conflicts: Int = 0) : SyncOutcome()
    data class Error(val message: String, val code: String? = null) : SyncOutcome()
    data object NoItems : SyncOutcome()
}

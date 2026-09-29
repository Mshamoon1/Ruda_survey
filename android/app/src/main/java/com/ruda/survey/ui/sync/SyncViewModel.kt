package com.ruda.survey.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.data.sync.SyncWorker
import com.ruda.survey.domain.model.SyncOutcome
import com.ruda.survey.domain.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val syncDao: SyncDao,
    private val connectivityObserver: ConnectivityObserver,
    private val appContext: android.content.Context
) : ViewModel() {
    private val _syncState = MutableStateFlow(SyncState(isOnline = false))
    val syncState = _syncState.asStateFlow()
    private val _syncOutcome = MutableStateFlow<SyncOutcome?>(null)
    val syncOutcome = _syncOutcome.asStateFlow()
    private val _issues = MutableStateFlow<List<String>>(emptyList())
    val issues = _issues.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val tokens = RepositoryFactory.getTokenManager(appContext)
            syncDao.observeEntries().collect { entries ->
                val owner = tokens.getAuthenticatedUserId()
                val owned = entries.filter { syncRepository.payload(it)?.ownerId == owner && owner != null }
                val legacy = entries.count { syncRepository.payload(it) == null && it.status != "SYNCED" }
                val active = owned.count { it.status == SyncQueueEntry.STATUS_IN_PROGRESS }
                _syncState.update { it.copy(
                    pendingCount = owned.count { row -> row.status == SyncQueueEntry.STATUS_PENDING } + active,
                    failedCount = owned.count { row -> row.status == SyncQueueEntry.STATUS_FAILED } + legacy,
                    conflictCount = owned.count { row -> row.status == SyncQueueEntry.STATUS_CONFLICT },
                    inProgressCount = active, isSyncing = active > 0,
                    lastSyncTime = owned.filter { row -> row.status == SyncQueueEntry.STATUS_SYNCED }.maxOfOrNull { row -> row.updatedAt },
                    authenticationRequired = tokens.isOnlineAuthenticationRequired()
                ) }
                _issues.value = owned.filter { it.status in listOf("FAILED", "CONFLICT") }.map {
                    val payload = syncRepository.payload(it)!!
                    val explanation = if (it.status == "CONFLICT") "Needs review; local changes are preserved."
                        else it.lastError ?: "Synchronization failed; local changes are preserved."
                    "SR " + payload.item.srNo + ": " + explanation
                } + if (entries.any { syncRepository.payload(it) == null && it.status != "SYNCED" })
                    listOf("Earlier pending work needs ownership and attachment review before it can be uploaded.") else emptyList()
            }
        }
        viewModelScope.launch {
            connectivityObserver.observe().collect { online -> _syncState.update { it.copy(isOnline = online) } }
        }
    }

    fun triggerSync() {
        if (!connectivityObserver.isCurrentlyConnected()) {
            _syncOutcome.value = SyncOutcome.Error("You're offline. Changes are safely stored on this device.")
            return
        }
        SyncWorker.enqueueImmediate(appContext)
    }

    fun enqueueWorkManager() = triggerSync()
    fun clearError() { _syncState.update { it.copy(lastError = null) }; clearOutcome() }
    fun clearOutcome() { _syncOutcome.value = null }
}

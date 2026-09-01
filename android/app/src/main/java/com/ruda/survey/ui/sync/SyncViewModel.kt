package com.ruda.survey.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.data.sync.SyncWorker
import com.ruda.survey.domain.model.SyncOutcome
import com.ruda.survey.domain.model.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val syncDao: SyncDao,
    private val connectivityObserver: ConnectivityObserver,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncOutcome = MutableStateFlow<SyncOutcome?>(null)
    val syncOutcome: StateFlow<SyncOutcome?> = _syncOutcome.asStateFlow()

    init {
        observePendingCount()
        observeConflictCount()
        observeConnectivity()
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            syncDao.getPendingCount().collect { count ->
                _syncState.value = _syncState.value.copy(pendingCount = count)
            }
        }
    }

    private fun observeConflictCount() {
        viewModelScope.launch {
            syncDao.getConflictCount().collect { count ->
                _syncState.value = _syncState.value.copy(conflictCount = count)
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.observe().collect { isOnline ->
                _syncState.value = _syncState.value.copy(isOnline = isOnline)
                if (isOnline && _syncState.value.hasPendingItems) {
                    triggerSync()
                }
            }
        }
    }

    fun triggerSync() {
        if (!_syncState.value.canSync) return

        viewModelScope.launch {
            _syncState.value = _syncState.value.copy(isSyncing = true, lastError = null)

            try {
                val result = syncRepository.processQueue()
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncTime = System.currentTimeMillis()
                )

                _syncOutcome.value = when {
                    result.synced > 0 && result.failed == 0 && result.conflicts == 0 ->
                        SyncOutcome.Success(result.synced)
                    result.synced > 0 && (result.failed > 0 || result.conflicts > 0) ->
                        SyncOutcome.Partial(result.synced, result.failed, result.conflicts)
                    result.conflicts > 0 ->
                        SyncOutcome.Error("${result.conflicts} conflicts require resolution", "CONFLICT")
                    result.failed > 0 ->
                        SyncOutcome.Error("${result.failed} items failed")
                    else ->
                        SyncOutcome.NoItems
                }
            } catch (e: Exception) {
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastError = e.message
                )
                _syncOutcome.value = SyncOutcome.Error(e.message ?: "Sync failed")
            }
        }
    }

    fun enqueueWorkManager() {
        SyncWorker.enqueueImmediate(appContext)
    }

    fun clearError() {
        _syncState.value = _syncState.value.copy(lastError = null)
        _syncOutcome.value = null
    }

    fun clearOutcome() {
        _syncOutcome.value = null
    }
}

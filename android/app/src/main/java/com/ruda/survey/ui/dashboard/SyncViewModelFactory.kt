package com.ruda.survey.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.ui.sync.SyncViewModel

class SyncViewModelFactory(
    private val syncRepository: SyncRepository,
    private val syncDao: SyncDao,
    private val connectivityObserver: ConnectivityObserver,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SyncViewModel(syncRepository, syncDao, connectivityObserver, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

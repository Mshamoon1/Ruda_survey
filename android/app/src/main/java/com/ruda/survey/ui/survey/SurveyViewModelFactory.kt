package com.ruda.survey.ui.survey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.domain.repository.SurveyRepository

class SurveyViewModelFactory(
    private val repository: SurveyRepository,
    private val syncRepository: SyncRepository? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SurveyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SurveyViewModel(repository, syncRepository, connectivityObserver, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

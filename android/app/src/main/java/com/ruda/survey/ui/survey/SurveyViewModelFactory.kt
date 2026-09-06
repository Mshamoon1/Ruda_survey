package com.ruda.survey.ui.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ruda.survey.domain.repository.SurveyRepository

class SurveyViewModelFactory(
    private val repository: SurveyRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SurveyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SurveyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

package com.ruda.survey.ui.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruda.survey.domain.model.*
import com.ruda.survey.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SurveyViewModel(
    private val repository: SurveyRepository
) : ViewModel() {

    private val _surveyState = MutableStateFlow<UiState<SurveyItem>>(UiState.Empty)
    val surveyState: StateFlow<UiState<SurveyItem>> = _surveyState.asStateFlow()

    private val _createState = MutableStateFlow<UiState<SurveyItem>>(UiState.Empty)
    val createState: StateFlow<UiState<SurveyItem>> = _createState.asStateFlow()

    private val _updateState = MutableStateFlow<UiState<SurveyItem>>(UiState.Empty)
    val updateState: StateFlow<UiState<SurveyItem>> = _updateState.asStateFlow()

    private val _deleteState = MutableStateFlow<UiState<Unit>>(UiState.Empty)
    val deleteState: StateFlow<UiState<Unit>> = _deleteState.asStateFlow()

    private val _srNoLookupState = MutableStateFlow<UiState<SurveyItem>>(UiState.Empty)
    val srNoLookupState: StateFlow<UiState<SurveyItem>> = _srNoLookupState.asStateFlow()

    private val _allSurveysState = MutableStateFlow<UiState<List<SurveyItem>>>(UiState.Empty)
    val allSurveysState: StateFlow<UiState<List<SurveyItem>>> = _allSurveysState.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<PendingImage>>(emptyList())
    val pendingImages: StateFlow<List<PendingImage>> = _pendingImages.asStateFlow()

    var pendingDocBytes: ByteArray? = null
        private set
    var pendingDocName: String? = null
        private set

    private val _nextSrNoState = MutableStateFlow<Int?>(null)
    val nextSrNoState: StateFlow<Int?> = _nextSrNoState.asStateFlow()

    var currentSurvey: SurveyItem? = null
        private set

    var selectedImageType: String = "imgOne"

    fun fetchNextSrNo() {
        _nextSrNoState.value = null
        viewModelScope.launch {
            val result = repository.getAllSurveys()
            result.onSuccess { surveys ->
                val maxSr = surveys.maxOfOrNull { it.srNo } ?: 0
                _nextSrNoState.value = maxSr + 1
            }
        }
    }

    fun lookupBySrNo(srNo: String) {
        val parsed = srNo.trim().toIntOrNull()
        if (parsed == null || parsed <= 0) {
            _srNoLookupState.value = UiState.Error("VALIDATION_ERROR", "Enter a valid serial number")
            return
        }
        _srNoLookupState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getSurveyBySrNo(parsed)
            _srNoLookupState.value = result.fold(
                onSuccess = {
                    currentSurvey = it
                    repository.saveSurveyId(it.id)
                    UiState.Success(it)
                },
                onFailure = {
                    UiState.Error("NOT_FOUND", it.message ?: "Survey not found")
                }
            )
        }
    }

    fun getSavedSurveyId(): String? = repository.getSurveyId()

    fun clearSavedSurveyId() {
        repository.clearSurveyId()
    }

    fun resetSrNoLookup() {
        _srNoLookupState.value = UiState.Empty
    }

    fun loadAllSurveys() {
        _allSurveysState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getAllSurveys()
            _allSurveysState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("NETWORK_ERROR", it.message ?: "Failed to load surveys") }
            )
        }
    }

    fun loadSurvey(id: String) {
        _surveyState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getSurveyById(id)
            _surveyState.value = result.fold(
                onSuccess = {
                    currentSurvey = it
                    UiState.Success(it)
                },
                onFailure = { UiState.Error("LOAD_ERROR", it.message ?: "Failed to load survey") }
            )
        }
    }

    fun createSurvey(item: SurveyItem) {
        _createState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.createSurvey(item)
            _createState.value = result.fold(
                onSuccess = {
                    currentSurvey = it
                    UiState.Success(it)
                },
                onFailure = { UiState.Error("CREATE_FAILED", it.message ?: "Failed to create survey") }
            )
        }
    }

    fun updateSurvey(item: SurveyItem) {
        _updateState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.updateSurvey(item)
            _updateState.value = result.fold(
                onSuccess = {
                    currentSurvey = it
                    UiState.Success(it)
                },
                onFailure = { UiState.Error("UPDATE_FAILED", it.message ?: "Failed to update survey") }
            )
        }
    }

    fun deleteSurvey(id: String) {
        _deleteState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.deleteSurvey(id)
            _deleteState.value = result.fold(
                onSuccess = {
                    currentSurvey = null
                    UiState.Success(Unit)
                },
                onFailure = { UiState.Error("DELETE_FAILED", it.message ?: "Failed to delete survey") }
            )
        }
    }

    fun saveFormState(item: SurveyItem) {
        currentSurvey = item
    }

    fun queueGpsImage(pending: PendingImage) {
        val current = _pendingImages.value.toMutableList()
        current.removeAll { it.imageType == pending.imageType }
        current.add(pending)
        _pendingImages.value = current
        android.util.Log.d("SurveyViewModel", "queueGpsImage type=${pending.imageType} total=${current.size} bytes=${pending.stampedBytes.size}")
    }

    fun getPendingImages(): List<PendingImage> = _pendingImages.value

    fun clearPendingImages() {
        _pendingImages.value = emptyList()
    }

    fun setPendingDoc(bytes: ByteArray?, name: String?) {
        pendingDocBytes = bytes
        pendingDocName = name
    }

    fun resetCreateState() {
        _createState.value = UiState.Empty
    }

    fun resetUpdateState() {
        _updateState.value = UiState.Empty
    }

    fun resetDeleteState() {
        _deleteState.value = UiState.Empty
    }
}

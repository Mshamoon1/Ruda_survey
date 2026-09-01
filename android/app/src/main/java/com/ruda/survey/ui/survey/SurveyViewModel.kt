package com.ruda.survey.ui.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.data.sync.SyncWorker
import com.ruda.survey.domain.model.*
import com.ruda.survey.domain.repository.SurveyRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

class SurveyViewModel(
    private val repository: SurveyRepository,
    private val syncRepository: SyncRepository? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    private val appContext: android.content.Context? = null
) : ViewModel() {

    private val _parcelState = MutableStateFlow<UiState<ParcelInfo>>(UiState.Empty)
    val parcelState: StateFlow<UiState<ParcelInfo>> = _parcelState.asStateFlow()

    private val _surveyState = MutableStateFlow<UiState<SurveyData>>(UiState.Empty)
    val surveyState: StateFlow<UiState<SurveyData>> = _surveyState.asStateFlow()

    private val _originalState = MutableStateFlow<UiState<SurveyData>>(UiState.Empty)
    val originalState: StateFlow<UiState<SurveyData>> = _originalState.asStateFlow()

    private val _draftState = MutableStateFlow<EditableDraft?>(null)
    val draftState: StateFlow<EditableDraft?> = _draftState.asStateFlow()

    private val _submitState = MutableStateFlow<UiState<RevisionResult>>(UiState.Empty)
    val submitState: StateFlow<UiState<RevisionResult>> = _submitState.asStateFlow()

    private val _sheetState = MutableStateFlow<UiState<SurveyData>>(UiState.Empty)
    val sheetState: StateFlow<UiState<SurveyData>> = _sheetState.asStateFlow()

    private val _imageState = MutableStateFlow<UiState<ImageInfo>>(UiState.Empty)
    val imageState: StateFlow<UiState<ImageInfo>> = _imageState.asStateFlow()

    private val _isOfflineQueued = MutableStateFlow(false)
    val isOfflineQueued: StateFlow<Boolean> = _isOfflineQueued.asStateFlow()

    private val _pdfState = MutableStateFlow<UiState<ByteArray>>(UiState.Empty)
    val pdfState: StateFlow<UiState<ByteArray>> = _pdfState.asStateFlow()

    private val _excelState = MutableStateFlow<UiState<ByteArray>>(UiState.Empty)
    val excelState: StateFlow<UiState<ByteArray>> = _excelState.asStateFlow()

    private val _searchResultsState = MutableStateFlow<UiState<List<SearchParcel>>>(UiState.Empty)
    val searchResultsState: StateFlow<UiState<List<SearchParcel>>> = _searchResultsState.asStateFlow()

    private val _searchOptionsState = MutableStateFlow<UiState<Pair<List<String>, List<String>>>>(UiState.Empty)
    val searchOptionsState: StateFlow<UiState<Pair<List<String>, List<String>>>> = _searchOptionsState.asStateFlow()

    private val _ownerSuggestionsState = MutableStateFlow<UiState<List<OwnerSuggestion>>>(UiState.Empty)
    val ownerSuggestionsState: StateFlow<UiState<List<OwnerSuggestion>>> = _ownerSuggestionsState.asStateFlow()

    private val _srNoLookupState = MutableStateFlow<UiState<List<SrNoLookupResult>>>(UiState.Empty)
    val srNoLookupState: StateFlow<UiState<List<SrNoLookupResult>>> = _srNoLookupState.asStateFlow()

    private val _ownerQuery = MutableStateFlow("")
    private var ownerDebounceJob: Job? = null

    private var currentParcelCode: String = ""

    init {
        setupOwnerDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun setupOwnerDebounce() {
        _ownerQuery
            .filter { it.length >= 2 }
            .debounce(350L)
            .distinctUntilChanged()
            .onEach { query ->
                _ownerSuggestionsState.value = UiState.Loading
                val result = repository.searchOwnerSuggestions(query)
                _ownerSuggestionsState.value = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error("NETWORK_ERROR", it.message ?: "Failed to search owners") }
                )
            }
            .launchIn(viewModelScope)
    }

    fun onOwnerQueryChanged(query: String) {
        _ownerQuery.value = query
        if (query.length < 2) {
            ownerDebounceJob?.cancel()
            _ownerSuggestionsState.value = UiState.Empty
        }
    }

    fun clearOwnerSuggestions() {
        _ownerQuery.value = ""
        _ownerSuggestionsState.value = UiState.Empty
    }

    var pendingImageType: String = "FRONT"

    // Images captured before revision submission
    private val _pendingImages = MutableStateFlow<List<PendingImage>>(emptyList())
    val pendingImages: StateFlow<List<PendingImage>> = _pendingImages.asStateFlow()

    fun searchParcel(parcelCode: String) {
        if (parcelCode.isBlank()) {
            _parcelState.value = UiState.Error("VALIDATION_ERROR", "Parcel ID is required")
            return
        }
        currentParcelCode = parcelCode.trim()
        _parcelState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getParcelInfo(currentParcelCode)
            _parcelState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = {
                    UiState.Error(
                        if (it.message?.contains("not found") == true) "PARCEL_NOT_FOUND"
                        else "NETWORK_ERROR",
                        it.message ?: "Search failed"
                    )
                }
            )
        }
    }

    fun loadSearchOptions(tehsil: String? = null) {
        _searchOptionsState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getSearchOptions(tehsil)
            _searchOptionsState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("NETWORK_ERROR", it.message ?: "Failed to load options") }
            )
        }
    }

    fun searchParcels(village: String?, tehsil: String?, ownerName: String? = null, khasraNumber: String? = null, mauzaNumber: String? = null) {
        if (village.isNullOrBlank() && tehsil.isNullOrBlank() && ownerName.isNullOrBlank() && khasraNumber.isNullOrBlank() && mauzaNumber.isNullOrBlank()) {
            _searchResultsState.value = UiState.Error("VALIDATION_ERROR", "At least one search parameter is required")
            return
        }
        _searchResultsState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.searchParcels(
                village = village?.trim()?.ifBlank { null },
                tehsil = tehsil?.trim()?.ifBlank { null },
                ownerName = ownerName?.trim()?.ifBlank { null },
                khasraNumber = khasraNumber?.trim()?.ifBlank { null },
                mauzaNumber = mauzaNumber?.trim()?.ifBlank { null }
            )
            _searchResultsState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = {
                    UiState.Error(
                        if (it.message?.contains("not found") == true) "NO_RESULTS"
                        else "NETWORK_ERROR",
                        it.message ?: "Search failed"
                    )
                }
            )
        }
    }

    fun selectSearchResult(parcelCode: String) {
        currentParcelCode = parcelCode
    }

    fun resetSearchResults() {
        _searchResultsState.value = UiState.Empty
    }

    fun lookupBySerialNumber(srNo: String) {
        val parsed = srNo.trim().toIntOrNull()
        if (parsed == null || parsed <= 0) {
            _srNoLookupState.value = UiState.Error("VALIDATION_ERROR", "Enter a valid serial number")
            return
        }
        _srNoLookupState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.lookupBySerialNumber(parsed)
            _srNoLookupState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = {
                    UiState.Error(
                        if (it.message?.contains("not found") == true) "NO_RESULTS"
                        else "NETWORK_ERROR",
                        it.message ?: "Lookup failed"
                    )
                }
            )
        }
    }

    fun resetSrNoLookup() {
        _srNoLookupState.value = UiState.Empty
    }

    fun loadOriginal() {
        if (currentParcelCode.isBlank()) return
        _originalState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getOriginalSurvey(currentParcelCode)
            _originalState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("LOAD_ERROR", it.message ?: "Failed to load") }
            )
        }
    }

    fun loadCurrent() {
        if (currentParcelCode.isBlank()) return
        _surveyState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getCurrentSurvey(currentParcelCode)
            _surveyState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("LOAD_ERROR", it.message ?: "Failed to load") }
            )
        }
    }

    fun startDraft(originalFields: Map<String, Any?>, baseRevisionNo: Int) {
        _draftState.value = EditableDraft(
            parcelCode = currentParcelCode,
            baseRevisionNo = baseRevisionNo,
            fields = originalFields.toMutableMap()
        )
    }

    fun updateDraftField(field: String, value: Any?) {
        val current = _draftState.value ?: return
        val updatedFields = current.fields.toMutableMap()
        updatedFields[field] = value
        _draftState.value = current.copy(fields = updatedFields)
    }

    fun setChangeReason(reason: String) {
        val current = _draftState.value ?: return
        _draftState.value = current.copy(changeReason = reason)
    }

    fun submitRevision() {
        val draft = _draftState.value ?: return
        _submitState.value = UiState.Loading
        _isOfflineQueued.value = false

        viewModelScope.launch {
            val isOnline = connectivityObserver?.isCurrentlyConnected() ?: true

            if (isOnline && syncRepository != null) {
                submitOnline(draft)
            } else if (syncRepository != null) {
                submitOffline(draft)
            } else {
                submitOnline(draft)
            }
        }
    }

    private suspend fun submitOnline(draft: EditableDraft) {
        val result = repository.createRevision(
            parcelCode = draft.parcelCode,
            data = draft.fields,
            changeReason = draft.changeReason.ifBlank { null },
            clientUuid = draft.clientUuid,
            parentRevisionNo = draft.baseRevisionNo.takeIf { it > 0 }
        )
        _submitState.value = result.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error("SUBMIT_FAILED", it.message ?: "Submission failed") }
        )
    }

    private suspend fun submitOffline(draft: EditableDraft) {
        try {
            val queueId = syncRepository!!.enqueueRevision(
                parcelCode = draft.parcelCode,
                data = draft.fields,
                changeReason = draft.changeReason.ifBlank { null },
                clientUuid = draft.clientUuid,
                parentRevisionNo = draft.baseRevisionNo.takeIf { it > 0 }
            )

            _isOfflineQueued.value = true
            _submitState.value = UiState.Success(
                RevisionResult(
                    replayed = false,
                    revisionNo = 0,
                    status = "QUEUED",
                    diff = emptyMap(),
                    fullPayload = draft.fields
                )
            )

            appContext?.let { SyncWorker.enqueueImmediate(it) }
        } catch (e: Exception) {
            _submitState.value = UiState.Error(
                "QUEUE_FAILED",
                "Failed to queue for sync: ${e.message}"
            )
        }
    }

    fun queueImage(imageType: String, imageBytes: ByteArray, fileName: String) {
        queueGpsImage(PendingImage(
            imageType = imageType,
            originalBytes = imageBytes,
            stampedBytes = imageBytes,
            fileName = fileName,
            latitude = null,
            longitude = null,
            accuracy = null,
            areaName = null,
            capturedAt = System.currentTimeMillis(),
            pointId = null,
            qrPayload = null
        ))
    }

    fun queueGpsImage(pending: PendingImage) {
        val current = _pendingImages.value.toMutableList()
        current.removeAll { it.imageType == pending.imageType && it.pointId == pending.pointId && it.sequenceNo == pending.sequenceNo }
        current.add(pending)
        _pendingImages.value = current
    }

    fun uploadImage(imageType: String, imageBytes: ByteArray, fileName: String) {
        val revisionNo = (_submitState.value as? UiState.Success)?.data?.revisionNo
        if (revisionNo == null) {
            queueImage(imageType, imageBytes, fileName)
            return
        }
        _imageState.value = UiState.Loading

        viewModelScope.launch {
            val isOnline = connectivityObserver?.isCurrentlyConnected() ?: true

            if (isOnline) {
                uploadImageOnline(revisionNo, imageType, imageBytes, fileName)
            } else {
                uploadImageOffline(revisionNo, imageType, imageBytes, fileName)
            }
        }
    }

    fun flushPendingImages() {
        val images = _pendingImages.value
        if (images.isEmpty()) return
        val revisionNo = (_submitState.value as? UiState.Success)?.data?.revisionNo ?: return

        viewModelScope.launch {
            for (pending in images) {
                try {
                    val isOnline = connectivityObserver?.isCurrentlyConnected() ?: true
                    if (isOnline) {
                        uploadGpsImageOnline(revisionNo, pending)
                    } else {
                        uploadGpsImageOffline(revisionNo, pending)
                    }
                    _pendingImages.value = _pendingImages.value.filter { it !== pending }
                } catch (_: Exception) {
                    // image stays in the list so it can be retried
                }
            }
        }
    }

    private suspend fun uploadGpsImageOnline(revisionNo: Int, pending: PendingImage) {
        val result = repository.uploadImage(
            currentParcelCode, revisionNo, pending.imageType,
            pending.originalBytes, pending.fileName,
            latitude = pending.latitude,
            longitude = pending.longitude,
            accuracy = pending.accuracy,
            areaName = pending.areaName,
            capturedAt = pending.capturedAt,
            pointId = pending.pointId,
            sequenceNo = pending.sequenceNo,
            qrPayload = pending.qrPayload,
            stampedBytes = pending.stampedBytes
        )
        _imageState.value = result.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error("UPLOAD_FAILED", it.message ?: "Upload failed") }
        )
    }

    private suspend fun uploadGpsImageOffline(revisionNo: Int, pending: PendingImage) {
        try {
            val filePath = java.io.File(java.io.File(appContext?.cacheDir, "images"), pending.fileName).absolutePath
            java.io.File(filePath).parentFile?.mkdirs()
            java.io.File(filePath).writeBytes(pending.originalBytes)

            syncRepository?.enqueueImage(
                parcelCode = currentParcelCode,
                revisionNo = revisionNo,
                imageType = pending.imageType,
                filePath = filePath,
                clientUuid = pending.fileName
            )

            _imageState.value = UiState.Success(
                ImageInfo(
                    imageType = pending.imageType,
                    checksumSha256 = null,
                    storageKey = null,
                    contentType = "image/jpeg",
                    fileSize = pending.originalBytes.size.toLong(),
                    uploadedAt = null
                )
            )
        } catch (e: Exception) {
            _imageState.value = UiState.Error(
                "QUEUE_FAILED",
                "Failed to queue image: ${e.message}"
            )
        }
    }

    private suspend fun uploadImageOnline(
        revisionNo: Int,
        imageType: String,
        imageBytes: ByteArray,
        fileName: String
    ) {
        val result = repository.uploadImage(
            currentParcelCode, revisionNo, imageType, imageBytes, fileName
        )
        _imageState.value = result.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error("UPLOAD_FAILED", it.message ?: "Upload failed") }
        )
    }

    private suspend fun uploadImageOffline(
        revisionNo: Int,
        imageType: String,
        imageBytes: ByteArray,
        fileName: String
    ) {
        try {
            val filePath = java.io.File(java.io.File(appContext?.cacheDir, "images"), fileName).absolutePath
            java.io.File(filePath).parentFile?.mkdirs()
            java.io.File(filePath).writeBytes(imageBytes)

            syncRepository?.enqueueImage(
                parcelCode = currentParcelCode,
                revisionNo = revisionNo,
                imageType = imageType,
                filePath = filePath
            )

            _imageState.value = UiState.Success(
                ImageInfo(
                    imageType = imageType,
                    checksumSha256 = null,
                    storageKey = null,
                    contentType = "image/jpeg",
                    fileSize = imageBytes.size.toLong(),
                    uploadedAt = null
                )
            )
        } catch (e: Exception) {
            _imageState.value = UiState.Error(
                "QUEUE_FAILED",
                "Failed to queue image: ${e.message}"
            )
        }
    }

    fun loadSheet() {
        if (currentParcelCode.isBlank()) return
        _sheetState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.getSurveySheet(currentParcelCode)
            _sheetState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("LOAD_ERROR", it.message ?: "Failed to load") }
            )
        }
    }

    fun downloadPdf() {
        if (currentParcelCode.isBlank()) return
        _pdfState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.downloadPdf(currentParcelCode)
            _pdfState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("PDF_DOWNLOAD_FAILED", it.message ?: "Failed to download PDF") }
            )
        }
    }

    fun downloadExcel() {
        if (currentParcelCode.isBlank()) return
        _excelState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.downloadExcel(currentParcelCode)
            _excelState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("EXCEL_DOWNLOAD_FAILED", it.message ?: "Failed to download Excel") }
            )
        }
    }

    fun resetSubmitState() {
        _submitState.value = UiState.Empty
        _isOfflineQueued.value = false
    }

    fun resetPdfState() {
        _pdfState.value = UiState.Empty
    }

    fun resetExcelState() {
        _excelState.value = UiState.Empty
    }

    var pendingShareAction: Boolean = false
        private set

    fun requestSharePdf() {
        pendingShareAction = true
        downloadPdf()
    }

    fun requestDownloadPdf() {
        pendingShareAction = false
        downloadPdf()
    }
}

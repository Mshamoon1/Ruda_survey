package com.ruda.survey.domain.model

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val code: String, val message: String) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}

data class AuthState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val role: String? = null
)

data class ParcelInfo(
    val parcelCode: String,
    val sourceNid: Int?,
    val village: String?,
    val tehsil: String?,
    val district: String?,
    val ownerNameCurrent: String?,
    val masterLineCount: Int,
    val revisionNo: Int,
    val source: String,
    val currentRevision: CurrentRevisionInfo?,
    val khasraNumber: String? = null,
    val mauzaNumber: String? = null
)

data class CurrentRevisionInfo(
    val revisionNo: Int,
    val fullPayload: Map<String, Any?>,
    val changedFields: List<String>,
    val status: String
)

data class SurveyData(
    val parcelCode: String,
    val source: String,
    val revisionNo: Int,
    val fields: Map<String, Any?>,
    val original: Map<String, Any?>,
    val images: List<ImageInfo>,
    val surveyor: String?,
    val changedAt: String?
)

data class ImageInfo(
    val imageType: String,
    val checksumSha256: String?,
    val storageKey: String?,
    val contentType: String?,
    val fileSize: Long?,
    val uploadedAt: String?
)

data class EditableDraft(
    val parcelCode: String,
    val baseRevisionNo: Int,
    val fields: MutableMap<String, Any?>,
    val changeReason: String = "",
    val clientUuid: String = java.util.UUID.randomUUID().toString()
) {
    fun computeChanges(original: Map<String, Any?>): Map<String, Map<String, Any?>> {
        val diff = mutableMapOf<String, Map<String, Any?>>()
        for ((key, newValue) in fields) {
            val oldValue = original[key]
            if (oldValue != newValue) {
                diff[key] = mapOf("old" to oldValue, "new" to newValue)
            }
        }
        return diff
    }
}

data class RevisionResult(
    val replayed: Boolean,
    val revisionNo: Int,
    val status: String,
    val diff: Map<String, Map<String, Any?>>,
    val fullPayload: Map<String, Any?>,
    val eventId: Long = System.nanoTime()
)

data class SearchParcel(
    val parcelCode: String,
    val khasraNumber: String?,
    val mauzaNumber: String?,
    val ownerName: String?,
    val village: String?,
    val tehsil: String?,
    val district: String?,
    val sourceNid: Int?
)

data class OwnerSuggestion(
    val ownerName: String,
    val recordCount: Int
)

data class SrNoLookupResult(
    val parcelCode: String,
    val village: String?,
    val tehsil: String?,
    val district: String?,
    val ownerName: String?,
    val masterLineCount: Int
)

data class PendingImage(
    val imageType: String,
    val originalBytes: ByteArray,
    val stampedBytes: ByteArray,
    val fileName: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val areaName: String?,
    val capturedAt: Long,
    val pointId: String?,
    val sequenceNo: Int = 1,
    val qrPayload: String?
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = fileName.hashCode()
}

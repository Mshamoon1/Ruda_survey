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

data class SurveyItem(
    val id: String = "",
    val srNo: Int = 0,
    val parcelId: String = "",
    val rd: String = "",
    val pkg: String = "",
    val village: String = "",
    val status: String = "",
    val structuralName: String = "",
    val natureOfConstruction: String = "",
    val imgOne: String = "",
    val imgTwo: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val ownerName: String = "",
    val fName: String = "",
    val cnic: String = "",
    val khasraNo: String = "",
    val phone: String = "",
    val landOwnerDoc: String = "",
    val electricityConnectionName: String = "",
    val landArea: String = "",
    val length: String = "",
    val width: String = "",
    val area: String = "",
    val image1Bytes: ByteArray? = null,
    val image2Bytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = id.hashCode()
}

data class EditableDraft(
    val surveyItem: SurveyItem,
    val clientUuid: String = java.util.UUID.randomUUID().toString()
)

data class SearchParcel(
    val id: String,
    val srNo: Int,
    val village: String?,
    val ownerName: String?,
    val structuralName: String?,
    val natureOfConstruction: String?
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
    val pointId: String?
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = fileName.hashCode()
}

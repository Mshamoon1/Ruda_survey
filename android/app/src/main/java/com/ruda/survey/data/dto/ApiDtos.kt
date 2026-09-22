package com.ruda.survey.data.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto?,
    val token: String?
)

data class UserDto(
    @SerializedName("_id")
    val id: String,
    val user_name: String,
    val email: String,
    val role: String,
    @SerializedName("__v")
    val version: Int
)

data class SurveyDataWrapper(
    val success: Boolean,
    val message: String,
    val data: Any?
)

data class SurveyListResponse(
    val success: Boolean = false,
    val message: String = "",
    val data: List<SurveyItemDto> = emptyList(),
    val total: Int? = null,
    val count: Int? = null,
    @SerializedName("totalSurveys")
    val totalSurveys: Int? = null,
    @SerializedName("total_surveys")
    val totalSurveysUnderscore: Int? = null
) {
    fun getTotalCount(): Int {
        val explicitTotal = total ?: count ?: totalSurveys ?: totalSurveysUnderscore
        return explicitTotal ?: data.size
    }
}

data class SurveyItemDto(
    @SerializedName("_id")
    val id: String = "",
    @SerializedName("sr_no")
    val sr_no: Int = 0,
    @SerializedName("parcel_id")
    val parcel_id: String? = null,
    val rd: String? = null,
    val pkg: String? = null,
    val village: String? = null,
    val status: String? = null,
    @SerializedName("stractural_name")
    val structuralName: String? = null,
    @SerializedName("nature_of_construction")
    val nature_of_construction: String? = null,
    val imgOne: String? = null,
    val imgTwo: String? = null,
    val coordinates: Any? = null,
    @SerializedName(value = "lat", alternate = ["latitude"])
    val lat: Any? = null,
    @SerializedName(value = "lng", alternate = ["longitude", "long"])
    val lng: Any? = null,
    val identification: IdentificationDto? = null,
    val covered_area: CoveredAreaDto? = null,
    @SerializedName("__v")
    val version: Int = 0
)

data class CoordinatesDto(
    @SerializedName(value = "lat", alternate = ["latitude"])
    val lat: Double? = null,
    @SerializedName(value = "lng", alternate = ["longitude", "long"])
    val lng: Double? = null
)

data class IdentificationDto(
    val owner_name: String?,
    val f_name: String?,
    val cnic: String?,
    val khasra_no: String?,
    val phone: String?,
    val land_owner_doc: String?,
    val electricity_connection_name: String?,
    val land_area: String?
)

data class CoveredAreaDto(
    val length: String?,
    val width: String?,
    val area: String?
)

data class CreateSurveyResponse(
    val success: Boolean,
    val message: String,
    val data: SurveyItemDto?
)

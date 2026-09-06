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
    val success: Boolean,
    val message: String,
    val data: List<SurveyItemDto>
)

data class SurveyItemDto(
    @SerializedName("_id")
    val id: String,
    val sr_no: Int,
    val parcel_id: String?,
    val rd: String?,
    val pkg: String?,
    val village: String?,
    val status: String?,
    @SerializedName("stractural_name")
    val structuralName: String?,
    val nature_of_construction: String?,
    val imgOne: String?,
    val imgTwo: String?,
    val coordinates: CoordinatesDto?,
    val identification: IdentificationDto?,
    val covered_area: CoveredAreaDto?,
    @SerializedName("__v")
    val version: Int = 0
)

data class CoordinatesDto(
    val lat: Double?,
    val lng: Double?
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

package com.ruda.survey.data.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserDto
)

data class UserDto(
    val id: Long,
    val username: String,
    val first_name: String?,
    val last_name: String?,
    val role: String
)

data class RefreshRequest(val refresh: String)
data class RefreshResponse(val access: String)
data class LogoutRequest(val refresh: String)

data class ParcelLookupResponse(
    val parcel: ParcelHeaderDto,
    val original: Map<String, Any?>,
    val current_revision: RevisionSummaryDto?,
    val revision_no: Int,
    val source: String
)

data class ParcelHeaderDto(
    val parcel_code: String,
    val source_nid: Int?,
    val village: String?,
    val tehsil: String?,
    val district: String?,
    val owner_name_current: String?,
    val khasra_number: String? = null,
    val mauza_number: String? = null
)

data class RevisionSummaryDto(
    val revision_no: Int,
    val full_payload: Map<String, Any?>,
    val changed_fields: List<String>,
    val status: String
)

data class CurrentDataResponse(
    val parcel: Map<String, Any?>,
    val source: String,
    val revision_no: Int,
    val data: Map<String, Any?>
)

data class RevisionCreateRequest(
    val client_uuid: String,
    val data: Map<String, Any?>,
    val change_reason: String? = null,
    val device_info: Map<String, String>? = null,
    val parent_revision_no: Int? = null
)

data class RevisionCreateResponse(
    val replayed: Boolean,
    val id: Long,
    val revision_no: Int,
    val status: String,
    val client_uuid: String,
    val accepted_at: String?,
    val diff: Map<String, Map<String, Any?>>,
    val full_payload: Map<String, Any?>
)

data class RevisionListResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<RevisionListItem>
)

data class RevisionListItem(
    val revision_no: Int,
    val status: String,
    val changed_by: String,
    val changed_at: String?,
    val created_at: String?,
    val accepted_at: String?,
    val change_reason: String?,
    val client_uuid: String,
    val changes: Map<String, Map<String, Any?>>,
    val images: List<ImageDto>
)

data class ImageDto(
    val image_type: String,
    val checksum_sha256: String?,
    val file_path: String?,
    val content_type: String?,
    val file_size: Long?,
    val uploaded_at: String?
)

data class ImageUploadResponse(
    val id: Long,
    val image_type: String,
    val checksum_sha256: String,
    val storage_key: String,
    val content_type: String,
    val file_size: Long,
    val width_px: Int,
    val height_px: Int
)

data class SheetResponse(
    val parcel: ParcelHeaderDto,
    val source: String,
    val current_revision_no: Int?,
    val surveyor: String?,
    val changed_at: String?,
    val accepted_at: String?,
    val images: List<ImageDto>,
    val fields: Map<String, Any?>,
    val original: Map<String, Any?>
)

data class StatusChangeRequest(
    val target: String,
    val reason: String? = null
)

data class ApiErrorResponse(
    val error: ApiErrorDetail
)

data class ApiErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, Any?>?
)

data class SearchRequest(
    val khasra_number: String? = null,
    val mauza_number: String? = null
)

data class SearchResult(
    val parcel_code: String,
    val khasra_number: String?,
    val mauza_number: String?,
    val owner_name: String?,
    val village: String?,
    val tehsil: String?,
    val district: String?,
    val source_nid: Int?
)

data class SearchResponse(
    val results: List<SearchResult>,
    val count: Int
)

data class SearchOptionsResponse(
    val villages: List<String>,
    val tehsils: List<String>
)

data class OwnerSuggestionDto(
    val owner_name: String,
    val record_count: Int
)

data class OwnerSuggestionsResponse(
    val results: List<OwnerSuggestionDto>
)

data class SrNoLookupResultDto(
    val parcel_code: String,
    val village: String?,
    val tehsil: String?,
    val district: String?,
    val owner_name: String?,
    val master_line_count: Int
)

data class SrNoLookupResponse(
    val sr_no: Int,
    val parcels: List<SrNoLookupResultDto>
)

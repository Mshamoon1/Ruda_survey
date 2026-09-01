package com.ruda.survey.data.remote

import com.ruda.survey.data.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SurveyApi {

    @POST("auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/refresh/")
    suspend fun refresh(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("auth/logout/")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("auth/me/")
    suspend fun me(): Response<UserDto>

    @GET("surveys/parcel/{parcel_code}/")
    suspend fun parcelLookup(@Path("parcel_code") parcelCode: String): Response<ParcelLookupResponse>

    @GET("surveys/{parcel_code}/original/")
    suspend fun originalData(@Path("parcel_code") parcelCode: String): Response<Map<String, Any>>

    @GET("surveys/{parcel_code}/current/")
    suspend fun currentData(@Path("parcel_code") parcelCode: String): Response<CurrentDataResponse>

    @GET("surveys/{parcel_code}/revisions/")
    suspend fun revisionHistory(
        @Path("parcel_code") parcelCode: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<RevisionListResponse>

    @POST("surveys/{parcel_code}/revisions/")
    suspend fun createRevision(
        @Path("parcel_code") parcelCode: String,
        @Body request: RevisionCreateRequest
    ): Response<RevisionCreateResponse>

    @Multipart
    @POST("surveys/{parcel_code}/revisions/{revision_no}/images/")
    suspend fun uploadImage(
        @Path("parcel_code") parcelCode: String,
        @Path("revision_no") revisionNo: Int,
        @Part("image_type") imageType: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("latitude") latitude: RequestBody? = null,
        @Part("longitude") longitude: RequestBody? = null,
        @Part("accuracy") accuracy: RequestBody? = null,
        @Part("area_name") areaName: RequestBody? = null,
        @Part("captured_at") capturedAt: RequestBody? = null,
        @Part("point_id") pointId: RequestBody? = null,
        @Part("sequence_no") sequenceNo: RequestBody? = null,
        @Part("qr_payload") qrPayload: RequestBody? = null
    ): Response<ImageUploadResponse>

    @POST("surveys/{parcel_code}/revisions/{revision_no}/status/")
    suspend fun changeRevisionStatus(
        @Path("parcel_code") parcelCode: String,
        @Path("revision_no") revisionNo: Int,
        @Body request: StatusChangeRequest
    ): Response<Map<String, Any>>

    @GET("surveys/{parcel_code}/sheet/")
    suspend fun surveySheet(
        @Path("parcel_code") parcelCode: String
    ): Response<SheetResponse>

    @GET("surveys/{parcel_code}/pdf/")
    @Streaming
    suspend fun surveyPdf(
        @Path("parcel_code") parcelCode: String
    ): Response<okhttp3.ResponseBody>

    @GET("surveys/{parcel_code}/export/")
    @Streaming
    suspend fun surveyExport(
        @Path("parcel_code") parcelCode: String
    ): Response<okhttp3.ResponseBody>

    @GET("surveys/search/")
    suspend fun searchSurveys(
        @Query("village") village: String? = null,
        @Query("tehsil") tehsil: String? = null,
        @Query("owner_name") ownerName: String? = null,
        @Query("khasra_number") khasraNumber: String? = null,
        @Query("mauza_number") mauzaNumber: String? = null
    ): Response<SearchResponse>

    @GET("surveys/search-options/")
    suspend fun searchOptions(
        @Query("tehsil") tehsil: String? = null
    ): Response<SearchOptionsResponse>

    @GET("surveys/owners/")
    suspend fun searchOwners(
        @Query("q") query: String
    ): Response<OwnerSuggestionsResponse>

    @GET("surveys/sr-no/{sr_no}/")
    suspend fun lookupBySrNo(
        @Path("sr_no") srNo: Int
    ): Response<SrNoLookupResponse>
}

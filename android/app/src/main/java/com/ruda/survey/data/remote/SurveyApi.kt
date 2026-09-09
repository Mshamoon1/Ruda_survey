package com.ruda.survey.data.remote

import com.ruda.survey.data.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SurveyApi {

    @GET("surveys")
    suspend fun getAllSurveys(): Response<SurveyListResponse>

    @GET("survey/{id}")
    suspend fun getSurveyById(@Path("id") id: String): Response<SurveyDataWrapper>

    @GET("sr_no/{sr_no}")
    suspend fun getSurveyBySrNo(@Path("sr_no") srNo: Int): Response<SurveyDataWrapper>

    @Multipart
    @POST("survey")
    suspend fun createSurvey(
        @Part("sr_no") srNo: RequestBody,
        @Part("parcel_id") parcelId: RequestBody?,
        @Part("rd") rd: RequestBody?,
        @Part("pkg") pkg: RequestBody?,
        @Part("lat") lat: RequestBody?,
        @Part("lng") lng: RequestBody?,
        @Part("village") village: RequestBody?,
        @Part("owner_name") ownerName: RequestBody?,
        @Part("cnic") cnic: RequestBody?,
        @Part("f_name") fName: RequestBody?,
        @Part("khasra_no") khasraNo: RequestBody?,
        @Part("phone") phone: RequestBody?,
        @Part("electricity_connection_name") electricity: RequestBody?,
        @Part("land_area") landArea: RequestBody?,
        @Part("status") status: RequestBody?,
        @Part("stractural_name") structuralName: RequestBody?,
        @Part("length") length: RequestBody?,
        @Part("width") width: RequestBody?,
        @Part("area") area: RequestBody?,
        @Part("nature_of_construction") natureOfConstruction: RequestBody?,
        @Part landOwnerDoc: MultipartBody.Part?,
        @Part imgOne: MultipartBody.Part?,
        @Part imgTwo: MultipartBody.Part?
    ): Response<CreateSurveyResponse>

    @Multipart
    @PUT("survey/{id}")
    suspend fun updateSurvey(
        @Path("id") id: String,
        @Part("sr_no") srNo: RequestBody,
        @Part("parcel_id") parcelId: RequestBody?,
        @Part("rd") rd: RequestBody?,
        @Part("pkg") pkg: RequestBody?,
        @Part("lat") lat: RequestBody?,
        @Part("lng") lng: RequestBody?,
        @Part("village") village: RequestBody?,
        @Part("owner_name") ownerName: RequestBody?,
        @Part("cnic") cnic: RequestBody?,
        @Part("f_name") fName: RequestBody?,
        @Part("khasra_no") khasraNo: RequestBody?,
        @Part("phone") phone: RequestBody?,
        @Part("electricity_connection_name") electricity: RequestBody?,
        @Part("land_area") landArea: RequestBody?,
        @Part("status") status: RequestBody?,
        @Part("stractural_name") structuralName: RequestBody?,
        @Part("nature_of_construction") natureOfConstruction: RequestBody?,
        @Part("length") length: RequestBody?,
        @Part("width") width: RequestBody?,
        @Part("area") area: RequestBody?,
        @Part landOwnerDoc: MultipartBody.Part?,
        @Part imgOne: MultipartBody.Part?,
        @Part imgTwo: MultipartBody.Part?
    ): Response<CreateSurveyResponse>

    @DELETE("survey/{id}")
    suspend fun deleteSurvey(@Path("id") id: String): Response<SurveyDataWrapper>
}

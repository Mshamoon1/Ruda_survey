package com.ruda.survey.data.local

import androidx.room.*

@Entity(tableName = "cached_parcels", primaryKeys = ["parcel_code"])
data class CachedParcel(
    @ColumnInfo(name = "parcel_code") val parcelCode: String,
    @ColumnInfo(name = "source_nid") val sourceNid: Int?,
    @ColumnInfo(name = "village") val village: String?,
    @ColumnInfo(name = "tehsil") val tehsil: String?,
    @ColumnInfo(name = "district") val district: String?,
    @ColumnInfo(name = "owner_name_current") val ownerNameCurrent: String?,
    @ColumnInfo(name = "master_line_count") val masterLineCount: Int = 0,
    @ColumnInfo(name = "revision_no") val revisionNo: Int = 0,
    @ColumnInfo(name = "source") val source: String = "master",
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_surveys",
    primaryKeys = ["parcel_code", "survey_type"]
)
data class CachedSurvey(
    @ColumnInfo(name = "parcel_code") val parcelCode: String,
    @ColumnInfo(name = "survey_type") val surveyType: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "revision_no") val revisionNo: Int,
    @ColumnInfo(name = "fields_json") val fieldsJson: String,
    @ColumnInfo(name = "images_json") val imagesJson: String = "[]",
    @ColumnInfo(name = "surveyor") val surveyor: String? = null,
    @ColumnInfo(name = "changed_at") val changedAt: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "draft_surveys",
    primaryKeys = ["parcel_code"]
)
data class DraftSurvey(
    @ColumnInfo(name = "parcel_code") val parcelCode: String,
    @ColumnInfo(name = "base_revision_no") val baseRevisionNo: Int,
    @ColumnInfo(name = "fields_json") val fieldsJson: String,
    @ColumnInfo(name = "change_reason") val changeReason: String = "",
    @ColumnInfo(name = "client_uuid") val clientUuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_submissions")
data class PendingSubmission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "parcel_code") val parcelCode: String,
    @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "change_reason") val changeReason: String? = null,
    @ColumnInfo(name = "parent_revision_no") val parentRevisionNo: Int? = null,
    @ColumnInfo(name = "status") val status: String = "pending",
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_images")
data class CachedImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "parcel_code") val parcelCode: String,
    @ColumnInfo(name = "revision_no") val revisionNo: Int,
    @ColumnInfo(name = "image_type") val imageType: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "checksum_sha256") val checksumSha256: String? = null,
    @ColumnInfo(name = "content_type") val contentType: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long? = null,
    @ColumnInfo(name = "uploaded") val uploaded: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

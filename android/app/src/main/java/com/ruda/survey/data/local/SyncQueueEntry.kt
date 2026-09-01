package com.ruda.survey.data.local

import androidx.room.*

@Entity(tableName = "sync_queue")
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "parcel_code") val parcelCode: String,
    @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "revision_no") val revisionNo: Int? = null,
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    @ColumnInfo(name = "image_type") val imageType: String? = null,
    @ColumnInfo(name = "status") val status: String = "PENDING",
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CONFLICT = "CONFLICT"

        const val OP_REVISION_CREATE = "REVISION_CREATE"
        const val OP_IMAGE_UPLOAD = "IMAGE_UPLOAD"
    }
}

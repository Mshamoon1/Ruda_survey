package com.ruda.survey.demo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "demo_images")
data class DemoImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parcel_code: String,
    val revision_no: Int,
    val image_type: String,
    val file_path: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val area_name: String? = null,
    val captured_at: Long? = null,
    val content_type: String? = "image/jpeg",
    val file_size: Long? = null,
    val qr_payload: String? = null,
    val created_at: Long = System.currentTimeMillis()
)

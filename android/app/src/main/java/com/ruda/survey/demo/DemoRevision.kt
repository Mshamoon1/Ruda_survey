package com.ruda.survey.demo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "demo_revisions")
data class DemoRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parcel_code: String,
    val revision_no: Int,
    val full_payload_json: String,
    val changes_json: String = "{}",
    val change_reason: String = "",
    val client_uuid: String = "",
    val status: String = "synced",
    val created_at: Long = System.currentTimeMillis()
)

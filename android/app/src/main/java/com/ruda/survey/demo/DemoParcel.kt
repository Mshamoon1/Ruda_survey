package com.ruda.survey.demo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "demo_parcels")
data class DemoParcel(
    @PrimaryKey val parcel_code: String,
    val source_nid: Int? = null,
    val village: String? = null,
    val tehsil: String? = null,
    val district: String? = null,
    val owner_name_current: String? = null,
    val khasra_number: String? = null,
    val mauza_number: String? = null,
    val master_line_count: Int = 1,
    val current_revision_no: Int = 0,
    val source: String = "master",
    val original_fields_json: String = "{}",
    val current_fields_json: String = "{}"
)

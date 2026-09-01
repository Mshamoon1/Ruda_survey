package com.ruda.survey.demo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DemoDataSeeder(private val context: Context) {

    private val db = DemoDatabase.getInstance(context)
    private val dao = db.demoDao()
    private val gson = Gson()

    suspend fun seedIfNeeded() {
        if (dao.getParcelCount() > 0) return
        seedParcels()
    }

    suspend fun reset() {
        dao.deleteAll()
        seedParcels()
    }

    private suspend fun seedParcels() {
        val parcels = buildDemoParcels()
        dao.insertParcels(parcels)
    }

    private fun buildDemoParcels(): List<DemoParcel> {
        val records = mutableListOf<DemoParcel>()

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00005", sourceNid = 1,
            village = "Arya Nagar", tehsil = "Ferozwala", district = "Sheikhupura",
            ownerName = "Rana Bashir", khasra = "917e7e37", mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00005", "source_nid" to 1,
                "sr_no" to 1, "chainage_m" to 150.00, "affected_persons_count" to 1,
                "phase_code" to "Ph#1", "latitude" to 31.70385, "longitude" to 74.41594,
                "project_component" to "Dam", "owner_name" to "Rana Bashir",
                "father_name" to "Muhammad Buksh", "caste" to "Rajput",
                "village" to "Arya Nagar", "tehsil" to "Ferozwala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Hawaili i.e. rooms, verenda", "structure_count" to 4,
                "tenure_status" to "Owner", "length_ft" to 27.10, "width_ft" to 31.70,
                "area_value" to 859.07, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 1.889954,
                "impact_extent" to "Major Impact", "river_location" to "Right",
                "in_row_yn" to "Yes", "cl_offset_m" to 439, "extra_note" to null,
                "khasra_number" to "917e7e37", "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00006", sourceNid = 2,
            village = "Arya Nagar", tehsil = "Ferozwala", district = "Sheikhupura",
            ownerName = "Rana Bashir", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00006", "source_nid" to 2,
                "sr_no" to 2, "chainage_m" to 150.00, "affected_persons_count" to null,
                "phase_code" to "Ph#1", "latitude" to 31.70385, "longitude" to 74.41594,
                "project_component" to "Dam", "owner_name" to "Rana Bashir",
                "father_name" to "Muhammad Buksh", "caste" to "Rajput",
                "village" to "Arya Nagar", "tehsil" to "Ferozwala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Bathroom", "structure_count" to 2,
                "tenure_status" to "Owner", "length_ft" to 3.50, "width_ft" to 3.50,
                "area_value" to 12.25, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 0.026950,
                "impact_extent" to "Major Impact", "river_location" to "Right",
                "in_row_yn" to "Yes", "cl_offset_m" to 439, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00007", sourceNid = 3,
            village = "Arya Nagar", tehsil = "Ferozwala", district = "Sheikhupura",
            ownerName = "Rana Bashir", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00007", "source_nid" to 3,
                "sr_no" to 3, "chainage_m" to 150.00, "affected_persons_count" to null,
                "phase_code" to "Ph#1", "latitude" to 31.70385, "longitude" to 74.41594,
                "project_component" to "Dam", "owner_name" to "Rana Bashir",
                "father_name" to "Muhammad Buksh", "caste" to "Rajput",
                "village" to "Arya Nagar", "tehsil" to "Ferozwala", "district" to "Sheikhupura",
                "ownership_documents" to "-", "structure_status" to "Cattle Farm",
                "structure_name" to "Electric motor pump", "structure_count" to 2,
                "tenure_status" to "Owner", "length_ft" to 0.00, "width_ft" to 0.00,
                "area_value" to 0.00, "construction_nature" to "-",
                "unit_rate_rs" to 30000, "compensation_million" to 0.060000,
                "impact_extent" to "Major Impact", "river_location" to "Right",
                "in_row_yn" to "Yes", "cl_offset_m" to 439, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00008", sourceNid = 4802,
            village = "Ratini Wal", tehsil = "Lahore City", district = "Lahore",
            ownerName = "Mr. M. Younus", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00008", "source_nid" to 4802,
                "sr_no" to 4, "chainage_m" to 3750.00, "affected_persons_count" to 2,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. M. Younus",
                "father_name" to "M. Safi", "caste" to null,
                "village" to "Ratini Wal", "tehsil" to "Lahore City", "district" to "Lahore",
                "ownership_documents" to "Intiqal", "structure_status" to "Residential",
                "structure_name" to "House", "structure_count" to 1,
                "tenure_status" to "Owner", "length_ft" to 45.00, "width_ft" to 34.00,
                "area_value" to 1530.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 3.366000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00009", sourceNid = 5264,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. M, Shabir", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00009", "source_nid" to 5264,
                "sr_no" to 5, "chainage_m" to 3150.00, "affected_persons_count" to 3,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. M, Shabir",
                "father_name" to "M.Ali", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 10,
                "tenure_status" to "Owner", "length_ft" to 53.00, "width_ft" to 55.00,
                "area_value" to 2915.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 6.413000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00010", sourceNid = 5265,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Maqsood Ali", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00010", "source_nid" to 5265,
                "sr_no" to 6, "chainage_m" to 3150.00, "affected_persons_count" to 4,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Maqsood Ali",
                "father_name" to "Akber Ali", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 9,
                "tenure_status" to "Owner", "length_ft" to 20.00, "width_ft" to 30.00,
                "area_value" to 600.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 1.320000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00011", sourceNid = 5266,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Safdar Ali", khasra = "0082373", mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00011", "source_nid" to 5266,
                "sr_no" to 7, "chainage_m" to 3150.00, "affected_persons_count" to 5,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Safdar Ali",
                "father_name" to "Akbar Ali", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 8,
                "tenure_status" to "Owner", "length_ft" to 36.00, "width_ft" to 74.00,
                "area_value" to 2664.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 5.860800,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to "0082373", "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00012", sourceNid = 5267,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Mehmood AlI", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00012", "source_nid" to 5267,
                "sr_no" to 8, "chainage_m" to 3100.00, "affected_persons_count" to 6,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Mehmood AlI",
                "father_name" to "Akbar Ali", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 5,
                "tenure_status" to "Owner", "length_ft" to 34.00, "width_ft" to 30.00,
                "area_value" to 1020.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 2.244000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00013", sourceNid = 5268,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. M. Saleem", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00013", "source_nid" to 5268,
                "sr_no" to 9, "chainage_m" to 3000.00, "affected_persons_count" to 7,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. M. Saleem",
                "father_name" to "M.Ali", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 13,
                "tenure_status" to "Owner", "length_ft" to 65.00, "width_ft" to 80.00,
                "area_value" to 5200.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 11.440000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00014", sourceNid = 5269,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Niamat Ali", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00014", "source_nid" to 5269,
                "sr_no" to 10, "chainage_m" to 3000.00, "affected_persons_count" to 8,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Niamat Ali",
                "father_name" to "Kushi Muhammad", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 12,
                "tenure_status" to "Owner", "length_ft" to 65.00, "width_ft" to 38.00,
                "area_value" to 2470.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 5.434000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00015", sourceNid = 5270,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Salamt Ali", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00015", "source_nid" to 5270,
                "sr_no" to 11, "chainage_m" to 3000.00, "affected_persons_count" to 9,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Salamt Ali",
                "father_name" to "Kushi Muhammad", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 8,
                "tenure_status" to "Owner", "length_ft" to 43.00, "width_ft" to 38.00,
                "area_value" to 1634.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 3.594800,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00016", sourceNid = 5271,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Mushtaq", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00016", "source_nid" to 5271,
                "sr_no" to 12, "chainage_m" to 3000.00, "affected_persons_count" to 10,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Mushtaq",
                "father_name" to "Ismail", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 7,
                "tenure_status" to "Owner", "length_ft" to 60.00, "width_ft" to 60.00,
                "area_value" to 3600.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 7.920000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00017", sourceNid = 5272,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Meraj din", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00017", "source_nid" to 5272,
                "sr_no" to 13, "chainage_m" to 3000.00, "affected_persons_count" to 11,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Meraj din",
                "father_name" to "Ismail", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 4,
                "tenure_status" to "Owner", "length_ft" to 17.00, "width_ft" to 17.00,
                "area_value" to 289.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 0.635800,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00018", sourceNid = 5273,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Siraj din", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00018", "source_nid" to 5273,
                "sr_no" to 14, "chainage_m" to 3000.00, "affected_persons_count" to 12,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Siraj din",
                "father_name" to "Ismail", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 8,
                "tenure_status" to "Owner", "length_ft" to 70.00, "width_ft" to 142.00,
                "area_value" to 9940.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 21.868000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00019", sourceNid = 5274,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Shabaz", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00019", "source_nid" to 5274,
                "sr_no" to 15, "chainage_m" to 3000.00, "affected_persons_count" to 13,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Shabaz",
                "father_name" to "Mushtaq", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 4,
                "tenure_status" to "Owner", "length_ft" to 25.00, "width_ft" to 42.00,
                "area_value" to 1050.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 2.310000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00020", sourceNid = 5275,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Safdar", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00020", "source_nid" to 5275,
                "sr_no" to 16, "chainage_m" to 3000.00, "affected_persons_count" to 14,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Safdar",
                "father_name" to "Ismail", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 6,
                "tenure_status" to "Owner", "length_ft" to 60.00, "width_ft" to 50.00,
                "area_value" to 3000.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 6.600000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00021", sourceNid = 5276,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Qurban Ali", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00021", "source_nid" to 5276,
                "sr_no" to 17, "chainage_m" to 3000.00, "affected_persons_count" to 15,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Qurban Ali",
                "father_name" to "M.Ibrahim", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 11,
                "tenure_status" to "Owner", "length_ft" to 50.00, "width_ft" to 95.00,
                "area_value" to 4750.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 10.450000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00022", sourceNid = 5277,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Asghar Ali (late)", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00022", "source_nid" to 5277,
                "sr_no" to 18, "chainage_m" to 3000.00, "affected_persons_count" to 16,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Asghar Ali (late)",
                "father_name" to "M.Ibrahim", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 3,
                "tenure_status" to "Owner", "length_ft" to 100.00, "width_ft" to 36.00,
                "area_value" to 3600.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 7.920000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00023", sourceNid = 5278,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Akbar Ali", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00023", "source_nid" to 5278,
                "sr_no" to 19, "chainage_m" to 3000.00, "affected_persons_count" to 17,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Akbar Ali",
                "father_name" to "M.Ibrahim", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 3,
                "tenure_status" to "Owner", "length_ft" to 15.00, "width_ft" to 80.00,
                "area_value" to 1200.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 2.640000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        records.add(makeParcel(
            parcelCode = "RUDA-P14-R00024", sourceNid = 5279,
            village = "Mralpaar", tehsil = "Ferozewala", district = "Sheikhupura",
            ownerName = "Mr. Anwar Ali", khasra = null, mauza = null,
            originalFields = mapOf(
                "parcel_code" to "RUDA-P14-R00024", "source_nid" to 5279,
                "sr_no" to 20, "chainage_m" to 3000.00, "affected_persons_count" to 18,
                "phase_code" to "Ph#1", "latitude" to null, "longitude" to null,
                "project_component" to "River Channelization", "owner_name" to "Mr. Anwar Ali",
                "father_name" to "M.Ibrahim", "caste" to null,
                "village" to "Mralpaar", "tehsil" to "Ferozewala", "district" to "Sheikhupura",
                "ownership_documents" to "Intiqal", "structure_status" to "Cattle Farm",
                "structure_name" to "Cattle Shed", "structure_count" to 3,
                "tenure_status" to "Owner", "length_ft" to 36.00, "width_ft" to 100.00,
                "area_value" to 3600.00, "construction_nature" to "Semi-Pacca",
                "unit_rate_rs" to 2200, "compensation_million" to 7.920000,
                "impact_extent" to "Major Impact", "river_location" to "Left",
                "in_row_yn" to "Yes", "cl_offset_m" to null, "extra_note" to null,
                "khasra_number" to null, "mauza_number" to null
            )
        ))

        return records
    }

    private fun makeParcel(
        parcelCode: String,
        sourceNid: Int?,
        village: String,
        tehsil: String,
        district: String,
        ownerName: String,
        khasra: String?,
        mauza: String?,
        originalFields: Map<String, Any?>
    ): DemoParcel {
        val json = gson.toJson(originalFields)
        return DemoParcel(
            parcel_code = parcelCode,
            source_nid = sourceNid,
            village = village,
            tehsil = tehsil,
            district = district,
            owner_name_current = ownerName,
            khasra_number = khasra,
            mauza_number = mauza,
            master_line_count = 1,
            current_revision_no = 0,
            source = "master",
            original_fields_json = json,
            current_fields_json = json
        )
    }
}

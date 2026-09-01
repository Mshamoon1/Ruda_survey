package com.ruda.survey.demo

import androidx.room.*

@Dao
interface DemoDao {

    @Query("SELECT * FROM demo_parcels")
    suspend fun getAllParcels(): List<DemoParcel>

    @Query("SELECT * FROM demo_parcels WHERE parcel_code = :parcelCode")
    suspend fun getParcel(parcelCode: String): DemoParcel?

    @Query("SELECT COUNT(*) FROM demo_parcels")
    suspend fun getParcelCount(): Int

    @Query("SELECT * FROM demo_parcels WHERE (:village IS NULL OR village = :village) AND (:tehsil IS NULL OR tehsil = :tehsil) AND (:ownerName IS NULL OR owner_name_current LIKE '%' || :ownerName || '%') AND (:khasraNumber IS NULL OR khasra_number = :khasraNumber) AND (:mauzaNumber IS NULL OR mauza_number = :mauzaNumber)")
    suspend fun searchParcels(
        village: String?,
        tehsil: String?,
        ownerName: String?,
        khasraNumber: String?,
        mauzaNumber: String?
    ): List<DemoParcel>

    @Query("SELECT DISTINCT village FROM demo_parcels WHERE village IS NOT NULL ORDER BY village")
    suspend fun getVillages(): List<String>

    @Query("SELECT DISTINCT tehsil FROM demo_parcels WHERE tehsil IS NOT NULL ORDER BY tehsil")
    suspend fun getTehsils(): List<String>

    @Query("SELECT DISTINCT village FROM demo_parcels WHERE village IS NOT NULL AND tehsil = :tehsil ORDER BY village")
    suspend fun getVillagesByTehsil(tehsil: String): List<String>

    @Query("SELECT DISTINCT owner_name_current FROM demo_parcels WHERE owner_name_current IS NOT NULL AND owner_name_current != '' AND owner_name_current LIKE '%' || :query || '%' ORDER BY owner_name_current LIMIT 15")
    suspend fun searchOwnerNames(query: String): List<String>

    @Query("SELECT COUNT(*) FROM demo_parcels WHERE owner_name_current = :ownerName")
    suspend fun getOwnerRecordCount(ownerName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcel(parcel: DemoParcel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcels(parcels: List<DemoParcel>)

    @Update
    suspend fun updateParcel(parcel: DemoParcel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: DemoRevision): Long

    @Query("SELECT * FROM demo_revisions WHERE parcel_code = :parcelCode ORDER BY revision_no DESC")
    suspend fun getRevisions(parcelCode: String): List<DemoRevision>

    @Query("SELECT * FROM demo_revisions WHERE parcel_code = :parcelCode ORDER BY revision_no DESC LIMIT 1")
    suspend fun getLatestRevision(parcelCode: String): DemoRevision?

    @Query("SELECT MAX(revision_no) FROM demo_revisions WHERE parcel_code = :parcelCode")
    suspend fun getMaxRevisionNo(parcelCode: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: DemoImage): Long

    @Query("SELECT * FROM demo_images WHERE parcel_code = :parcelCode AND revision_no = :revisionNo")
    suspend fun getImages(parcelCode: String, revisionNo: Int): List<DemoImage>

    @Query("SELECT * FROM demo_images WHERE parcel_code = :parcelCode")
    suspend fun getAllImagesForParcel(parcelCode: String): List<DemoImage>

    @Query("DELETE FROM demo_parcels")
    suspend fun deleteAllParcels()

    @Query("DELETE FROM demo_revisions")
    suspend fun deleteAllRevisions()

    @Query("DELETE FROM demo_images")
    suspend fun deleteAllImages()

    @Transaction
    suspend fun deleteAll() {
        deleteAllImages()
        deleteAllRevisions()
        deleteAllParcels()
    }
}

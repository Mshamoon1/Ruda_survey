package com.ruda.survey.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {
    @Query("SELECT * FROM cached_surveys WHERE surveyor = :owner ORDER BY fetched_at DESC")
    suspend fun getSurveysForUser(owner: String): List<CachedSurvey>

    @Query("SELECT * FROM cached_surveys WHERE surveyor = :owner ORDER BY fetched_at DESC")
    fun observeSurveysForUser(owner: String): Flow<List<CachedSurvey>>

    @Query("DELETE FROM cached_surveys WHERE surveyor = :owner AND survey_type = :type")
    suspend fun deleteSurveyIdentity(owner: String, type: String)

    @Query("DELETE FROM cached_surveys WHERE surveyor = :owner AND survey_type IN (:types)")
    suspend fun deleteSurveyIdentities(owner: String, types: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurveys(surveys: List<CachedSurvey>)

    // CachedParcel queries
    @Query("SELECT * FROM cached_parcels WHERE parcel_code = :parcelCode")
    suspend fun getCachedParcel(parcelCode: String): CachedParcel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcel(parcel: CachedParcel)

    @Query("DELETE FROM cached_parcels WHERE parcel_code = :parcelCode")
    suspend fun deleteParcel(parcelCode: String)

    // CachedSurvey queries
    @Query("SELECT * FROM cached_surveys WHERE parcel_code = :parcelCode AND survey_type = :surveyType")
    suspend fun getCachedSurvey(parcelCode: String, surveyType: String): CachedSurvey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: CachedSurvey)

    @Query("DELETE FROM cached_surveys WHERE parcel_code = :parcelCode")
    suspend fun deleteSurveysForParcel(parcelCode: String)

    // DraftSurvey queries
    @Query("SELECT * FROM draft_surveys WHERE parcel_code = :parcelCode")
    suspend fun getDraft(parcelCode: String): DraftSurvey?

    @Query("SELECT * FROM draft_surveys ORDER BY updated_at DESC")
    fun getAllDrafts(): Flow<List<DraftSurvey>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftSurvey)

    @Query("DELETE FROM draft_surveys WHERE parcel_code = :parcelCode")
    suspend fun deleteDraft(parcelCode: String)

    // PendingSubmission queries
    @Query("SELECT * FROM pending_submissions WHERE status = 'pending' ORDER BY created_at ASC")
    suspend fun getPendingSubmissions(): List<PendingSubmission>

    @Query("SELECT * FROM pending_submissions WHERE parcel_code = :parcelCode AND status = 'pending'")
    suspend fun getPendingForParcel(parcelCode: String): PendingSubmission?

    @Insert
    suspend fun insertPending(submission: PendingSubmission): Long

    @Query("UPDATE pending_submissions SET status = :status, last_error = :error WHERE id = :id")
    suspend fun updatePendingStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE pending_submissions SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt WHERE id = :id")
    suspend fun incrementRetry(id: Long, nextRetryAt: Long)

    @Query("DELETE FROM pending_submissions WHERE status = 'synced'")
    suspend fun deleteSynced()

    // CachedImage queries
    @Query("SELECT * FROM cached_images WHERE parcel_code = :parcelCode AND uploaded = 0")
    suspend fun getUnuploadedImages(parcelCode: String): List<CachedImage>

    @Insert
    suspend fun insertImage(image: CachedImage): Long

    @Query("UPDATE cached_images SET uploaded = 1 WHERE id = :id")
    suspend fun markImageUploaded(id: Long)

    @Query("UPDATE cached_images SET uploaded = 1 WHERE file_path = :path")
    suspend fun markPathUploaded(path: String)

    @Query("DELETE FROM cached_images WHERE parcel_code = :parcelCode AND uploaded = 1")
    suspend fun deleteUploadedImages(parcelCode: String)

    // Cleanup
    @Query("DELETE FROM cached_surveys WHERE fetched_at < :cutoffTime AND source = 'server'")
    suspend fun deleteStaleSurveys(cutoffTime: Long)

    @Query("DELETE FROM cached_parcels WHERE fetched_at < :cutoffTime")
    suspend fun deleteStaleParcels(cutoffTime: Long)
}

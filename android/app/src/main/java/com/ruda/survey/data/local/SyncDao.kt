package com.ruda.survey.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY created_at ASC")
    suspend fun getPendingItems(): List<SyncQueueEntry>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR (status = 'FAILED' AND next_retry_at <= :now) ORDER BY created_at ASC")
    suspend fun getReadyItems(now: Long = System.currentTimeMillis()): List<SyncQueueEntry>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED')")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'CONFLICT'")
    fun getConflictCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getById(id: Long): SyncQueueEntry?

    @Query("SELECT * FROM sync_queue WHERE parcel_code = :parcelCode AND status IN ('PENDING', 'FAILED', 'CONFLICT')")
    suspend fun getPendingForParcel(parcelCode: String): List<SyncQueueEntry>

    @Query("SELECT * FROM sync_queue WHERE client_uuid = :clientUuid")
    suspend fun getByClientUuid(clientUuid: String): SyncQueueEntry?

    @Query("SELECT * FROM sync_queue WHERE client_uuid = :clientUuid AND status != 'SYNCED'")
    suspend fun getActiveByClientUuid(clientUuid: String): SyncQueueEntry?

    @Insert
    suspend fun insert(entry: SyncQueueEntry): Long

    @Update
    suspend fun update(entry: SyncQueueEntry)

    @Query("UPDATE sync_queue SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'IN_PROGRESS', updated_at = :updatedAt WHERE id = :id")
    suspend fun markInProgress(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'SYNCED', updated_at = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'FAILED', retry_count = retry_count + 1, last_error = :error, next_retry_at = :nextRetryAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun markFailed(id: Long, error: String, nextRetryAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'CONFLICT', last_error = :error, error_code = :errorCode, updated_at = :updatedAt WHERE id = :id")
    suspend fun markConflict(id: Long, error: String, errorCode: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'PENDING', retry_count = 0, last_error = NULL, error_code = NULL, next_retry_at = :nextRetryAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun resetForRetry(id: Long, nextRetryAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET data_json = :dataJson, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateData(id: Long, dataJson: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED'")
    suspend fun deleteSynced()

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'IN_PROGRESS'")
    suspend fun getInProgressCount(): Int
}

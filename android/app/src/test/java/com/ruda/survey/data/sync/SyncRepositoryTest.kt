package com.ruda.survey.data.sync

import com.ruda.survey.data.dto.*
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

class SyncRepositoryTest {

    private lateinit var repository: SyncRepository
    private lateinit var api: SurveyApi
    private lateinit var dao: SyncDao
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        api = mock()
        dao = mock()
        tokenManager = mock()
        repository = SyncRepository(api, dao, tokenManager)
    }

    @Test
    fun `enqueue revision creates queue entry`() = runTest {
        whenever(dao.getActiveByClientUuid(any())).thenReturn(null)
        whenever(dao.insert(any())).thenReturn(1L)

        val id = repository.enqueueRevision(
            parcelCode = "RUDA-P14-R00005",
            data = mapOf("owner_name" to "New Owner"),
            changeReason = "Correction",
            clientUuid = "uuid-12345",
            parentRevisionNo = 2
        )

        assertEquals(1L, id)
        verify(dao).insert(any<SyncQueueEntry>())
    }

    @Test
    fun `enqueue revision updates data and resets conflict for existing client uuid`() = runTest {
        val existing = SyncQueueEntry(
            id = 42,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"old":"data"}""",
            status = SyncQueueEntry.STATUS_CONFLICT
        )
        whenever(dao.getActiveByClientUuid("uuid-12345")).thenReturn(existing)

        val id = repository.enqueueRevision(
            parcelCode = "RUDA-P14-R00005",
            data = mapOf("owner_name" to "New Owner"),
            changeReason = null,
            clientUuid = "uuid-12345",
            parentRevisionNo = 2
        )

        assertEquals(42L, id)
        verify(dao).updateData(eq(42), any())
        verify(dao).resetForRetry(42)
        verify(dao, never()).insert(any())
    }

    @Test
    fun `enqueue revision updates data for failed status`() = runTest {
        val existing = SyncQueueEntry(
            id = 42,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"old":"data"}""",
            status = SyncQueueEntry.STATUS_FAILED
        )
        whenever(dao.getActiveByClientUuid("uuid-12345")).thenReturn(existing)

        repository.enqueueRevision(
            parcelCode = "RUDA-P14-R00005",
            data = mapOf("owner_name" to "New Owner"),
            changeReason = null,
            clientUuid = "uuid-12345",
            parentRevisionNo = 2
        )

        verify(dao).updateData(eq(42), any())
        verify(dao).resetForRetry(42)
    }

    @Test
    fun `enqueue image creates queue entry`() = runTest {
        whenever(dao.insert(any())).thenReturn(2L)

        val id = repository.enqueueImage(
            parcelCode = "RUDA-P14-R00005",
            revisionNo = 1,
            imageType = "FRONT",
            filePath = "/path/to/image.jpg"
        )

        assertEquals(2L, id)
        verify(dao).insert(any<SyncQueueEntry>())
    }

    @Test
    fun `process queue success marks items as synced`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn("token")

        val revisionResponse = RevisionCreateResponse(
            replayed = false,
            id = 10,
            revision_no = 3,
            status = "SUBMITTED",
            client_uuid = "uuid-12345",
            accepted_at = "2024-01-01T00:00:00Z",
            diff = mapOf("owner_name" to mapOf("old" to "Old", "new" to "New")),
            full_payload = mapOf("owner_name" to "New")
        )
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any()))
            .thenReturn(Response.success(revisionResponse))

        val result = repository.processQueue()

        assertEquals(1, result.synced)
        assertEquals(0, result.failed)
        assertEquals(0, result.conflicts)
        verify(dao).markInProgress(1)
        verify(dao).markSynced(1)
    }

    @Test
    fun `process queue 409 REVISION_CONFLICT marks as CONFLICT`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn("token")

        val errorBody = """{"error":{"code":"REVISION_CONFLICT","message":"Stale parent"}}"""
            .toResponseBody("application/json".toMediaTypeOrNull())
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any()))
            .thenReturn(Response.error(409, errorBody))

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        assertEquals(1, result.conflicts)
        verify(dao).markConflict(1, "Stale parent", "REVISION_CONFLICT")
    }

    @Test
    fun `process queue 409 IDEMPOTENCY_CONFLICT marks as CONFLICT`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn("token")

        val errorBody = """{"error":{"code":"IDEMPOTENCY_CONFLICT","message":"UUID conflict"}}"""
            .toResponseBody("application/json".toMediaTypeOrNull())
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any()))
            .thenReturn(Response.error(409, errorBody))

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        assertEquals(1, result.conflicts)
        verify(dao).markConflict(1, "UUID conflict", "IDEMPOTENCY_CONFLICT")
    }

    @Test
    fun `process queue 401 auth error marks as FAILED without retry`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn("token")

        val errorBody = """{"error":{"code":"AUTHENTICATION_REQUIRED","message":"Auth required"}}"""
            .toResponseBody("application/json".toMediaTypeOrNull())
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any()))
            .thenReturn(Response.error(401, errorBody))

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(1, result.failed)
        assertEquals(0, result.conflicts)
        verify(dao).updateStatus(1, SyncQueueEntry.STATUS_FAILED)
    }

    @Test
    fun `process queue 400 validation error marks as FAILED without retry`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn("token")

        val errorBody = """{"error":{"code":"VALIDATION_ERROR","message":"Invalid data"}}"""
            .toResponseBody("application/json".toMediaTypeOrNull())
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any()))
            .thenReturn(Response.error(400, errorBody))

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(1, result.failed)
        assertEquals(0, result.conflicts)
        verify(dao).updateStatus(1, SyncQueueEntry.STATUS_FAILED)
    }

    @Test
    fun `process queue 500 server error marks as FAILED with retry`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn("token")

        val errorBody = """{"error":{"code":"SERVER_ERROR","message":"Internal error"}}"""
            .toResponseBody("application/json".toMediaTypeOrNull())
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any()))
            .thenReturn(Response.error(500, errorBody))

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(1, result.failed)
        assertEquals(0, result.conflicts)
        verify(dao).markFailed(eq(1), any(), any())
    }

    @Test
    fun `process queue skips items when no auth token`() = runTest {
        val item = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{"owner_name":"New"}}"""
        )
        whenever(dao.getReadyItems()).thenReturn(listOf(item))
        whenever(tokenManager.getAccessToken()).thenReturn(null)

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        assertEquals(0, result.conflicts)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `process queue handles empty queue`() = runTest {
        whenever(dao.getReadyItems()).thenReturn(emptyList())

        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        assertEquals(0, result.conflicts)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `resolve conflict updates data and resets for retry`() = runTest {
        val item = SyncQueueEntry(
            id = 42,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"old":"data"}""",
            status = SyncQueueEntry.STATUS_CONFLICT
        )
        whenever(dao.getById(42)).thenReturn(item)

        repository.resolveConflict(42, mapOf("owner_name" to "New"), "Updated")

        verify(dao).updateData(eq(42), any())
        verify(dao).resetForRetry(42)
    }

    @Test
    fun `resolve conflict does nothing for non-conflict item`() = runTest {
        val item = SyncQueueEntry(
            id = 42,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"old":"data"}""",
            status = SyncQueueEntry.STATUS_SYNCED
        )
        whenever(dao.getById(42)).thenReturn(item)

        repository.resolveConflict(42, mapOf("owner_name" to "New"), "Updated")

        verify(dao, never()).updateData(any(), any())
        verify(dao, never()).resetForRetry(any())
    }

    @Test
    fun `has pending items returns true when items exist`() = runTest {
        whenever(dao.getPendingItems()).thenReturn(listOf(mock()))

        assertTrue(repository.hasPendingItems())
    }

    @Test
    fun `has pending items returns false when queue is empty`() = runTest {
        whenever(dao.getPendingItems()).thenReturn(emptyList())

        assertFalse(repository.hasPendingItems())
    }
}

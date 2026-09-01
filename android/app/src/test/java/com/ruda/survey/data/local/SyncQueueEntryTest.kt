package com.ruda.survey.data.local

import org.junit.Assert.*
import org.junit.Test

class SyncQueueEntryTest {

    @Test
    fun `default status is PENDING`() {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}"
        )

        assertEquals(SyncQueueEntry.STATUS_PENDING, entry.status)
    }

    @Test
    fun `default retry count is zero`() {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}"
        )

        assertEquals(0, entry.retryCount)
    }

    @Test
    fun `default last error is null`() {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}"
        )

        assertNull(entry.lastError)
    }

    @Test
    fun `default error code is null`() {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}"
        )

        assertNull(entry.errorCode)
    }

    @Test
    fun `revision create entry stores all fields`() {
        val entry = SyncQueueEntry(
            id = 1,
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"client_uuid":"uuid-12345","data":{}}""",
            status = SyncQueueEntry.STATUS_PENDING,
            retryCount = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        assertEquals(1, entry.id)
        assertEquals(SyncQueueEntry.OP_REVISION_CREATE, entry.operationType)
        assertEquals("RUDA-P14-R00005", entry.parcelCode)
        assertEquals("uuid-12345", entry.clientUuid)
        assertNull(entry.revisionNo)
        assertNull(entry.filePath)
        assertNull(entry.imageType)
    }

    @Test
    fun `image upload entry stores all fields`() {
        val entry = SyncQueueEntry(
            id = 2,
            operationType = SyncQueueEntry.OP_IMAGE_UPLOAD,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            revisionNo = 1,
            filePath = "/path/to/image.jpg",
            imageType = "FRONT",
            dataJson = """{"image_type":"FRONT"}""",
            status = SyncQueueEntry.STATUS_IN_PROGRESS,
            retryCount = 1,
            lastError = "Network error",
            errorCode = "NETWORK_ERROR",
            nextRetryAt = 2000L
        )

        assertEquals(2, entry.id)
        assertEquals(SyncQueueEntry.OP_IMAGE_UPLOAD, entry.operationType)
        assertEquals(1, entry.revisionNo)
        assertEquals("/path/to/image.jpg", entry.filePath)
        assertEquals("FRONT", entry.imageType)
        assertEquals(SyncQueueEntry.STATUS_IN_PROGRESS, entry.status)
        assertEquals(1, entry.retryCount)
        assertEquals("Network error", entry.lastError)
        assertEquals("NETWORK_ERROR", entry.errorCode)
        assertEquals(2000L, entry.nextRetryAt)
    }

    @Test
    fun `status constants are correct`() {
        assertEquals("PENDING", SyncQueueEntry.STATUS_PENDING)
        assertEquals("IN_PROGRESS", SyncQueueEntry.STATUS_IN_PROGRESS)
        assertEquals("SYNCED", SyncQueueEntry.STATUS_SYNCED)
        assertEquals("FAILED", SyncQueueEntry.STATUS_FAILED)
        assertEquals("CONFLICT", SyncQueueEntry.STATUS_CONFLICT)
    }

    @Test
    fun `operation type constants are correct`() {
        assertEquals("REVISION_CREATE", SyncQueueEntry.OP_REVISION_CREATE)
        assertEquals("IMAGE_UPLOAD", SyncQueueEntry.OP_IMAGE_UPLOAD)
    }

    @Test
    fun `conflict entry stores error code`() {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}",
            status = SyncQueueEntry.STATUS_CONFLICT,
            lastError = "Stale parent revision",
            errorCode = "REVISION_CONFLICT"
        )

        assertEquals(SyncQueueEntry.STATUS_CONFLICT, entry.status)
        assertEquals("Stale parent revision", entry.lastError)
        assertEquals("REVISION_CONFLICT", entry.errorCode)
    }

    @Test
    fun `with retry info stores correct values`() {
        val entry = SyncQueueEntry(
            operationType = SyncQueueEntry.OP_REVISION_CREATE,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}",
            status = SyncQueueEntry.STATUS_FAILED,
            retryCount = 3,
            lastError = "Server error",
            errorCode = "SERVER_ERROR",
            nextRetryAt = 5000L
        )

        assertEquals(3, entry.retryCount)
        assertEquals("Server error", entry.lastError)
        assertEquals("SERVER_ERROR", entry.errorCode)
        assertEquals(5000L, entry.nextRetryAt)
    }
}

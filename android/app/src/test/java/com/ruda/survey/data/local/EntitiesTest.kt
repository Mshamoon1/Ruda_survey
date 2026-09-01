package com.ruda.survey.data.local

import org.junit.Assert.*
import org.junit.Test

class EntitiesTest {

    @Test
    fun `CachedParcel stores all fields`() {
        val parcel = CachedParcel(
            parcelCode = "RUDA-P14-R00005",
            sourceNid = 5,
            village = "Test Village",
            tehsil = "Test Tehsil",
            district = "Test District",
            ownerNameCurrent = "Test Owner",
            masterLineCount = 10,
            revisionNo = 2,
            source = "revision",
            fetchedAt = 1000L
        )

        assertEquals("RUDA-P14-R00005", parcel.parcelCode)
        assertEquals(5, parcel.sourceNid)
        assertEquals("Test Village", parcel.village)
        assertEquals(10, parcel.masterLineCount)
        assertEquals(2, parcel.revisionNo)
    }

    @Test
    fun `CachedParcel default values`() {
        val parcel = CachedParcel(
            parcelCode = "RUDA-P14-R00005",
            sourceNid = null,
            village = null,
            tehsil = null,
            district = null,
            ownerNameCurrent = null
        )

        assertEquals(0, parcel.masterLineCount)
        assertEquals(0, parcel.revisionNo)
        assertEquals("master", parcel.source)
        assertTrue(parcel.fetchedAt > 0)
    }

    @Test
    fun `DraftSurvey stores all fields`() {
        val draft = DraftSurvey(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fieldsJson = """{"owner_name":"Ali"}""",
            changeReason = "Correction",
            clientUuid = "uuid-12345",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("RUDA-P14-R00005", draft.parcelCode)
        assertEquals(1, draft.baseRevisionNo)
        assertEquals("Correction", draft.changeReason)
        assertEquals("uuid-12345", draft.clientUuid)
    }

    @Test
    fun `DraftSurvey default values`() {
        val draft = DraftSurvey(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fieldsJson = "{}"
        )

        assertEquals("", draft.changeReason)
        assertTrue(draft.clientUuid.isNotEmpty())
        assertTrue(draft.createdAt > 0)
        assertTrue(draft.updatedAt > 0)
    }

    @Test
    fun `PendingSubmission stores all fields`() {
        val pending = PendingSubmission(
            id = 1,
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = """{"owner_name":"Ali"}""",
            changeReason = "Correction",
            parentRevisionNo = 2,
            status = "pending",
            retryCount = 0,
            lastError = null,
            createdAt = 1000L,
            nextRetryAt = 1000L
        )

        assertEquals(1, pending.id)
        assertEquals("RUDA-P14-R00005", pending.parcelCode)
        assertEquals("pending", pending.status)
        assertEquals(0, pending.retryCount)
    }

    @Test
    fun `PendingSubmission default values`() {
        val pending = PendingSubmission(
            parcelCode = "RUDA-P14-R00005",
            clientUuid = "uuid-12345",
            dataJson = "{}"
        )

        assertEquals(0, pending.id)
        assertEquals("pending", pending.status)
        assertEquals(0, pending.retryCount)
        assertNull(pending.lastError)
    }

    @Test
    fun `CachedImage stores all fields`() {
        val image = CachedImage(
            id = 1,
            parcelCode = "RUDA-P14-R00005",
            revisionNo = 1,
            imageType = "FRONT",
            filePath = "/path/to/image.jpg",
            checksumSha256 = "abc123",
            contentType = "image/jpeg",
            fileSize = 1024L,
            uploaded = false,
            createdAt = 1000L
        )

        assertEquals(1, image.id)
        assertEquals("FRONT", image.imageType)
        assertEquals("/path/to/image.jpg", image.filePath)
        assertFalse(image.uploaded)
    }

    @Test
    fun `CachedImage default values`() {
        val image = CachedImage(
            parcelCode = "RUDA-P14-R00005",
            revisionNo = 1,
            imageType = "SECOND",
            filePath = "/path/to/image.jpg"
        )

        assertEquals(0, image.id)
        assertNull(image.checksumSha256)
        assertNull(image.contentType)
        assertNull(image.fileSize)
        assertFalse(image.uploaded)
    }
}

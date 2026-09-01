package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test

class UiStateTest {

    @Test
    fun `Loading is a UiState`() {
        val state: UiState<String> = UiState.Loading
        assertTrue(state is UiState.Loading)
    }

    @Test
    fun `Success contains data`() {
        val state: UiState<String> = UiState.Success("hello")
        assertTrue(state is UiState.Success)
        assertEquals("hello", (state as UiState.Success).data)
    }

    @Test
    fun `Error contains code and message`() {
        val state: UiState<String> = UiState.Error("ERR_001", "Something went wrong")
        assertTrue(state is UiState.Error)
        val error = state as UiState.Error
        assertEquals("ERR_001", error.code)
        assertEquals("Something went wrong", error.message)
    }

    @Test
    fun `Empty is a UiState`() {
        val state: UiState<String> = UiState.Empty
        assertTrue(state is UiState.Empty)
    }

    @Test
    fun `AuthState defaults`() {
        val auth = AuthState()
        assertFalse(auth.isLoggedIn)
        assertNull(auth.username)
        assertNull(auth.role)
    }

    @Test
    fun `AuthState with values`() {
        val auth = AuthState(isLoggedIn = true, username = "admin", role = "admin")
        assertTrue(auth.isLoggedIn)
        assertEquals("admin", auth.username)
        assertEquals("admin", auth.role)
    }

    @Test
    fun `ParcelInfo stores all fields`() {
        val info = ParcelInfo(
            parcelCode = "RUDA-P14-R00005",
            sourceNid = 5,
            village = "Test Village",
            tehsil = "Test Tehsil",
            district = "Test District",
            ownerNameCurrent = "Test Owner",
            masterLineCount = 10,
            revisionNo = 2,
            source = "revision",
            currentRevision = CurrentRevisionInfo(
                revisionNo = 2,
                fullPayload = mapOf("owner_name" to "Test Owner"),
                changedFields = listOf("owner_name"),
                status = "SUBMITTED"
            )
        )

        assertEquals("RUDA-P14-R00005", info.parcelCode)
        assertEquals(5, info.sourceNid)
        assertEquals("Test Village", info.village)
        assertEquals("revision", info.source)
        assertNotNull(info.currentRevision)
        assertEquals(2, info.currentRevision!!.revisionNo)
    }

    @Test
    fun `ImageInfo stores all fields`() {
        val img = ImageInfo(
            imageType = "FRONT",
            checksumSha256 = "abc123",
            storageKey = "parcels/RUDA-P14-R00005/rev1/FRONT.jpg",
            contentType = "image/jpeg",
            fileSize = 1024L,
            uploadedAt = "2024-01-01T00:00:00Z"
        )

        assertEquals("FRONT", img.imageType)
        assertEquals("abc123", img.checksumSha256)
        assertEquals(1024L, img.fileSize)
    }

    @Test
    fun `RevisionResult stores all fields`() {
        val result = RevisionResult(
            replayed = false,
            revisionNo = 3,
            status = "SUBMITTED",
            diff = mapOf("owner_name" to mapOf("old" to "Ali", "new" to "Ahmed")),
            fullPayload = mapOf("owner_name" to "Ahmed")
        )

        assertFalse(result.replayed)
        assertEquals(3, result.revisionNo)
        assertEquals("SUBMITTED", result.status)
        assertEquals("Ali", result.diff["owner_name"]?.get("old"))
        assertEquals("Ahmed", result.diff["owner_name"]?.get("new"))
    }
}

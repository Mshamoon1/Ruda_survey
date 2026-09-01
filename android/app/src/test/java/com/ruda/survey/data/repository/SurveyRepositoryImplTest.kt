package com.ruda.survey.data.repository

import com.ruda.survey.data.dto.*
import com.ruda.survey.data.local.SurveyDao
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager
import com.ruda.survey.domain.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

class SurveyRepositoryImplTest {

    private lateinit var repository: SurveyRepositoryImpl
    private lateinit var api: SurveyApi
    private lateinit var dao: SurveyDao
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        api = mock()
        dao = mock()
        tokenManager = mock()
        repository = SurveyRepositoryImpl(api, dao, tokenManager)
    }

    @Test
    fun `login success returns AuthState`() = runTest {
        val loginResponse = LoginResponse(
            access = "access-token",
            refresh = "refresh-token",
            user = UserDto(1, "admin", "Admin", "User", "admin")
        )
        whenever(api.login(any())).thenReturn(Response.success(loginResponse))

        val result = repository.login("admin", "password")

        assertTrue(result.isSuccess)
        val auth = result.getOrNull()!!
        assertTrue(auth.isLoggedIn)
        assertEquals("admin", auth.username)
        assertEquals("admin", auth.role)
        verify(tokenManager).saveTokens("access-token", "refresh-token")
    }

    @Test
    fun `login failure returns error`() = runTest {
        val errorBody = """{"error":{"code":"AUTH_FAILED","message":"Invalid credentials"}}"""
            .toResponseBody("application/json".toMediaTypeOrNull())
        whenever(api.login(any())).thenReturn(Response.error(401, errorBody))

        val result = repository.login("admin", "wrong")

        assertTrue(result.isFailure)
        verify(tokenManager, never()).saveTokens(any(), any())
    }

    @Test
    fun `login network exception returns failure`() = runTest {
        whenever(api.login(any())).thenThrow(RuntimeException("Network error"))

        val result = repository.login("admin", "password")

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `logout clears tokens`() = runTest {
        whenever(tokenManager.getRefreshToken()).thenReturn("refresh-token")
        whenever(api.logout(any())).thenReturn(Response.success(Unit))

        val result = repository.logout()

        assertTrue(result.isSuccess)
        verify(tokenManager).clearTokens()
    }

    @Test
    fun `getParcelInfo success returns ParcelInfo`() = runTest {
        val response = ParcelLookupResponse(
            parcel = ParcelHeaderDto("RUDA-P14-R00005", 5, "Village", "Tehsil", "District", "Owner"),
            original = mapOf("owner_name" to "Original Owner"),
            current_revision = RevisionSummaryDto(2, mapOf("owner_name" to "New Owner"), listOf("owner_name"), "SUBMITTED"),
            revision_no = 2,
            source = "revision"
        )
        whenever(api.parcelLookup("RUDA-P14-R00005")).thenReturn(Response.success(response))

        val result = repository.getParcelInfo("RUDA-P14-R00005")

        assertTrue(result.isSuccess)
        val info = result.getOrNull()!!
        assertEquals("RUDA-P14-R00005", info.parcelCode)
        assertEquals(5, info.sourceNid)
        assertEquals("Village", info.village)
        assertEquals("revision", info.source)
        assertNotNull(info.currentRevision)
        assertEquals(2, info.currentRevision!!.revisionNo)
    }

    @Test
    fun `getCurrentSurvey success returns SurveyData`() = runTest {
        val response = CurrentDataResponse(
            parcel = mapOf("parcel_code" to "RUDA-P14-R00005"),
            source = "revision",
            revision_no = 3,
            data = mapOf("owner_name" to "Current Owner")
        )
        whenever(api.currentData("RUDA-P14-R00005")).thenReturn(Response.success(response))

        val result = repository.getCurrentSurvey("RUDA-P14-R00005")

        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        assertEquals("RUDA-P14-R00005", data.parcelCode)
        assertEquals("revision", data.source)
        assertEquals(3, data.revisionNo)
        assertEquals("Current Owner", data.fields["owner_name"])
    }

    @Test
    fun `createRevision success returns RevisionResult`() = runTest {
        val response = RevisionCreateResponse(
            replayed = false,
            id = 10,
            revision_no = 4,
            status = "SUBMITTED",
            client_uuid = "uuid-12345",
            accepted_at = "2024-01-01T00:00:00Z",
            diff = mapOf("owner_name" to mapOf("old" to "Old", "new" to "New")),
            full_payload = mapOf("owner_name" to "New")
        )
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any())).thenReturn(Response.success(response))

        val result = repository.createRevision(
            parcelCode = "RUDA-P14-R00005",
            data = mapOf("owner_name" to "New"),
            changeReason = "Correction",
            clientUuid = "uuid-12345",
            parentRevisionNo = 3
        )

        assertTrue(result.isSuccess)
        val rev = result.getOrNull()!!
        assertFalse(rev.replayed)
        assertEquals(4, rev.revisionNo)
        assertEquals("SUBMITTED", rev.status)
        assertEquals("Old", rev.diff["owner_name"]?.get("old"))
        assertEquals("New", rev.diff["owner_name"]?.get("new"))
    }

    @Test
    fun `createRevision idempotent replay detected`() = runTest {
        val response = RevisionCreateResponse(
            replayed = true,
            id = 10,
            revision_no = 4,
            status = "SUBMITTED",
            client_uuid = "uuid-12345",
            accepted_at = "2024-01-01T00:00:00Z",
            diff = emptyMap(),
            full_payload = mapOf("owner_name" to "New")
        )
        whenever(api.createRevision(eq("RUDA-P14-R00005"), any())).thenReturn(Response.success(response))

        val result = repository.createRevision(
            parcelCode = "RUDA-P14-R00005",
            data = mapOf("owner_name" to "New"),
            changeReason = null,
            clientUuid = "uuid-12345",
            parentRevisionNo = 3
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.replayed)
    }

    @Test
    fun `uploadImage success returns ImageInfo`() = runTest {
        val response = ImageUploadResponse(
            id = 1,
            image_type = "FRONT",
            checksum_sha256 = "abc123",
            storage_key = "parcels/RUDA-P14-R00005/rev1/FRONT.jpg",
            content_type = "image/jpeg",
            file_size = 1024,
            width_px = 1920,
            height_px = 1080
        )
        whenever(api.uploadImage(eq("RUDA-P14-R00005"), eq(1), any(), any()))
            .thenReturn(Response.success(response))

        val result = repository.uploadImage(
            parcelCode = "RUDA-P14-R00005",
            revisionNo = 1,
            imageType = "FRONT",
            imageBytes = ByteArray(100),
            fileName = "photo.jpg"
        )

        assertTrue(result.isSuccess)
        val img = result.getOrNull()!!
        assertEquals("FRONT", img.imageType)
        assertEquals("abc123", img.checksumSha256)
        assertEquals(1024L, img.fileSize)
    }

    @Test
    fun `getSurveySheet success returns SurveyData`() = runTest {
        val response = SheetResponse(
            parcel = ParcelHeaderDto("RUDA-P14-R00005", 5, "Village", "Tehsil", "District", "Owner"),
            source = "revision",
            current_revision_no = 3,
            surveyor = "admin",
            changed_at = "2024-01-01T00:00:00Z",
            accepted_at = "2024-01-01T00:00:00Z",
            images = listOf(ImageDto("FRONT", "abc", null, "image/jpeg", 1024, null)),
            fields = mapOf("owner_name" to "Current Owner"),
            original = mapOf("owner_name" to "Original Owner")
        )
        whenever(api.surveySheet("RUDA-P14-R00005")).thenReturn(Response.success(response))

        val result = repository.getSurveySheet("RUDA-P14-R00005")

        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        assertEquals("RUDA-P14-R00005", data.parcelCode)
        assertEquals("revision", data.source)
        assertEquals(3, data.revisionNo)
        assertEquals("admin", data.surveyor)
        assertEquals(1, data.images.size)
        assertEquals("FRONT", data.images[0].imageType)
    }

    @Test
    fun `getAuthToken delegates to tokenManager`() {
        whenever(tokenManager.getAccessToken()).thenReturn("test-token")
        assertEquals("test-token", repository.getAuthToken())
    }

    @Test
    fun `isLoggedIn delegates to tokenManager`() {
        whenever(tokenManager.hasTokens()).thenReturn(true)
        assertTrue(repository.isLoggedIn())
    }
}

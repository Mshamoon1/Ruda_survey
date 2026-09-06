package com.ruda.survey.data.repository

import com.ruda.survey.data.dto.*
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
    private lateinit var tokenManager: TokenManager

    private fun testSurveyDto(id: String = "abc123", srNo: Int = 5) = SurveyItemDto(
        id = id,
        sr_no = srNo,
        parcel_id = "RUDA-P14-R00005",
        rd = "5",
        pkg = "1",
        village = "Test Village",
        status = "active",
        structuralName = "House",
        nature_of_construction = "Permanent",
        imgOne = "img1.jpg",
        imgTwo = "img2.jpg",
        coordinates = CoordinatesDto(lat = 31.5, lng = 74.3),
        identification = IdentificationDto(
            owner_name = "Ali",
            f_name = "Ahmed",
            cnic = "12345-1234567-1",
            khasra_no = "K1",
            phone = "0300-1234567",
            land_owner_doc = "registry",
            electricity_connection_name = "Yes",
            land_area = "500"
        ),
        covered_area = CoveredAreaDto(length = "10", width = "8", area = "80")
    )

    @Before
    fun setup() {
        api = mock()
        tokenManager = mock()
        repository = SurveyRepositoryImpl(api, tokenManager)
    }

    @Test
    fun `getAllSurveys success returns list`() = runTest {
        val response = SurveyListResponse(
            success = true,
            message = "ok",
            data = listOf(testSurveyDto(), testSurveyDto("def456", 6))
        )
        whenever(api.getAllSurveys()).thenReturn(Response.success(response))

        val result = repository.getAllSurveys()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()!!.size)
        assertEquals("RUDA-P14-R00005", result.getOrNull()!![0].parcelId)
    }

    @Test
    fun `getAllSurveys failure returns error`() = runTest {
        whenever(api.getAllSurveys()).thenReturn(
            Response.error(500, "Server Error".toResponseBody("text/plain".toMediaTypeOrNull()))
        )

        val result = repository.getAllSurveys()
        assertTrue(result.isFailure)
    }

    @Test
    fun `getSurveyBySrNo success returns survey`() = runTest {
        val dataMap = mapOf(
            "_id" to "abc123",
            "sr_no" to 5,
            "parcel_id" to "RUDA-P14-R00005",
            "village" to "Test Village",
            "coordinates" to mapOf("lat" to 31.5, "lng" to 74.3),
            "identification" to mapOf("owner_name" to "Ali", "f_name" to "Ahmed")
        )
        val wrapper = SurveyDataWrapper(success = true, message = "ok", data = dataMap)
        whenever(api.getSurveyBySrNo(5)).thenReturn(Response.success(wrapper))

        val result = repository.getSurveyBySrNo(5)

        assertTrue(result.isSuccess)
        val item = result.getOrNull()!!
        assertEquals(5, item.srNo)
        assertEquals("Ali", item.ownerName)
        assertEquals(31.5, item.lat, 0.001)
    }

    @Test
    fun `getSurveyBySrNo not found returns failure`() = runTest {
        whenever(api.getSurveyBySrNo(999)).thenReturn(
            Response.error(404, "Not found".toResponseBody("text/plain".toMediaTypeOrNull()))
        )

        val result = repository.getSurveyBySrNo(999)
        assertTrue(result.isFailure)
    }

    @Test
    fun `getSurveyById success returns survey`() = runTest {
        val dataMap = mapOf(
            "_id" to "id123",
            "sr_no" to 5,
            "parcel_id" to "RUDA-P14-R00005"
        )
        val wrapper = SurveyDataWrapper(success = true, message = "ok", data = dataMap)
        whenever(api.getSurveyById("id123")).thenReturn(Response.success(wrapper))

        val result = repository.getSurveyById("id123")

        assertTrue(result.isSuccess)
        assertEquals("id123", result.getOrNull()!!.id)
    }

    @Test
    fun `deleteSurvey success`() = runTest {
        val wrapper = SurveyDataWrapper(success = true, message = "deleted", data = null)
        whenever(api.deleteSurvey("id123")).thenReturn(Response.success(wrapper))

        val result = repository.deleteSurvey("id123")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `deleteSurvey failure`() = runTest {
        whenever(api.deleteSurvey("id123")).thenReturn(
            Response.error(500, "Error".toResponseBody("text/plain".toMediaTypeOrNull()))
        )

        val result = repository.deleteSurvey("id123")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getAuthToken returns token from TokenManager`() {
        whenever(tokenManager.getAccessToken()).thenReturn("test-token")
        assertEquals("test-token", repository.getAuthToken())
    }

    @Test
    fun `getAuthToken returns null when no token`() {
        whenever(tokenManager.getAccessToken()).thenReturn(null)
        assertNull(repository.getAuthToken())
    }

    @Test
    fun `isLoggedIn delegates to TokenManager`() {
        whenever(tokenManager.hasTokens()).thenReturn(true)
        assertTrue(repository.isLoggedIn())
        whenever(tokenManager.hasTokens()).thenReturn(false)
        assertFalse(repository.isLoggedIn())
    }
}

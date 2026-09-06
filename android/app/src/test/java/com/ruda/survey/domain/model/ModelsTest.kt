package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {

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
    fun `SurveyItem defaults`() {
        val item = SurveyItem()
        assertEquals("", item.id)
        assertEquals(0, item.srNo)
        assertEquals("", item.parcelId)
        assertEquals(0.0, item.lat, 0.001)
    }

    @Test
    fun `SurveyItem stores all fields`() {
        val item = SurveyItem(
            id = "abc123",
            srNo = 5,
            parcelId = "P1",
            rd = "5",
            pkg = "1",
            village = "Test",
            status = "active",
            structuralName = "House",
            natureOfConstruction = "Pucca",
            lat = 31.5,
            lng = 74.3,
            ownerName = "Ali",
            fName = "Ahmed",
            cnic = "12345-1234567-1",
            khasraNo = "K1",
            phone = "0300-1234567",
            landOwnerDoc = "registry",
            electricityConnectionName = "Yes",
            landArea = "500",
            length = "10",
            width = "8",
            area = "80"
        )

        assertEquals("abc123", item.id)
        assertEquals(5, item.srNo)
        assertEquals("Ali", item.ownerName)
        assertEquals("Ahmed", item.fName)
        assertEquals(31.5, item.lat, 0.001)
    }

    @Test
    fun `SearchParcel stores fields`() {
        val parcel = SearchParcel(
            id = "abc",
            srNo = 5,
            village = "Test",
            ownerName = "Ali",
            structuralName = "House",
            natureOfConstruction = "Pucca"
        )
        assertEquals("abc", parcel.id)
        assertEquals(5, parcel.srNo)
        assertEquals("Ali", parcel.ownerName)
    }

    @Test
    fun `EditableDraft stores surveyItem and uuid`() {
        val item = SurveyItem(srNo = 5)
        val draft = EditableDraft(surveyItem = item, clientUuid = "uuid-123")
        assertEquals(item, draft.surveyItem)
        assertEquals("uuid-123", draft.clientUuid)
    }
}

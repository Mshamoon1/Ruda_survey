package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test

class EditableDraftTest {

    @Test
    fun `draft creates with generated clientUuid`() {
        val item = SurveyItem(srNo = 5, parcelId = "P1", village = "Test")
        val draft = EditableDraft(surveyItem = item)

        assertTrue(draft.clientUuid.isNotEmpty())
        assertEquals(item, draft.surveyItem)
    }

    @Test
    fun `draft preserves provided clientUuid`() {
        val item = SurveyItem(srNo = 5)
        val draft = EditableDraft(surveyItem = item, clientUuid = "my-uuid")

        assertEquals("my-uuid", draft.clientUuid)
    }

    @Test
    fun `different drafts have different UUIDs`() {
        val item = SurveyItem(srNo = 5)
        val draft1 = EditableDraft(surveyItem = item)
        val draft2 = EditableDraft(surveyItem = item)

        assertNotEquals(draft1.clientUuid, draft2.clientUuid)
    }
}

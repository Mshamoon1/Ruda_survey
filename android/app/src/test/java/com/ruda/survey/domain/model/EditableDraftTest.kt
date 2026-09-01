package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test

class EditableDraftTest {

    @Test
    fun `computeChanges detects changed fields`() {
        val original = mapOf(
            "owner_name" to "Ali Khan",
            "area_sqft" to 1200.0,
            "cnic_no" to "12345-6789012-3"
        )
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf(
                "owner_name" to "Ahmed Khan",
                "area_sqft" to 1200.0,
                "cnic_no" to "12345-6789012-3"
            )
        )

        val diff = draft.computeChanges(original)

        assertEquals(1, diff.size)
        assertEquals("Ali Khan", diff["owner_name"]?.get("old"))
        assertEquals("Ahmed Khan", diff["owner_name"]?.get("new"))
    }

    @Test
    fun `computeChanges returns empty when no changes`() {
        val original = mapOf("owner_name" to "Ali Khan", "area_sqft" to 1200.0)
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf("owner_name" to "Ali Khan", "area_sqft" to 1200.0)
        )

        val diff = draft.computeChanges(original)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `computeChanges handles null old value`() {
        val original = mapOf<String, Any?>("owner_name" to null)
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf("owner_name" to "New Name")
        )

        val diff = draft.computeChanges(original)
        assertEquals(1, diff.size)
        assertNull(diff["owner_name"]?.get("old"))
        assertEquals("New Name", diff["owner_name"]?.get("new"))
    }

    @Test
    fun `computeChanges handles null new value`() {
        val original = mapOf<String, Any?>("owner_name" to "Ali Khan")
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf("owner_name" to null)
        )

        val diff = draft.computeChanges(original)
        assertEquals(1, diff.size)
        assertEquals("Ali Khan", diff["owner_name"]?.get("old"))
        assertNull(diff["owner_name"]?.get("new"))
    }

    @Test
    fun `clientUuid is generated if not provided`() {
        val draft1 = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf()
        )
        val draft2 = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf()
        )

        assertNotEquals(draft1.clientUuid, draft2.clientUuid)
        assertTrue(draft1.clientUuid.isNotEmpty())
    }

    @Test
    fun `clientUuid is preserved when provided`() {
        val uuid = "test-uuid-12345"
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf(),
            clientUuid = uuid
        )

        assertEquals(uuid, draft.clientUuid)
    }

    @Test
    fun `default changeReason is empty`() {
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf()
        )

        assertEquals("", draft.changeReason)
    }

    @Test
    fun `computeChanges with multiple field types`() {
        val original = mapOf<String, Any?>(
            "owner_name" to "Ali",
            "area_sqft" to 1000.0,
            "cnic_no" to "11111-1111111-1",
            "contact_number" to null
        )
        val draft = EditableDraft(
            parcelCode = "RUDA-P14-R00005",
            baseRevisionNo = 1,
            fields = mutableMapOf(
                "owner_name" to "Ali",
                "area_sqft" to 1500.0,
                "cnic_no" to null,
                "contact_number" to "0300-1234567"
            )
        )

        val diff = draft.computeChanges(original)
        assertEquals(3, diff.size)
        assertEquals(1500.0, diff["area_sqft"]?.get("new"))
        assertNull(diff["cnic_no"]?.get("new"))
        assertEquals("0300-1234567", diff["contact_number"]?.get("new"))
    }
}

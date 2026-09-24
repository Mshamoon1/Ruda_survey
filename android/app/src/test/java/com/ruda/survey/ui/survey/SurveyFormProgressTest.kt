package com.ruda.survey.ui.survey

import org.junit.Assert.*
import org.junit.Test

class SurveyFormProgressTest {
    private val complete = SurveyFormProgress(
        parcelId = "parcel-1", packageNo = "1", hasDoorPhoto = true,
        ownerName = "Owner", cnic = "35202-1234567-1", contact = "0300-123-456",
        village = "Village", khasraNo = "124/3", status = "residential", construction = "pacca"
    )

    @Test fun emptyFormHasNoCompletedSections() {
        val progress = SurveyFormProgress()
        assertEquals(0, progress.percentage)
        assertEquals(0, progress.completedCount)
        assertTrue(progress.completedSections.none { it })
    }

    @Test fun validRequiredInputsCompleteAllSectionsWithoutOptionalFields() {
        assertEquals(100, complete.percentage)
        assertEquals(5, complete.completedCount)
        assertTrue(complete.completedSections.all { it })
    }

    @Test fun missingDoorPhotoPreventsReadyStateEvenWhenAllTextIsValid() {
        val progress = complete.copy(hasDoorPhoto = false)
        assertEquals(90, progress.percentage)
        assertEquals(4, progress.completedCount)
        assertFalse(progress.completedSections[1])
    }

    @Test fun malformedIdentityFieldsDoNotCountAsCompleted() {
        val progress = complete.copy(cnic = "35202-123", contact = "0300-123")
        assertEquals(80, progress.percentage)
        assertFalse(progress.completedSections[2])
        assertFalse(complete.copy(cnic = "35202123456712").completedSections[2])
    }

    @Test fun partialAndOutOfOrderSectionsDoNotMarkEarlierSectionsCompleted() {
        val progress = SurveyFormProgress(parcelId = "parcel", status = "residential", construction = "pacca")
        assertEquals(30, progress.percentage)
        assertEquals(listOf(false, false, false, false, true), progress.completedSections)
    }

    @Test fun clearingARequiredValueReversesCompletion() {
        val progress = complete.copy(packageNo = "  ")
        assertEquals(90, progress.percentage)
        assertEquals(4, progress.completedCount)
        assertFalse(progress.completedSections[0])
    }
}

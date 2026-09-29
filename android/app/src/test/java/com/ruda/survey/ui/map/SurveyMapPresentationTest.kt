package com.ruda.survey.ui.map

import com.ruda.survey.domain.model.SurveyItem
import org.junit.Assert.*
import org.junit.Test

class SurveyMapPresentationTest {
    private val completed = SurveyItem(parcelId = "P1", pkg = "1", imgOne = "door.jpg",
        ownerName = "Owner", cnic = "12345-1234567-1", phone = "03001234567",
        village = "Village", khasraNo = "K1", status = "residential", natureOfConstruction = "pacca")

    @Test fun importedPointAndStructuralStatusAreNotProofOfSurveyCompletion() {
        assertFalse(SurveyItem(lat = 31.6, lng = 74.3, status = "residential").isSurveyed)
        assertEquals("#E45161", SurveyItem().mapPointColor)
    }

    @Test fun completeSurveyIsGreenEvenBeforeItsOfflineUploadFinishes() {
        assertTrue(completed.isSurveyed)
        assertTrue(completed.copy(imgOne = "", image1LocalPath = "door.jpg", syncStatus = "PENDING_CREATE").isSurveyed)
        assertEquals("#00A878", completed.mapPointColor)
    }

    @Test fun missingEvidenceOrRequiredDetailsStaysPending() {
        assertFalse(completed.copy(imgOne = "").isSurveyed)
        assertFalse(completed.copy(cnic = "123").isSurveyed)
        assertFalse(completed.copy(khasraNo = "").isSurveyed)
    }
}

package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test

class SurveyMapLocationsTest {
    @Test fun invalidCoordinatesCannotDistortMapBounds() {
        assertFalse(SurveyItem(lat = 31.6, lng = 774.2).hasMapLocation)
        assertFalse(SurveyItem(lat = Double.NaN, lng = 74.2).hasMapLocation)
        assertFalse(SurveyItem().hasMapLocation)
        assertTrue(SurveyItem(lat = 0.0, lng = 74.2).hasMapLocation)
    }

    @Test fun isolatedOutlierDoesNotZoomAwayFromSurveyArea() {
        val local = (1..100).map { SurveyItem(id = "$it", lat = 31.6 + it * 0.0001, lng = 74.3) }
        val remote = SurveyItem(id = "outlier", lat = 74.2, lng = 31.6)
        val all = local + remote
        assertEquals(local, initialMapFocus(all))
        assertEquals(101, all.count { it.hasMapLocation })
    }

    @Test fun smallOrWidelyDistributedSetsKeepTheirExtent() {
        val all = listOf(SurveyItem(lat = 31.6, lng = 74.3), SurveyItem(lat = 12.0, lng = 4.0))
        assertEquals(all, initialMapFocus(all))
    }
}

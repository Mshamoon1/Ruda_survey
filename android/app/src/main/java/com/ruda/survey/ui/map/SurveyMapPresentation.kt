package com.ruda.survey.ui.map

import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.ui.survey.SurveyFormProgress

/** Survey completion is separate from a building's structural status or upload status. */
internal val SurveyItem.isSurveyed: Boolean
    get() = SurveyFormProgress(
        parcelId = parcelId, packageNo = pkg,
        hasDoorPhoto = imgOne.isNotBlank() || !image1LocalPath.isNullOrBlank() || image1Bytes != null,
        ownerName = ownerName, cnic = cnic, contact = phone, village = village,
        khasraNo = khasraNo, status = status, construction = natureOfConstruction
    ).percentage == 100

internal val SurveyItem.mapPointColor: String
    get() = if (isSurveyed) "#00A878" else "#E45161"

package com.ruda.survey.ui.survey

/** Read-only presentation of the requirements in SurveyFormFragment.validateForm(). */
internal data class SurveyFormProgress(
    val parcelId: String = "",
    val packageNo: String = "",
    val hasDoorPhoto: Boolean = false,
    val ownerName: String = "",
    val cnic: String = "",
    val contact: String = "",
    val village: String = "",
    val khasraNo: String = "",
    val status: String = "",
    val construction: String = ""
) {
    // Coordinates are optional in validation; this section requires the Door Pic only.
    // Keep the same CNIC/contact checks (including dash handling) as validateForm().
    private val requirements = listOf(
        listOf(parcelId.isNotBlank(), packageNo.isNotBlank()),
        listOf(hasDoorPhoto),
        listOf(ownerName.isNotBlank(), cnic.replace("-", "").let { it.isNotBlank() && it.length == 13 },
            contact.replace("-", "").let { it.isNotBlank() && it.length >= 10 }),
        listOf(village.isNotBlank(), khasraNo.isNotBlank()),
        listOf(status.isNotBlank(), construction.isNotBlank())
    )
    val completedSections: List<Boolean> = requirements.map { section -> section.all { it } }
    val completedCount: Int = completedSections.count { it }
    val percentage: Int = requirements.flatten().let { fields -> fields.count { it } * 100 / fields.size }
}

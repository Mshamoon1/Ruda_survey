package com.ruda.survey.domain.model

val SurveyItem.hasMapLocation: Boolean
    get() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 &&
        lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)

/** Camera focus only: remote outliers stay on the map and remain searchable. */
fun initialMapFocus(surveys: List<SurveyItem>): List<SurveyItem> {
    val valid = surveys.filter { it.hasMapLocation }
    if (valid.size < 20) return valid
    val latitudes = valid.map { it.lat }.sorted()
    val longitudes = valid.map { it.lng }.sorted()
    fun range(values: List<Double>): ClosedFloatingPointRange<Double> {
        val median = values[values.size / 2]
        val spread = ((values[values.size * 3 / 4] - values[values.size / 4]) * 3).coerceAtLeast(0.1)
        return (median - spread)..(median + spread)
    }
    val latitudeRange = range(latitudes)
    val longitudeRange = range(longitudes)
    val focused = valid.filter { it.lat in latitudeRange && it.lng in longitudeRange }
    return if (focused.size >= valid.size * 0.9) focused else valid
}

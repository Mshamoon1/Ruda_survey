package com.ruda.survey.ui.map

import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ruda.survey.R
import com.ruda.survey.domain.model.SurveyItem
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.util.Locale

class SurveyInfoWindow(
    mapView: MapView,
    private val onDetailsClick: (SurveyItem) -> Unit
) : MarkerInfoWindow(R.layout.item_survey_map_info, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val surveyItem = marker.relatedObject as? SurveyItem ?: return

        val view = mView
        val tvParcelId = view.findViewById<TextView>(R.id.tvParcelId)
        val tvOwnerName = view.findViewById<TextView>(R.id.tvOwnerName)
        val tvVillage = view.findViewById<TextView>(R.id.tvVillage)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val tvCoordinates = view.findViewById<TextView>(R.id.tvCoordinates)

        tvParcelId.text = surveyItem.parcelId.ifEmpty { "No Parcel ID" }
        tvOwnerName.text = surveyItem.ownerName.ifEmpty { "Unknown Owner" }
        tvVillage.text = surveyItem.village.ifEmpty { "-" }
        
        tvStatus.text = surveyItem.status.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }
        
        val statusColor = if (surveyItem.status.contains("complete", true)) {
            ContextCompat.getColor(view.context, R.color.status_success)
        } else {
            ContextCompat.getColor(view.context, R.color.status_warning)
        }
        tvStatus.setTextColor(statusColor)

        tvCoordinates.text = String.format(Locale.US, "%.6f, %.6f", surveyItem.lat, surveyItem.lng)

        view.setOnClickListener {
            onDetailsClick(surveyItem)
        }
    }
}

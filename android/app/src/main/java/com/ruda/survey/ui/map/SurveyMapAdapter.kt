package com.ruda.survey.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ruda.survey.R
import com.ruda.survey.domain.model.SurveyItem

class SurveyMapAdapter(
    private val onClick: (SurveyItem) -> Unit
) : ListAdapter<SurveyItem, SurveyMapAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_survey_map, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvParcelId: TextView = view.findViewById(R.id.tvParcelId)
        private val tvOwnerName: TextView = view.findViewById(R.id.tvOwnerName)
        private val tvVillage: TextView = view.findViewById(R.id.tvVillage)
        private val viewDot: View = view.findViewById(R.id.viewDot)
        private val cardRoot: View = view.findViewById(R.id.cardRoot)

        fun bind(item: SurveyItem) {
            tvParcelId.text = item.parcelId.ifEmpty { "No Parcel ID" }
            tvOwnerName.text = item.ownerName.ifEmpty { "Unknown Owner" }
            tvVillage.text = item.village.ifEmpty { "-" }

            // Color dot: green if has GPS, red if no GPS
            val hasGps = item.lat != 0.0 && item.lng != 0.0
            val dotColor = if (hasGps) {
                ContextCompat.getColor(itemView.context, R.color.status_success)
            } else {
                ContextCompat.getColor(itemView.context, R.color.status_error)
            }
            viewDot.background.setTint(dotColor)

            cardRoot.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SurveyItem>() {
            override fun areItemsTheSame(a: SurveyItem, b: SurveyItem) = a.id == b.id
            override fun areContentsTheSame(a: SurveyItem, b: SurveyItem) = a == b
        }
    }
}

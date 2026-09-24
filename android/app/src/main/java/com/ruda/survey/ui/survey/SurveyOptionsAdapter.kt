package com.ruda.survey.ui.survey

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/** Fixed-choice exposed dropdowns must never turn their selection into a search query. */
internal class SurveyOptionsAdapter(context: Context, options: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, options.toList()) {

    private val allOptions = options.toList()
    private val fullListFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults = FilterResults().apply {
            values = allOptions
            count = allOptions.size
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            // The adapter always owns the full list; never replace it with matching rows.
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = fullListFilter
}

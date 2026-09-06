package com.ruda.survey.ui.survey

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ruda.survey.databinding.ItemSearchResultBinding
import com.ruda.survey.domain.model.SearchParcel

class SearchResultsAdapter(
    private val onItemClick: (SearchParcel) -> Unit
) : ListAdapter<SearchParcel, SearchResultsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchParcel) {
            binding.tvParcelCode.text = "SR No: ${item.srNo}"
            binding.tvKhasra.text = item.village?.let { "Village: $it" } ?: "Village: N/A"
            binding.tvMauza.text = item.structuralName?.let { "Structure: $it" } ?: "Structure: N/A"
            binding.tvOwner.text = item.ownerName?.let { "Owner: $it" } ?: "Owner: N/A"
            binding.tvLocation.text = item.natureOfConstruction?.ifBlank { "" } ?: ""

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SearchParcel>() {
        override fun areItemsTheSame(oldItem: SearchParcel, newItem: SearchParcel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SearchParcel, newItem: SearchParcel): Boolean {
            return oldItem == newItem
        }
    }
}

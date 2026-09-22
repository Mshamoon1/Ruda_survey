package com.ruda.survey.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentSurveyListBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SurveyListFragment : Fragment() {

    private var _binding: FragmentSurveyListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveyListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSurveysState.collect { state ->
                if (!isAdded) return@collect
                when (state) {
                    is UiState.Loading -> {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvSurveys.visibility = View.GONE
                        binding.tvSurveyCount.text = "Loading..."
                    }
                    is UiState.Success -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val allSurveys = state.data
                            val ctx = requireContext().applicationContext
                            val tokenMgr = RepositoryFactory.getTokenManager(ctx)

                            val surveys = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                val allowedIds = tokenMgr.getUserSurveyIds()
                                allSurveys.filter { allowedIds.contains(it.id) }
                            }

                            if (!isAdded) return@launch

                            if (surveys.isEmpty()) {
                                binding.layoutEmpty.visibility = View.VISIBLE
                                binding.rvSurveys.visibility = View.GONE
                                binding.tvSurveyCount.text = "No surveys found"
                            } else {
                                binding.layoutEmpty.visibility = View.GONE
                                binding.rvSurveys.visibility = View.VISIBLE
                                binding.tvSurveyCount.text = "${surveys.size} survey${if (surveys.size != 1) "s" else ""} found"
                                binding.rvSurveys.layoutManager = LinearLayoutManager(requireContext())
                                binding.rvSurveys.adapter = SurveyListAdapter(surveys)
                            }
                        }
                    }
                    is UiState.Error -> {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvSurveys.visibility = View.GONE
                        binding.tvSurveyCount.text = state.message ?: "Failed to load surveys"
                    }
                    is UiState.Empty -> { }
                }
            }
        }

        viewModel.loadAllSurveys()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            viewModel.loadAllSurveys()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SurveyListAdapter(
        private val surveys: List<SurveyItem>
    ) : RecyclerView.Adapter<SurveyListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvParcelId: com.google.android.material.textview.MaterialTextView = view.findViewById(R.id.tvParcelId)
            val tvOwnerName: com.google.android.material.textview.MaterialTextView = view.findViewById(R.id.tvOwnerName)
            val tvVillage: com.google.android.material.textview.MaterialTextView = view.findViewById(R.id.tvVillage)
            val tvDate: com.google.android.material.textview.MaterialTextView = view.findViewById(R.id.tvDate)
            val chipStatus: com.google.android.material.chip.Chip = view.findViewById(R.id.chipStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_survey_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = surveys[position]
            holder.tvParcelId.text = item.parcelId.ifEmpty { "Unknown Parcel" }
            holder.tvOwnerName.text = "Owner: ${item.ownerName.ifEmpty { "N/A" }}"
            holder.tvVillage.text = "Village: ${item.village.ifEmpty { "N/A" }}"
            holder.tvDate.text = ""

            val status = item.status.ifEmpty { "draft" }
            holder.chipStatus.text = status.replaceFirstChar { it.uppercase() }
            val context = holder.itemView.context
            when (status.lowercase()) {
                "submitted", "synced" -> {
                    holder.chipStatus.setChipBackgroundColorResource(R.color.status_success)
                    holder.chipStatus.setTextColor(context.getColor(R.color.md_theme_surface))
                }
                "pending" -> {
                    holder.chipStatus.setChipBackgroundColorResource(R.color.status_warning)
                    holder.chipStatus.setTextColor(context.getColor(R.color.md_theme_surface))
                }
                else -> {
                    holder.chipStatus.setChipBackgroundColorResource(R.color.md_theme_surfaceVariant)
                    holder.chipStatus.setTextColor(context.getColor(R.color.md_theme_onSurfaceVariant))
                }
            }
        }

        override fun getItemCount() = surveys.size
    }
}

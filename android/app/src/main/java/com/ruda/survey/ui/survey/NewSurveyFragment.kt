package com.ruda.survey.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentNewSurveyBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.animateTapFeedback
import kotlinx.coroutines.launch

class NewSurveyFragment : Fragment() {
    private var _binding: FragmentNewSurveyBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewSurveyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        setupClickListeners()
        observeStates()
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        binding.etSerialNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.btnSerialLookup.performClick()
                true
            } else false
        }

        binding.btnSerialLookup.setOnClickListener {
            it.animateTapFeedback {
                val srNo = binding.etSerialNumber.text.toString().trim()
                if (srNo.isBlank()) {
                    Snackbar.make(binding.root, "Enter a serial number", Snackbar.LENGTH_SHORT).show()
                    return@animateTapFeedback
                }
                viewModel.lookupBySrNo(srNo)
            }
        }

        binding.btnViewOriginal.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_newSurvey_to_originalData)
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.srNoLookupState.collect { state ->
                binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                binding.btnSerialLookup.isEnabled = state !is UiState.Loading
                binding.cardResult.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
                binding.btnViewOriginal.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
                binding.rvSearchResults.visibility = View.GONE
                when (state) {
                    is UiState.Success -> showSurveyResult(state.data)
                    is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    is UiState.Empty -> {
                        binding.etSerialNumber.text?.clear()
                        binding.layoutDetails.removeAllViews()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun showSurveyResult(survey: SurveyItem) {
        binding.tvResultSrNo.text = "SR No: ${survey.srNo}"
        binding.layoutDetails.removeAllViews()

        // Group 1: Identity
        addDetailRow("Sr No.", survey.srNo.toString())
        addDetailRow("Parcel ID", survey.parcelId)
        addDetailRow("Village", survey.village)
        addDetailRow("Owner", survey.ownerName)
        addDetailRow("Father", survey.fName)
        addDetailRow("CNIC", survey.cnic)
        addDetailRow("Phone", survey.phone)

        addDetailDivider()

        // Group 2: Land details
        addDetailRow("Khasra No", survey.khasraNo)
        addDetailRow("Electricity", survey.electricityConnectionName)
        addDetailRow("Land Area", survey.landArea)
        addDetailRow("GPS", if (survey.lat != 0.0 || survey.lng != 0.0) "${survey.lat}, ${survey.lng}" else null)

        addDetailDivider()

        // Group 3: Structure details
        addDetailRow("Structure", survey.structuralName)
        addDetailRow("Status", survey.status?.replace("_", " "))
        addDetailRow("Construction", survey.natureOfConstruction)
        addDetailRow("RD", survey.rd)
        addDetailRow("Package", survey.pkg)
        addDetailRow("Length", survey.length)
        addDetailRow("Width", survey.width)
        addDetailRow("Area", survey.area)

    }

    private fun addDetailRow(label: String, value: String?) {
        val row = layoutInflater.inflate(R.layout.item_survey_detail_row, binding.layoutDetails, false)
        row.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvDetailLabel).text = label
        row.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tvDetailValue).text = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) && !it.equals("undefined", true) } ?: "\u2014"
        binding.layoutDetails.addView(row)
    }

    private fun addDetailDivider() {
        layoutInflater.inflate(R.layout.item_survey_detail_divider, binding.layoutDetails, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

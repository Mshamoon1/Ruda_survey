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

        binding.btnViewOriginal.visibility = View.GONE
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

        binding.btnAdvancedSearch.setOnClickListener {
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
                when (state) {
                    is UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                        binding.btnSerialLookup.isEnabled = false
                        binding.btnViewOriginal.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSerialLookup.isEnabled = true
                        val survey = state.data
                        showSurveyResult(survey)
                    }
                    is UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSerialLookup.isEnabled = true
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                        binding.btnViewOriginal.visibility = View.GONE
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                    is UiState.Empty -> { }
                }
            }
        }
    }

    private fun showSurveyResult(survey: SurveyItem) {
        binding.tvResult.text = buildString {
            appendLine("SR No: ${survey.srNo}")
            appendLine("Village: ${survey.village}")
            appendLine("Owner: ${survey.ownerName}")
            appendLine("Structure: ${survey.structuralName}")
            appendLine("Construction: ${survey.natureOfConstruction}")
        }
        binding.tvResult.visibility = View.VISIBLE
        binding.btnViewOriginal.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

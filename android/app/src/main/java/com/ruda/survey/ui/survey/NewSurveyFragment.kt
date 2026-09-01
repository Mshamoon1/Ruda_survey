package com.ruda.survey.ui.survey

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.ListPopupWindow
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentNewSurveyBinding
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.MotionConstants
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.isReducedMotionEnabled
import com.ruda.survey.utils.startFloatingIdle
import kotlinx.coroutines.launch

class NewSurveyFragment : Fragment() {
    private var _binding: FragmentNewSurveyBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel
    private lateinit var searchAdapter: SearchResultsAdapter
    private var emptyStateAnimator: android.animation.ValueAnimator? = null
    private var allVillages: List<String> = emptyList()
    private var allTehsils: List<String> = emptyList()
    private var ownerPopup: ListPopupWindow? = null
    private var ignoreOwnerTextChange = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewSurveyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository, appContext = requireContext().applicationContext)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        setupSearchResults()
        setupClickListeners()
        setupOwnerAutocomplete()
        setupSerialNumberIme()
        observeStates()

        // Load search options (villages + tehsils) immediately
        viewModel.loadSearchOptions()
    }

    private fun setupSearchResults() {
        searchAdapter = SearchResultsAdapter { searchParcel ->
            viewModel.selectSearchResult(searchParcel.parcelCode)
            viewModel.searchParcel(searchParcel.parcelCode)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun setupSerialNumberIme() {
        binding.etSerialNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.btnSerialLookup.performClick()
                true
            } else false
        }
    }

    private fun setupOwnerAutocomplete() {
        binding.etOwnerName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (ignoreOwnerTextChange) return
                viewModel.onOwnerQueryChanged(s?.toString() ?: "")
            }
        })

        ownerPopup = ListPopupWindow(requireContext()).apply {
            anchorView = binding.etOwnerName
            setWidth(ViewGroup.LayoutParams.MATCH_PARENT)
            setOnItemClickListener { _, _, position, _ ->
                val suggestions = (viewModel.ownerSuggestionsState.value as? UiState.Success)?.data ?: return@setOnItemClickListener
                val selected = suggestions.getOrNull(position) ?: return@setOnItemClickListener
                ignoreOwnerTextChange = true
                binding.etOwnerName.setText(selected.ownerName)
                binding.etOwnerName.setSelection(selected.ownerName.length)
                ignoreOwnerTextChange = false
                dismiss()
                viewModel.clearOwnerSuggestions()
            }
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        // Serial Number Lookup
        binding.btnSerialLookup.setOnClickListener {
            it.animateTapFeedback {
                val srNo = binding.etSerialNumber.text.toString().trim()
                if (srNo.isBlank()) {
                    Snackbar.make(binding.root, "Enter a serial number", Snackbar.LENGTH_SHORT).show()
                    return@animateTapFeedback
                }
                viewModel.lookupBySerialNumber(srNo)
            }
        }

        binding.btnAdvancedSearch.setOnClickListener {
            it.animateTapFeedback {
                val village = binding.spinnerVillage.text.toString().trim().ifBlank { null }
                val tehsil = binding.spinnerTehsil.text.toString().trim().ifBlank { null }
                val ownerName = binding.etOwnerName.text.toString().trim().ifBlank { null }
                val khasra = binding.etKhasraNumber.text.toString().trim().ifBlank { null }
                val mauza = binding.etMauzaNumber.text.toString().trim().ifBlank { null }

                if (village == null && tehsil == null && ownerName == null && khasra == null && mauza == null) {
                    Snackbar.make(binding.root, "Enter at least one search criterion", Snackbar.LENGTH_SHORT).show()
                    return@animateTapFeedback
                }
                viewModel.searchParcels(village, tehsil, ownerName, khasra, mauza)
            }
        }

        // Tehsil → Village dependent filter
        binding.spinnerTehsil.setOnItemClickListener { _, _, position, _ ->
            val selectedTehsil = allTehsils.getOrNull(position)
            if (selectedTehsil != null) {
                viewModel.loadSearchOptions(tehsil = selectedTehsil)
            }
        }

        // Clear Tehsil → reload all villages
        binding.spinnerTehsil.text = null
    }

    private fun observeStates() {
        // Search options (villages + tehsils)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchOptionsState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.spinnerVillage.isEnabled = false
                        binding.spinnerTehsil.isEnabled = false
                    }
                    is UiState.Success -> {
                        binding.spinnerVillage.isEnabled = true
                        binding.spinnerTehsil.isEnabled = true
                        val (villages, tehsils) = state.data
                        allVillages = villages
                        allTehsils = tehsils

                        // Only update tehsil adapter if not filtered
                        val currentTehsil = binding.spinnerTehsil.text.toString()
                        if (currentTehsil.isBlank()) {
                            val tehsilAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tehsils)
                            binding.spinnerTehsil.setAdapter(tehsilAdapter)
                        }

                        val villageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, villages)
                        binding.spinnerVillage.setAdapter(villageAdapter)
                    }
                    is UiState.Error -> {
                        binding.spinnerVillage.isEnabled = true
                        binding.spinnerTehsil.isEnabled = true
                        Snackbar.make(binding.root, getString(R.string.error_load_options), Snackbar.LENGTH_SHORT).show()
                    }
                    is UiState.Empty -> { }
                }
            }
        }

        // Serial Number lookup results
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.srNoLookupState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                        binding.btnSerialLookup.isEnabled = false
                    }
                    is UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSerialLookup.isEnabled = true
                        val results = state.data
                        if (results.isEmpty()) {
                            binding.tvResult.text = getString(R.string.label_no_results)
                            binding.tvResult.visibility = View.VISIBLE
                            binding.rvSearchResults.visibility = View.GONE
                        } else if (results.size == 1) {
                            // Single parcel found — auto-select and fetch full info
                            val parcel = results[0]
                            binding.tvResult.text = "Found: ${parcel.parcelCode}\n" +
                                    "Village: ${parcel.village}\n" +
                                    "Owner: ${parcel.ownerName}"
                            binding.tvResult.visibility = View.VISIBLE
                            viewModel.selectSearchResult(parcel.parcelCode)
                            viewModel.searchParcel(parcel.parcelCode)
                            viewModel.resetSrNoLookup()
                        } else {
                            binding.tvResult.text = getString(R.string.label_found_parcels, results.size, results.firstOrNull()?.let {
                                binding.etSerialNumber.text.toString().trim().toIntOrNull() ?: 0
                            } ?: 0)
                            binding.tvResult.visibility = View.VISIBLE
                            // Convert to SearchParcel for the existing adapter
                            val searchParcels = results.map { r ->
                                com.ruda.survey.domain.model.SearchParcel(
                                    parcelCode = r.parcelCode,
                                    khasraNumber = null,
                                    mauzaNumber = null,
                                    ownerName = r.ownerName,
                                    village = r.village,
                                    tehsil = r.tehsil,
                                    district = r.district,
                                    sourceNid = null
                                )
                            }
                            searchAdapter.submitList(searchParcels)
                            binding.rvSearchResults.visibility = View.VISIBLE
                        }
                    }
                    is UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSerialLookup.isEnabled = true
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                        view?.let { Snackbar.make(it, state.message, Snackbar.LENGTH_LONG).show() }
                    }
                    is UiState.Empty -> { }
                }
            }
        }

        // Owner autocomplete suggestions
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ownerSuggestionsState.collect { state ->
                when (state) {
                    is UiState.Success -> {
                        val suggestions = state.data
                        if (suggestions.isEmpty()) {
                            ownerPopup?.dismiss()
                        } else {
                            val displayNames = suggestions.map { "${it.ownerName} (${it.recordCount})" }
                            val adapter = ArrayAdapter(
                                requireContext(),
                                android.R.layout.simple_dropdown_item_1line,
                                displayNames
                            )
                            ownerPopup?.setAdapter(adapter)
                            if (!ownerPopup?.isShowing!!) {
                                ownerPopup?.show()
                            }
                        }
                    }
                    is UiState.Loading -> { }
                    is UiState.Error -> { ownerPopup?.dismiss() }
                    is UiState.Empty -> { ownerPopup?.dismiss() }
                }
            }
        }

        // Parcel ID search
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.parcelState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val info = state.data
                        binding.tvResult.text = "Found: ${info.parcelCode}\n" +
                                "Village: ${info.village}\n" +
                                "Revision: ${info.revisionNo} (${info.source})"
                        binding.tvResult.visibility = View.VISIBLE
                        binding.btnViewOriginal.visibility = View.VISIBLE
                        binding.btnViewOriginal.setOnClickListener {
                            if (isAdded) findNavController().navigate(R.id.action_newSurvey_to_originalData)
                        }
                    }
                    is UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                        view?.let { Snackbar.make(it, state.message, Snackbar.LENGTH_LONG).show() }
                    }
                    is UiState.Empty -> { }
                }
            }
        }

        // Advanced search results
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResultsState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.btnAdvancedSearch.isEnabled = false
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.btnAdvancedSearch.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        val results = state.data
                        if (results.isEmpty()) {
                            binding.tvResult.text = getString(R.string.label_no_results)
                            binding.tvResult.visibility = View.VISIBLE
                            binding.rvSearchResults.visibility = View.GONE
                            emptyStateAnimator?.cancel()
                            emptyStateAnimator = binding.tvResult.startFloatingIdle()
                        } else {
                            binding.tvResult.text = "Found ${results.size} result(s)"
                            binding.tvResult.visibility = View.VISIBLE
                            emptyStateAnimator?.cancel()
                            searchAdapter.submitList(results)
                            binding.rvSearchResults.visibility = View.VISIBLE

                            // Staggered card entrance
                            val reduced = view?.isReducedMotionEnabled() == true
                            if (!reduced) {
                                binding.rvSearchResults.post {
                                    val cardViews = (0 until binding.rvSearchResults.childCount)
                                        .map { binding.rvSearchResults.getChildAt(it) }
                                    cardViews.forEachIndexed { index, cardView ->
                                        cardView.alpha = 0f
                                        cardView.translationY = 16f
                                        cardView.animate()
                                            .alpha(1f)
                                            .translationY(0f)
                                            .setDuration(MotionConstants.DURATION_STANDARD)
                                            .setStartDelay(minOf(
                                                index * MotionConstants.CARD_STAGGER,
                                                MotionConstants.CARD_STAGGER_CAP
                                            ))
                                            .setInterpolator(MotionConstants.EASING_ENTRANCE)
                                            .start()
                                    }
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        binding.btnAdvancedSearch.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        binding.tvResult.visibility = View.GONE
                        binding.rvSearchResults.visibility = View.GONE
                        view?.let { Snackbar.make(it, state.message, Snackbar.LENGTH_LONG).show() }
                    }
                    is UiState.Empty -> { }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ownerPopup?.dismiss()
        ownerPopup = null
        emptyStateAnimator?.cancel()
        _binding = null
    }
}

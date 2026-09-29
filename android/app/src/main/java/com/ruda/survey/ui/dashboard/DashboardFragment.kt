package com.ruda.survey.ui.dashboard

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.ui.map.SurveyMapController
import com.mapbox.maps.MapView
import com.ruda.survey.R
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.databinding.FragmentDashboardBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.hasMapLocation
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.ui.survey.SurveyViewModel
import com.ruda.survey.ui.survey.SurveyViewModelFactory
import com.ruda.survey.ui.sync.SyncViewModel
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.staggerFadeIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var syncViewModel: SyncViewModel? = null
    private var surveyViewModel: SurveyViewModel? = null
    private var mapController: SurveyMapController? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupMapView()
        setupSyncViewModel()
        setupSurveyViewModel()

        animateEntrance()
        loadDashboardData()
    }

    private fun setupUI() {
        val welcomeHtml = "Welcome, <font color='#1B7F4C'><b>Surveyor</b></font>"
        binding.tvWelcome.text = Html.fromHtml(welcomeHtml, Html.FROM_HTML_MODE_LEGACY)
        binding.tvRole.text = getString(R.string.dashboard_role, "Field Surveyor")
        binding.imgNespakLink.setOnClickListener {
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://nespak.com.pk/")))
            } catch (_: android.content.ActivityNotFoundException) {
                Snackbar.make(binding.root, R.string.no_browser_available, Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.cardKpiTotal.isClickable = false
        binding.cardKpiTotal.isFocusable = false

        binding.btnNewSurvey.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) {
                    surveyViewModel?.startUpdateSession()
                    findNavController().navigate(R.id.action_dashboard_to_newSurvey)
                }
            }
        }

        binding.btnCreateSurvey.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val draft = try {
                            withContext(Dispatchers.IO) {
                                RepositoryFactory.create(requireContext().applicationContext).getDraft()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Snackbar.make(binding.root, e.message ?: "Could not open local draft", Snackbar.LENGTH_LONG).show()
                            return@launch
                        }
                        surveyViewModel?.clearPendingImages()
                        surveyViewModel?.setPendingDoc(null, null)
                        surveyViewModel?.saveFormState(draft ?: SurveyItem())
                        if (isAdded) findNavController().navigate(R.id.action_dashboard_to_surveyForm)
                    }
                }
            }
        }

        binding.btnExpandMap.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) findNavController().navigate(R.id.action_dashboard_to_maps)
            }
        }

        binding.btnMyLocation.setOnClickListener {
            it.animateTapFeedback {
                mapController?.centerOn(31.60, 74.30)
            }
        }

        binding.btnMapLayers.setOnClickListener {
            it.animateTapFeedback {
                // Stub — no layer toggle logic exists yet
            }
        }

        binding.btnLogout.setOnClickListener {
            it.animateTapFeedback {
                showLogoutConfirmation()
            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_map -> {
                    if (isAdded) findNavController().navigate(R.id.action_dashboard_to_maps)
                    true
                }
                R.id.nav_surveys -> {
                    if (isAdded) findNavController().navigate(R.id.action_dashboard_to_surveyList)
                    true
                }
                R.id.nav_profile -> {
                    if (isAdded) findNavController().navigate(R.id.action_dashboard_to_profile)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadDashboardData() {
        surveyViewModel?.loadAllSurveys(forceRefresh = true)

        binding.tvKpiTotal.text = "—"
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = RepositoryFactory.create(requireContext().applicationContext)
            fun showTotal(total: Int?) {
                if (total != null) binding.tvKpiTotal.text =
                    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(total)
            }
            showTotal(repository.getBackendSurveyTotal().getOrNull())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tokenManager = RepositoryFactory.getTokenManager(requireContext().applicationContext)
                val count = tokenManager.getNewSurveyCount().toString()
                if (_binding != null) binding.tvKpiNew.text = count
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                if (_binding != null) binding.tvKpiNew.text = "0"
            }
        }
    }

    private fun setupMapView() {
        mapView = binding.mapView
        mapController = SurveyMapController(binding.mapView, viewLifecycleOwner.lifecycleScope,
            onDetails = { item ->
                surveyViewModel?.saveFormState(item)
                if (isAdded) findNavController().navigate(R.id.action_dashboard_to_surveyForm)
            },
            onError = {
                if (_binding != null) Snackbar.make(binding.root,
                    "Map could not load. Check your connection and reopen the map.", Snackbar.LENGTH_LONG).show()
            }).also { it.initialize() }
    }

    private fun placePins(surveys: List<SurveyItem>) {
        mapController?.submit(surveys)
        if (surveys.any { it.hasMapLocation }) hideMapEmpty() else showMapEmpty()
    }

    private fun showMapEmpty() {
        binding.layoutMapEmpty.visibility = View.VISIBLE
    }

    private fun hideMapEmpty() {
        binding.layoutMapEmpty.visibility = View.GONE
    }

    private fun setupSyncViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext().applicationContext
            val repository = withContext(Dispatchers.IO) { RepositoryFactory.createSyncRepository(context) }
            val factory = SyncViewModelFactory(repository, SurveyDatabase.getInstance(context).syncDao(),
                ConnectivityObserver(context), context)
            val model = ViewModelProvider(this@DashboardFragment, factory)[SyncViewModel::class.java]
            syncViewModel = model
            binding.cardSyncStatus.visibility = View.VISIBLE
            binding.btnSyncNow.setOnClickListener {
                if (model.syncState.value.authenticationRequired && model.syncState.value.isOnline) {
                    findNavController().navigate(R.id.action_dashboard_to_login,
                        androidx.core.os.bundleOf("forceLogin" to true))
                } else model.triggerSync()
            }
            binding.cardSyncStatus.setOnClickListener {
                val issues = model.issues.value
                if (issues.isNotEmpty()) MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Synchronization needs attention")
                    .setMessage(issues.joinToString("\n\n"))
                    .setPositiveButton(android.R.string.ok, null).show()
            }
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.syncState.collect { state ->
                        binding.tvPendingCount.text = state.pendingCount.toString()
                        binding.tvKpiPending.text = state.totalActionable.toString()
                        val last = state.lastSyncTime?.let {
                            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))
                        } ?: getString(R.string.never)
                        binding.tvLastSync.text = getString(R.string.label_last_sync, last) +
                            "\nFailed: " + state.failedCount + "  Conflicts: " + state.conflictCount
                        binding.tvSyncStatus.text = when {
                            !state.isOnline -> "Offline Mode"
                            state.authenticationRequired -> "Sign in to sync"
                            state.isSyncing -> "Syncing..."
                            state.totalActionable == 0 -> "Online"
                            else -> "Online • Changes stored locally"
                        }
                        binding.btnSyncNow.text = if (state.authenticationRequired && state.isOnline) "Sign In"
                            else getString(R.string.btn_sync_now)
                        binding.btnSyncNow.visibility = if (state.totalActionable > 0 || state.authenticationRequired) View.VISIBLE else View.GONE
                        binding.btnSyncNow.isEnabled = !state.isSyncing
                    }
                }
                launch {
                    model.syncOutcome.collect { outcome ->
                        if (outcome is com.ruda.survey.domain.model.SyncOutcome.Error) {
                            Snackbar.make(binding.root, outcome.message, Snackbar.LENGTH_LONG).show()
                            model.clearOutcome()
                        }
                    }
                }
            }
        }
    }
    private fun setupSurveyViewModel() {
        val repository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        surveyViewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    surveyViewModel?.allSurveysState?.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val surveys = state.data
                                placePins(surveys)
                                val total = RepositoryFactory.create(requireContext().applicationContext)
                                    .getBackendSurveyTotal().getOrNull()
                                if (total != null) binding.tvKpiTotal.text =
                                    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(total)
                            }
                            is UiState.Error -> {
                                showMapEmpty()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    surveyViewModel?.createState?.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                binding.btnCreateSurvey.isEnabled = false
                            }
                            is UiState.Success -> {
                                binding.btnCreateSurvey.isEnabled = true
                                surveyViewModel?.resetCreateState()
                            }
                            is UiState.Error -> {
                                binding.btnCreateSurvey.isEnabled = true
                                Snackbar.make(
                                    binding.root, state.message, Snackbar.LENGTH_LONG
                                ).show()
                                surveyViewModel?.resetCreateState()
                            }
                            else -> {
                                binding.btnCreateSurvey.isEnabled = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showLogoutConfirmation() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.btn_logout))
        builder.setMessage(getString(R.string.btn_logout_confirm))
        builder.setPositiveButton(getString(R.string.btn_logout_confirm_action)) { _, _ ->
            val ctx = requireContext().applicationContext
            val tm = RepositoryFactory.getTokenManager(ctx)
            tm.resetNewSurveyCount()
            tm.clearTokens()
            if (isAdded) findNavController().navigate(R.id.action_dashboard_to_login)
        }
        builder.setNegativeButton(getString(R.string.btn_logout_cancel), null)
        builder.show()
    }

    private fun animateEntrance() {
        val cards = listOf(
            binding.cardKpiTotal,
            binding.cardKpiNew,
            binding.cardKpiPending,
            binding.cardMap,
            binding.cardSyncStatus,
            binding.btnNewSurvey,
            binding.btnCreateSurvey
        )
        cards.staggerFadeIn()
    }

    override fun onResume() {
        super.onResume()
        try {
            val tokenManager = RepositoryFactory.getTokenManager(requireContext().applicationContext)
            binding.tvKpiNew.text = tokenManager.getNewSurveyCount().toString()
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        mapController?.close()
        mapController = null
        mapView = null
        super.onDestroyView()
        _binding = null
    }
}

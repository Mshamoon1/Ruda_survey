package com.ruda.survey.ui.dashboard

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.ruda.survey.R
import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.databinding.FragmentDashboardBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.ui.survey.SurveyViewModel
import com.ruda.survey.ui.survey.SurveyViewModelFactory
import com.ruda.survey.ui.sync.SyncViewModel
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.staggerFadeIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var pendingSurveys: List<SurveyItem> = emptyList()

    private val lahoreCenter = GeoPoint(31.5204, 74.3587)
    private val defaultZoom = 13.0

    private var syncViewModel: SyncViewModel? = null
    private var surveyViewModel: SurveyViewModel? = null

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
    }

    private fun setupUI() {
        binding.tvWelcome.text = getString(R.string.dashboard_welcome, "Surveyor")
        binding.tvRole.text = getString(R.string.dashboard_role, "Field Surveyor")

        binding.btnNewSurvey.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) findNavController().navigate(R.id.action_dashboard_to_newSurvey)
            }
        }

        binding.btnCreateSurvey.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) {
                    surveyViewModel?.saveFormState(SurveyItem())
                    findNavController().navigate(R.id.action_dashboard_to_surveyForm)
                }
            }
        }

        binding.btnLogout.setOnClickListener {
            it.animateTapFeedback {
                showLogoutConfirmation()
            }
        }
    }

    private fun setupMapView() {
        mapView = binding.mapView
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(defaultZoom)
            controller.setCenter(lahoreCenter)
            
            // Handle touch to prevent scrollview from intercepting
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
        loadSurveysAndPin()
    }

    private fun loadSurveysAndPin() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = requireContext().applicationContext
                val repository = RepositoryFactory.createSurveyRepository(context)
                val result = repository.getAllSurveys()
                if (result.isSuccess) {
                    pendingSurveys = result.getOrDefault(emptyList())
                    placePins(pendingSurveys)
                } else {
                    showMapEmpty()
                }
            } catch (_: Exception) {
                showMapEmpty()
            }
        }
    }

    private fun placePins(surveys: List<SurveyItem>) {
        val map = mapView ?: return
        map.overlays.clear()

        val validSurveys = surveys.filter { item ->
            item.lat != 0.0 && item.lng != 0.0
        }

        if (validSurveys.isEmpty()) {
            showMapEmpty()
            return
        }

        hideMapEmpty()

        validSurveys.forEach { item ->
            val position = GeoPoint(item.lat, item.lng)
            val marker = Marker(map)
            marker.position = position
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = item.parcelId.ifEmpty { item.village }
            marker.snippet = item.ownerName.ifEmpty { getString(R.string.label_survey_pin) }
            
            // Custom icon if needed, otherwise default is used
            // marker.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_location)
            
            marker.setOnMarkerClickListener { m, _ ->
                m.showInfoWindow()
                true
            }
            map.overlays.add(marker)
        }

        if (validSurveys.isNotEmpty()) {
            // Zoom to first marker or center
            val first = validSurveys.first()
            map.controller.animateTo(GeoPoint(first.lat, first.lng))
        }

        map.invalidate()
    }

    private fun showMapEmpty() {
        binding.layoutMapEmpty.visibility = View.VISIBLE
    }

    private fun hideMapEmpty() {
        binding.layoutMapEmpty.visibility = View.GONE
    }

    private fun setupSyncViewModel() {
        try {
            val context = requireContext().applicationContext
            val db = com.ruda.survey.data.local.SurveyDatabase.getInstance(context)
            val syncDao: SyncDao = db.syncDao()
            val api = com.ruda.survey.data.remote.ApiClient.createSurveyApi(context)
            val tokenManager = com.ruda.survey.data.remote.SecureTokenManager(context)
            val syncRepository = SyncRepository(api, syncDao, tokenManager)
            val connectivityObserver = ConnectivityObserver(context)

            val factory = SyncViewModelFactory(
                syncRepository, syncDao, connectivityObserver, context
            )
            syncViewModel = ViewModelProvider(this, factory)[SyncViewModel::class.java]

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    syncViewModel?.syncState?.collectLatest { state ->
                        binding.cardSyncStatus.visibility = View.VISIBLE
                        binding.tvPendingCount.text = state.pendingCount.toString()

                        val syncTime = state.lastSyncTime ?: 0L
                        val lastSyncText = if (syncTime > 0) {
                            val elapsed = System.currentTimeMillis() - syncTime
                            when {
                                elapsed < 60_000 -> "Just now"
                                elapsed < 3_600_000 -> "${elapsed / 60_000}m ago"
                                elapsed < 86_400_000 -> "${elapsed / 3_600_000}h ago"
                                else -> getString(R.string.never)
                            }
                        } else {
                            getString(R.string.never)
                        }
                        binding.tvLastSync.text = getString(
                            R.string.label_last_sync
                        ) + ": " + lastSyncText

                        binding.tvSyncStatus.text = when {
                            state.isSyncing -> "Syncing\u2026"
                            state.pendingCount == 0 -> "All synced"
                            state.isOnline -> "Ready to sync"
                            else -> "Offline"
                        }

                        binding.btnSyncNow.visibility = if (state.pendingCount > 0 && !state.isSyncing) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
            }

            binding.btnSyncNow.setOnClickListener {
                it.animateTapFeedback {
                    syncViewModel?.triggerSync()
                }
            }
        } catch (_: Exception) {
            binding.cardSyncStatus.visibility = View.GONE
        }
    }

    private fun setupSurveyViewModel() {
        val repository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        surveyViewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                surveyViewModel?.createState?.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.btnCreateSurvey.isEnabled = false
                        }
                        is UiState.Success -> {
                            binding.btnCreateSurvey.isEnabled = true
                            if (isAdded) {
                                findNavController().navigate(R.id.action_dashboard_to_surveyForm)
                                surveyViewModel?.resetCreateState()
                            }
                        }
                        is UiState.Error -> {
                            binding.btnCreateSurvey.isEnabled = true
                            com.google.android.material.snackbar.Snackbar.make(
                                binding.root, state.message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG
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

    private fun showLogoutConfirmation() {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.btn_logout))
        builder.setMessage(getString(R.string.btn_logout_confirm))
        builder.setPositiveButton(getString(R.string.btn_logout_confirm_action)) { _, _ ->
            if (isAdded) findNavController().navigate(R.id.action_dashboard_to_login)
        }
        builder.setNegativeButton(getString(R.string.btn_logout_cancel), null)
        builder.show()
    }

    private fun animateEntrance() {
        val cards = listOf<View>(
            binding.cardMap,
            binding.cardSyncStatus,
            binding.btnNewSurvey,
            binding.btnCreateSurvey
        )
        cards.staggerFadeIn()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        loadSurveysAndPin()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mapView = null
    }
}

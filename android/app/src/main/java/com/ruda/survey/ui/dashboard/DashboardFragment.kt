package com.ruda.survey.ui.dashboard

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.databinding.FragmentDashboardBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.ui.map.SurveyInfoWindow
import com.ruda.survey.ui.survey.SurveyViewModel
import com.ruda.survey.ui.survey.SurveyViewModelFactory
import com.ruda.survey.ui.sync.SyncViewModel
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.staggerFadeIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var syncViewModel: SyncViewModel? = null
    private var surveyViewModel: SurveyViewModel? = null
    private var sharedInfoWindow: SurveyInfoWindow? = null

    private val lahoreCenter = GeoPoint(31.5204, 74.3587)
    private val defaultZoom = 13.0

    companion object {
        private var cachedMarkerBitmap: Bitmap? = null
    }

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
                    surveyViewModel?.saveFormState(SurveyItem())
                    findNavController().navigate(R.id.action_dashboard_to_surveyForm)
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
                mapView?.controller?.animateTo(lahoreCenter, 16.0, 300L)
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
        surveyViewModel?.loadAllSurveys()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tokenManager = RepositoryFactory.getTokenManager(requireContext().applicationContext)
                binding.tvKpiNew.text = tokenManager.getNewSurveyCount().toString()
            } catch (_: Exception) {
                binding.tvKpiNew.text = "0"
            }
        }
    }

    private fun populateKpis(surveys: List<SurveyItem>) {
        animateNumber(binding.tvKpiTotal, surveys.size)
    }

    private fun animateNumber(textView: TextView, targetValue: Int) {
        if (targetValue <= 0) {
            textView.text = "0"
            return
        }
        val animator = ValueAnimator.ofInt(0, targetValue)
        animator.duration = 1500
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            textView.text = animation.animatedValue.toString()
        }
        animator.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapView() {
        mapView = binding.mapView
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(defaultZoom)
            controller.setCenter(lahoreCenter)

            sharedInfoWindow = SurveyInfoWindow(this) { item ->
                surveyViewModel?.saveFormState(item)
                if (isAdded) findNavController().navigate(R.id.action_dashboard_to_surveyForm)
            }

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
    }

    private fun getMarkerBitmap(): Bitmap {
        cachedMarkerBitmap?.let { return it }
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_marker_green) ?: return createBitmap(24, 24)
        val bmp = createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(24),
            drawable.intrinsicHeight.coerceAtLeast(24)
        )
        bmp.applyCanvas {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
        cachedMarkerBitmap = bmp
        return bmp
    }

    private fun placePins(surveys: List<SurveyItem>) {
        val map = mapView ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val (markers, avgPoint) = withContext(Dispatchers.Default) {
                val validSurveys = surveys.filter { it.lat != 0.0 && it.lng != 0.0 }
                if (validSurveys.isEmpty()) return@withContext Pair(emptyList<Marker>(), null)

                val bitmap = getMarkerBitmap()
                val markerDrawable = bitmap.toDrawable(resources)

                val avgLat = validSurveys.map { it.lat }.average()
                val avgLng = validSurveys.map { it.lng }.average()

                val markerList = validSurveys.map { item ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(item.lat, item.lng)
                    marker.relatedObject = item
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.title = item.parcelId.ifEmpty { item.village }
                    marker.snippet = item.ownerName.ifEmpty { getString(R.string.label_survey_pin) }
                    marker.subDescription = item.village
                    marker.icon = markerDrawable
                    marker.infoWindow = sharedInfoWindow
                    marker.setOnMarkerClickListener { m, _ ->
                        m.showInfoWindow()
                        map.controller.animateTo(m.position, 16.0, 300L)
                        true
                    }
                    marker
                }
                Pair(markerList, GeoPoint(avgLat, avgLng))
            }

            if (!isAdded || mapView == null) return@launch

            if (markers.isEmpty()) {
                showMapEmpty()
                return@launch
            }

            hideMapEmpty()
            map.overlays.clear()
            map.overlays.addAll(markers)
            if (avgPoint != null) {
                map.controller.setCenter(avgPoint)
            } else {
                map.controller.setCenter(lahoreCenter)
            }
            map.controller.setZoom(defaultZoom)
            map.invalidate()
        }
    }

    private fun showMapEmpty() {
        binding.layoutMapEmpty.visibility = View.VISIBLE
    }

    private fun hideMapEmpty() {
        binding.layoutMapEmpty.visibility = View.GONE
    }

    private fun setupSyncViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = requireContext().applicationContext
                val syncRepository = withContext(Dispatchers.IO) {
                    RepositoryFactory.createSyncRepository(context)
                }
                val connectivityObserver = withContext(Dispatchers.IO) {
                    ConnectivityObserver(context)
                }

                val factory = SyncViewModelFactory(
                    syncRepository, SurveyDatabase.getInstance(context).syncDao(),
                    connectivityObserver, context
                )
                syncViewModel = ViewModelProvider(this@DashboardFragment, factory)[SyncViewModel::class.java]

                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    syncViewModel?.syncState?.collectLatest { state ->
                        binding.cardSyncStatus.visibility = View.VISIBLE
                        binding.tvPendingCount.text = state.pendingCount.toString()
                        binding.tvKpiPending.text = state.pendingCount.toString()

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
                        binding.tvLastSync.text = getString(R.string.label_last_sync, lastSyncText)

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

                binding.btnSyncNow.setOnClickListener {
                    it.animateTapFeedback {
                        syncViewModel?.triggerSync()
                    }
                }
            } catch (_: Exception) {
                binding.cardSyncStatus.visibility = View.GONE
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
                                populateKpis(surveys)
                                placePins(surveys)
                            }
                            is UiState.Error -> {
                                populateKpis(emptyList())
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
                                if (isAdded) {
                                    findNavController().navigate(R.id.action_dashboard_to_surveyForm)
                                    surveyViewModel?.resetCreateState()
                                }
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
            tm.clearUserSurveyIds()
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
        mapView?.onResume()
        try {
            val tokenManager = RepositoryFactory.getTokenManager(requireContext().applicationContext)
            binding.tvKpiNew.text = tokenManager.getNewSurveyCount().toString()
        } catch (_: Exception) {}
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

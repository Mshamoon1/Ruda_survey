package com.ruda.survey.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentMapsBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.ui.survey.SurveyViewModel
import com.ruda.survey.ui.survey.SurveyViewModelFactory
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapsFragment : Fragment() {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var locationOverlay: MyLocationNewOverlay? = null
    private var sharedInfoWindow: SurveyInfoWindow? = null
    private lateinit var viewModel: SurveyViewModel
    private var allSurveys: List<SurveyItem> = emptyList()
    private val markerMap = mutableMapOf<String, Marker>()
    private var listAdapter: SurveyMapAdapter? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isLoading = false

    private val lahoreCenter = GeoPoint(31.5204, 74.3587)

    private val markerClickListener = Marker.OnMarkerClickListener { m, map ->
        val item = m.relatedObject as? SurveyItem
        if (item != null) {
            m.showInfoWindow()
            map.controller.animateTo(m.position, 15.0, 300L)
        }
        true
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            enableMyLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val repository = RepositoryFactory.createSurveyRepository(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity().viewModelStore, factory).get(SurveyViewModel::class.java)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupMap()
        setupList()
        setupFabs()
    }

    private fun setupMap() {
        mapView = binding.mapView
        mapView?.apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.0)
            controller.setCenter(lahoreCenter)

            sharedInfoWindow = SurveyInfoWindow(this) { item ->
                onSurveyDetailsClick(item)
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

        loadSurveys()
        checkLocationPermission()
    }

    private fun loadSurveys() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allSurveysState.collect { state ->
                    if (!isAdded) return@collect
                    if (state is com.ruda.survey.domain.model.UiState.Success) {
                        prepareAndPlaceMarkers(state.data)
                    }
                }
            }
        }
        viewModel.loadAllSurveys()
    }

    private fun prepareAndPlaceMarkers(all: List<SurveyItem>) {
        val map = mapView ?: return
        allSurveys = all
        updateChipCount()
        listAdapter?.submitList(allSurveys)

        viewLifecycleOwner.lifecycleScope.launch {
            val (markerPairs, avgPoint) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val validSurveys = all.filter { it.lat != 0.0 && it.lng != 0.0 }
                if (validSurveys.isEmpty()) return@withContext Pair(emptyList<Pair<String, Marker>>(), null)

                val markerIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_marker_green)
                val markerDrawable = markerIcon?.let {
                    val bmp = Bitmap.createBitmap(
                        it.intrinsicWidth.coerceAtLeast(24),
                        it.intrinsicHeight.coerceAtLeast(24),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    it.setBounds(0, 0, canvas.width, canvas.height)
                    it.draw(canvas)
                    BitmapDrawable(resources, bmp)
                }

                val avgLat = validSurveys.map { it.lat }.average()
                val avgLng = validSurveys.map { it.lng }.average()

                val pairs = validSurveys.map { item ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(item.lat, item.lng)
                    marker.relatedObject = item
                    marker.infoWindow = sharedInfoWindow
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    markerDrawable?.let { marker.icon = it }
                    marker.setOnMarkerClickListener(markerClickListener)
                    Pair(item.id, marker)
                }
                Pair(pairs, GeoPoint(avgLat, avgLng))
            }

            if (!isAdded || mapView == null) return@launch

            map.overlays.clear()
            markerMap.clear()
            locationOverlay?.let { map.overlays.add(it) }

            markerPairs.forEach { (id, marker) ->
                map.overlays.add(marker)
                markerMap[id] = marker
            }

            if (avgPoint != null) {
                map.controller.setCenter(avgPoint)
            } else {
                map.controller.setCenter(lahoreCenter)
            }
            map.controller.setZoom(12.0)
            map.invalidate()
        }
    }

    private fun onSurveyDetailsClick(item: SurveyItem) {
        viewModel.saveFormState(item)
        findNavController().navigate(R.id.action_maps_to_surveyForm)
    }

    private fun updateChipCount() {
        binding.chipStatus.text = "${allSurveys.size} surveys"
    }

    private fun setupList() {
        listAdapter = SurveyMapAdapter { item ->
            val position = GeoPoint(item.lat, item.lng)
            mapView?.controller?.animateTo(position, 15.0, 300L)
            markerMap[item.id]?.showInfoWindow()
            binding.cardList.visibility = View.GONE
        }

        binding.rvSurveys.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }

        listAdapter?.submitList(allSurveys)
    }

    private fun setupFabs() {
        binding.fabList.setOnClickListener {
            if (binding.cardList.visibility == View.VISIBLE) {
                binding.cardList.visibility = View.GONE
            } else {
                binding.cardList.visibility = View.VISIBLE
                listAdapter?.submitList(allSurveys)
            }
        }

        binding.fabMyLocation.setOnClickListener {
            checkLocationPermission()
        }
    }

    private fun checkLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation() {
        val map = mapView ?: return
        try {
            if (locationOverlay == null) {
                locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
                locationOverlay?.enableMyLocation()
                map.overlays.add(0, locationOverlay)
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    map.controller.animateTo(geoPoint, 14.0, 300L)
                }
            }
        } catch (_: SecurityException) {}
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        locationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        locationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mapView = null
        locationOverlay = null
    }
}

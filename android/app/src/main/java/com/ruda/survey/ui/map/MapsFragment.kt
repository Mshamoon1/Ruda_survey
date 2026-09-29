package com.ruda.survey.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
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
import com.mapbox.maps.MapView
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentMapsBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.hasMapLocation
import com.ruda.survey.ui.survey.SurveyViewModel
import com.ruda.survey.ui.survey.SurveyViewModelFactory
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MapsFragment : Fragment() {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private lateinit var viewModel: SurveyViewModel
    private var allSurveys: List<SurveyItem> = emptyList()
    private var mapController: SurveyMapController? = null
    private var listAdapter: SurveyMapAdapter? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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
        mapController = SurveyMapController(binding.mapView, viewLifecycleOwner.lifecycleScope,
            onDetails = ::onSurveyDetailsClick,
            onError = {
                if (_binding != null) Snackbar.make(binding.root,
                    "Map could not load. Check your connection and reopen the map.", Snackbar.LENGTH_LONG).show()
            }).also { it.initialize() }
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
        viewModel.loadAllSurveys(forceRefresh = true)
    }

    private fun prepareAndPlaceMarkers(all: List<SurveyItem>) {
        allSurveys = all
        updateChipCount()
        if (binding.cardList.visibility == View.VISIBLE) listAdapter?.submitList(all)
        mapController?.submit(all)
    }

    private fun onSurveyDetailsClick(item: SurveyItem) {
        viewModel.saveFormState(item)
        findNavController().navigate(R.id.action_maps_to_surveyForm)
    }

    private fun updateChipCount() {
        val mapped = allSurveys.count { it.hasMapLocation }
        val format = NumberFormat.getIntegerInstance(Locale.US)
        binding.chipStatus.text = "${format.format(mapped)} mapped / ${format.format(allSurveys.size)} surveys"
    }

    private fun setupList() {
        listAdapter = SurveyMapAdapter { item ->
            mapController?.showSurvey(item)
            binding.cardList.visibility = View.GONE
        }

        binding.rvSurveys.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }

        listAdapter?.submitList(allSurveys)
    }

    private fun setupFabs() {
        binding.fabMapStyle.setOnClickListener { mapController?.toggleStyle() }
        binding.fabList.setOnClickListener {
            if (binding.cardList.visibility == View.VISIBLE) {
                binding.cardList.visibility = View.GONE
            } else {
                binding.cardList.visibility = View.VISIBLE
                listAdapter?.submitList(allSurveys)
            }
        }

        binding.fabMyLocation.setOnClickListener {
            checkLocationPermission(centerOnLocation = true)
        }
    }

    private fun checkLocationPermission(centerOnLocation: Boolean = false) {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation(centerOnLocation)
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation(centerOnLocation: Boolean = false) {
        val controller = mapController ?: return
        try {
            controller.enableLocation()
            if (centerOnLocation) fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (mapController !== controller || _binding == null) return@addOnSuccessListener
                if (location != null) controller.centerOn(location.latitude, location.longitude)
                else Snackbar.make(binding.root, "Location unavailable. Please enable GPS.", Snackbar.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) { }
    }

    override fun onDestroyView() {
        mapController?.close()
        mapController = null
        mapView = null
        super.onDestroyView()
        _binding = null
    }
}

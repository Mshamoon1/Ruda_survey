package com.ruda.survey.ui.survey

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentSurveyFormBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.LocationHelper
import com.ruda.survey.utils.MotionConstants
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.isReducedMotionEnabled
import kotlinx.coroutines.launch

class SurveyFormFragment : Fragment() {
    private var _binding: FragmentSurveyFormBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel
    private lateinit var locationHelper: LocationHelper

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleDocumentSelection(it) }
    }

    private var selectedStatus: String = ""
    private var selectedNature: String = ""

    private val validStatuses = setOf("residential", "commercial", "cattle farm", "agricultural", "empty plot", "under construction")
    private val validConstructionNatures = setOf("pacca", "semi-pacca", "kacha")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveyFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        locationHelper = LocationHelper(requireContext())

        setupDropdowns()

        val survey = viewModel.currentSurvey
        if (survey != null && survey.id.isNotBlank()) {
            populateFields(survey)
            animateSectionsIn()
        } else {
            binding.tvParcelCode.text = "New Survey"
            viewModel.setPendingDoc(null, null)
            selectedStatus = ""
            selectedNature = ""
            viewModel.fetchNextSrNo()
        }

        setupClickListeners()
        observeImageStatus()
        observeGpsLocation()
        observeNextSrNo()
    }

    private fun setupDropdowns() {
        val statuses = validStatuses.toTypedArray()
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, statuses)
        binding.etStructureStatus.setAdapter(statusAdapter)
        binding.etStructureStatus.setText("", false)
        binding.etStructureStatus.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = statusAdapter.getItem(position).toString()
            Log.d("SurveyForm", "Status selected from adapter: '$selectedStatus'")
        }

        val constructionNatures = validConstructionNatures.toTypedArray()
        val constructionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, constructionNatures)
        binding.etConstructionNature.setAdapter(constructionAdapter)
        binding.etConstructionNature.setText("", false)
        binding.etConstructionNature.setOnItemClickListener { _, _, position, _ ->
            selectedNature = constructionAdapter.getItem(position).toString()
            Log.d("SurveyForm", "Nature selected from adapter: '$selectedNature'")
        }
    }

    private fun observeNextSrNo() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.nextSrNoState.collect { srNo ->
                if (srNo != null && (binding.etSrNo.text.isNullOrBlank() || binding.etSrNo.text.toString() == "0")) {
                    binding.etSrNo.setText(srNo.toString())
                }
            }
        }
    }

    private fun populateFields(survey: SurveyItem) {
        Log.d("SurveyForm", "populateFields status='${survey.status}' nature='${survey.natureOfConstruction}'")
        binding.tvParcelCode.text = if (survey.srNo > 0) "SR #${survey.srNo}" else survey.village.ifBlank { "New Survey" }
        binding.etSrNo.setText(if (survey.srNo > 0) survey.srNo.toString() else "")
        binding.etParcelCode.setText(survey.parcelId)
        binding.etRdValue.setText(survey.rd)
        binding.etPackageNo.setText(survey.pkg)
        binding.etLatitude.setText(if (survey.lat != 0.0) survey.lat.toString() else "")
        binding.etLongitude.setText(if (survey.lng != 0.0) survey.lng.toString() else "")
        binding.etOwnerName.setText(survey.ownerName)
        binding.etFatherName.setText(survey.fName)
        binding.etCnic.setText(survey.cnic)
        binding.etContact.setText(survey.phone)
        binding.etLandOwnerDoc.setText(survey.landOwnerDoc)
        
        if (viewModel.pendingDocBytes == null && survey.landOwnerDoc.isNotBlank()) {
            binding.tvLandDocStatus.text = "File attached (Server)"
        } else if (viewModel.pendingDocBytes != null) {
            binding.tvLandDocStatus.text = viewModel.pendingDocName ?: "File attached"
        } else {
            binding.tvLandDocStatus.text = "No file selected"
        }

        binding.etVillage.setText(survey.village)
        binding.etKhasraNumber.setText(survey.khasraNo)
        binding.etElectricityConnection.setText(survey.electricityConnectionName)
        binding.etLandArea.setText(survey.landArea)
        
        val matchedStatus = validStatuses.find { it.equals(survey.status, ignoreCase = true) }
        Log.d("SurveyForm", "populateFields matchedStatus='$matchedStatus' for raw='${survey.status}'")
        if (matchedStatus != null) {
            binding.etStructureStatus.setText(matchedStatus, false)
            selectedStatus = matchedStatus
        } else {
            binding.etStructureStatus.setText("", false)
            selectedStatus = ""
        }
        
        binding.etStructureName.setText(survey.structuralName)
        binding.etLengthFt.setText(survey.length)
        binding.etWidthFt.setText(survey.width)
        binding.etAreaSqft.setText(survey.area)
        
        val matchedNature = validConstructionNatures.find { it.equals(survey.natureOfConstruction, ignoreCase = true) }
        Log.d("SurveyForm", "populateFields matchedNature='$matchedNature' for raw='${survey.natureOfConstruction}'")
        if (matchedNature != null) {
            binding.etConstructionNature.setText(matchedNature, false)
            selectedNature = matchedNature
        } else {
            binding.etConstructionNature.setText("", false)
            selectedNature = ""
        }
    }

    private fun observeGpsLocation() {
        val survey = viewModel.currentSurvey
        val hasLocation = survey != null && survey.lat != 0.0 && survey.lng != 0.0
        if (hasLocation) return

        viewLifecycleOwner.lifecycleScope.launch {
            locationHelper.startUpdates { location ->
                if (binding.etLatitude.text.isNullOrBlank() || binding.etLatitude.text.toString() == "0.0") {
                    binding.etLatitude.setText(String.format(java.util.Locale.US, "%.6f", location.latitude))
                }
                if (binding.etLongitude.text.isNullOrBlank() || binding.etLongitude.text.toString() == "0.0") {
                    binding.etLongitude.setText(String.format(java.util.Locale.US, "%.6f", location.longitude))
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSaveDraft.setOnClickListener {
            it.animateTapFeedback {
                saveCurrentFormState()
                val survey = viewModel.currentSurvey ?: buildSurveyItem()
                val existing = viewModel.currentSurvey
                if (existing != null && existing.id.isNotBlank()) {
                    viewModel.updateSurvey(survey.copy(id = existing.id))
                } else {
                    viewModel.createSurvey(survey)
                }
                Snackbar.make(binding.root, "Survey saved", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnPointImage1.setOnClickListener {
            it.animateTapFeedback {
                saveCurrentFormState()
                viewModel.selectedImageType = "imgOne"
                findNavController().navigate(R.id.action_form_to_camera, bundleOf("imageType" to "imgOne"))
            }
        }

        binding.btnPointImage2.setOnClickListener {
            it.animateTapFeedback {
                saveCurrentFormState()
                viewModel.selectedImageType = "imgTwo"
                findNavController().navigate(R.id.action_form_to_camera, bundleOf("imageType" to "imgTwo"))
            }
        }

        binding.btnReviewChanges.setOnClickListener {
            saveCurrentFormState()
            it.animateTapFeedback {
                findNavController().navigate(R.id.action_form_to_review)
            }
        }

        binding.btnUploadLandDoc.setOnClickListener {
            it.animateTapFeedback {
                pickDocument.launch(arrayOf("application/pdf", "image/*"))
            }
        }

        updateImageButtonText()
    }

    private fun handleDocumentSelection(uri: android.net.Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            val name = getFileName(uri)
            viewModel.setPendingDoc(bytes, name)
            binding.tvLandDocStatus.text = name ?: "File selected"
            Log.d("SurveyForm", "Document selected: name='$name' bytes=${bytes?.size}")
        } catch (e: Exception) {
            Log.e("SurveyForm", "Failed to read document", e)
            Snackbar.make(binding.root, "Failed to read file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun saveCurrentFormState() {
        val survey = buildSurveyItem()
        viewModel.saveFormState(survey)
    }

    private fun observeImageStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pendingImages.collect {
                updateImageButtonText()
            }
        }
    }

    private fun updateImageButtonText() {
        val pending = viewModel.pendingImages.value
        val hasPoint1 = pending.any { it.imageType == "imgOne" }
        val hasPoint2 = pending.any { it.imageType == "imgTwo" }

        binding.btnPointImage1.text = if (hasPoint1) "Door Pic (captured)" else "Door Pic"
        binding.btnPointImage2.text = if (hasPoint2) "Front View (captured)" else "Front View"
    }

    private fun buildSurveyItem(): SurveyItem {
        val existing = viewModel.currentSurvey
        val images = viewModel.pendingImages.value
        val img1 = images.find { it.imageType == "imgOne" }
        val img2 = images.find { it.imageType == "imgTwo" }

        val rawStatusText = binding.etStructureStatus.text.toString().trim()
        val validatedStatus = if (selectedStatus.isNotBlank()) selectedStatus else rawStatusText.lowercase()

        val rawNatureText = binding.etConstructionNature.text.toString().trim()
        val validatedNature = if (selectedNature.isNotBlank()) selectedNature else rawNatureText.lowercase()

        Log.d("SurveyForm", "Status raw='$rawStatusText' adapter='$selectedStatus' validated='$validatedStatus'")
        Log.d("SurveyForm", "Nature raw='$rawNatureText' adapter='$selectedNature' validated='$validatedNature'")
        Log.d("SurveyForm", "Doc bytes=${viewModel.pendingDocBytes?.size} name='${viewModel.pendingDocName}'")
        Log.d("SurveyForm", "pendingImages count=${images.size} img1=${img1 != null} img2=${img2 != null}")
        if (img1 != null) Log.d("SurveyForm", "img1 original=${img1.originalBytes.size} stamped=${img1.stampedBytes.size}")
        if (img2 != null) Log.d("SurveyForm", "img2 original=${img2.originalBytes.size} stamped=${img2.stampedBytes.size}")

        return SurveyItem(
            id = existing?.id ?: "",
            srNo = binding.etSrNo.text.toString().toIntOrNull() ?: existing?.srNo ?: 0,
            parcelId = binding.etParcelCode.text.toString().ifBlank { existing?.parcelId ?: "" },
            rd = binding.etRdValue.text.toString(),
            pkg = binding.etPackageNo.text.toString(),
            lat = binding.etLatitude.text.toString().toDoubleOrNull() ?: existing?.lat ?: 0.0,
            lng = binding.etLongitude.text.toString().toDoubleOrNull() ?: existing?.lng ?: 0.0,
            ownerName = binding.etOwnerName.text.toString(),
            fName = binding.etFatherName.text.toString(),
            cnic = binding.etCnic.text.toString(),
            phone = binding.etContact.text.toString(),
            landOwnerDoc = binding.etLandOwnerDoc.text.toString(),
            landOwnerDocBytes = viewModel.pendingDocBytes,
            landOwnerDocName = viewModel.pendingDocName,
            village = binding.etVillage.text.toString(),
            khasraNo = binding.etKhasraNumber.text.toString(),
            electricityConnectionName = binding.etElectricityConnection.text.toString(),
            landArea = binding.etLandArea.text.toString(),
            status = validatedStatus,
            structuralName = binding.etStructureName.text.toString(),
            length = binding.etLengthFt.text.toString(),
            width = binding.etWidthFt.text.toString(),
            area = binding.etAreaSqft.text.toString(),
            natureOfConstruction = validatedNature,
            imgOne = existing?.imgOne ?: "",
            imgTwo = existing?.imgTwo ?: "",
            image1Bytes = img1?.stampedBytes ?: img1?.originalBytes ?: existing?.image1Bytes,
            image2Bytes = img2?.stampedBytes ?: img2?.originalBytes ?: existing?.image2Bytes
        )
    }

    private fun animateSectionsIn() {
        val reduced = view?.isReducedMotionEnabled() ?: return
        if (reduced) return

        val sectionHeaders = mutableListOf<View>()
        val root = binding.root
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.widget.ScrollView) {
                val linearLayout = child.getChildAt(0) as? ViewGroup ?: continue
                for (j in 0 until linearLayout.childCount) {
                    val v = linearLayout.getChildAt(j)
                    if (v is com.google.android.material.textview.MaterialTextView) {
                        val text = v.text?.toString() ?: ""
                        if (text in listOf("Parcel Information", "Coordinates", "Ownership", "Location", "Structure")) {
                            sectionHeaders.add(v)
                        }
                    }
                }
            }
        }

        sectionHeaders.forEachIndexed { index, header ->
            header.alpha = 0f
            header.translationY = 12f
            header.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MotionConstants.DURATION_STANDARD)
                .setStartDelay(index * 80L)
                .setInterpolator(MotionConstants.EASING_ENTRANCE)
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationHelper.stopUpdates()
        _binding = null
    }
}

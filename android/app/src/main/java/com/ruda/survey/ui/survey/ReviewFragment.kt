package com.ruda.survey.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentReviewBinding
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.MotionConstants
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.isReducedMotionEnabled
import com.ruda.survey.utils.staggerFadeIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewFragment : Fragment() {
    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        viewModel.resetUpdateState()
        viewModel.resetCreateState()

        val survey = viewModel.currentSurvey
        if (survey == null) {
            findNavController().popBackStack()
            return
        }

        binding.tvBaseRevision.text = if (survey.id.isNotBlank()) "Edit: SR ${survey.srNo}" else "New: SR ${survey.srNo}"

        displayFullSurvey(survey)

        animateEntrance()

        binding.btnSubmit.setOnClickListener {
            val current = viewModel.currentSurvey
            if (current == null) {
                Snackbar.make(requireView(), "No survey data", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // A survey is new ONLY if it has no server-side ID
            val isNewSurvey = current.id.isBlank() || current.id.startsWith("temp_")
            
            android.util.Log.d("ReviewFragment", "Submit: id='${current.id}' isNew=$isNewSurvey img1=${current.image1Bytes?.size} img2=${current.image2Bytes?.size} doc=${current.landOwnerDocBytes?.size}")
            
            it.animateTapFeedback {
                if (isNewSurvey) {
                    viewModel.createSurvey(current.copy(id = ""))
                } else {
                    viewModel.updateSurvey(current)
                }
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createState.collect { state ->
                handleState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateState.collect { state ->
                handleState(state)
            }
        }
    }

    private fun displayFullSurvey(survey: com.ruda.survey.domain.model.SurveyItem) {
        binding.layoutDetails.removeAllViews()

        val parcelFields = mutableListOf<Pair<String, String>>()
        parcelFields.add(getString(R.string.field_sr_no) to survey.srNo.toString())
        if (survey.parcelId.isNotBlank()) parcelFields.add(getString(R.string.field_parcel_id) to survey.parcelId)
        if (survey.rd.isNotBlank()) parcelFields.add(getString(R.string.field_rd) to survey.rd)
        if (survey.pkg.isNotBlank()) parcelFields.add(getString(R.string.label_package_no) to survey.pkg)

        val ownershipFields = mutableListOf<Pair<String, String>>()
        ownershipFields.add(getString(R.string.label_owner_name) to survey.ownerName)
        ownershipFields.add(getString(R.string.label_father_name) to survey.fName)
        if (survey.cnic.isNotBlank()) ownershipFields.add(getString(R.string.field_cnic) to survey.cnic)
        if (survey.phone.isNotBlank()) ownershipFields.add(getString(R.string.field_contact_number) to survey.phone)
        if (survey.landOwnerDoc.isNotBlank() || survey.landOwnerDocBytes != null) {
            val status = if (survey.landOwnerDocBytes != null) "New Attachment (${survey.landOwnerDocName})" else "Attached (Server)"
            ownershipFields.add(getString(R.string.label_land_owner_doc) to status)
        }

        val locationFields = mutableListOf<Pair<String, String>>()
        locationFields.add(getString(R.string.label_village) to survey.village)
        if (survey.khasraNo.isNotBlank()) locationFields.add(getString(R.string.field_khasra_no) to survey.khasraNo)
        if (survey.electricityConnectionName.isNotBlank()) locationFields.add(getString(R.string.label_electricity_connection) to survey.electricityConnectionName)
        if (survey.landArea.isNotBlank()) locationFields.add(getString(R.string.label_land_area) to survey.landArea)
        if (survey.lat != 0.0 || survey.lng != 0.0) {
            locationFields.add(getString(R.string.field_latitude) to survey.lat.toString())
            locationFields.add(getString(R.string.field_longitude) to survey.lng.toString())
        }

        val structureFields = mutableListOf<Pair<String, String>>()
        structureFields.add(getString(R.string.label_structure_status) to survey.status.replace("_", " ").uppercase())
        structureFields.add(getString(R.string.field_structure) to survey.structuralName)
        structureFields.add(getString(R.string.label_construction_nature) to survey.natureOfConstruction)
        if (survey.length.isNotBlank()) structureFields.add(getString(R.string.label_length_ft) to survey.length)
        if (survey.width.isNotBlank()) structureFields.add(getString(R.string.label_width_ft) to survey.width)
        if (survey.area.isNotBlank()) structureFields.add(getString(R.string.label_area_sqft) to survey.area)

        val sections = listOf(
            Triple(R.drawable.ic_package, R.string.section_parcel_details, parcelFields),
            Triple(R.drawable.ic_person, R.string.section_ownership_details, ownershipFields),
            Triple(R.drawable.ic_location, R.string.section_location_details, locationFields),
            Triple(R.drawable.ic_home_24, R.string.section_structure_details, structureFields)
        )
        var isFirstSection = true
        for ((iconRes, titleRes, fields) in sections) {
            if (fields.isEmpty()) continue
            if (!isFirstSection) addSectionDivider()
            addSectionHeader(iconRes, titleRes)
            for ((label, value) in fields) addDetailRow(label, value)
            isFirstSection = false
        }

        val pendingImages = viewModel.pendingImages.value
        val img1 = pendingImages.find { it.imageType == "imgOne" }
        val img2 = pendingImages.find { it.imageType == "imgTwo" }

        val hasImg1 = img1 != null || survey.image1Bytes != null
        val hasImg2 = img2 != null || survey.image2Bytes != null

        if (hasImg1 || hasImg2) {
            binding.cardImages.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val bmp1 = img1?.stampedBytes?.let { bytes ->
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } ?: survey.image1Bytes?.let { bytes ->
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                val bmp2 = img2?.stampedBytes?.let { bytes ->
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } ?: survey.image2Bytes?.let { bytes ->
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                withContext(Dispatchers.Main) {
                    bmp1?.let { binding.ivReviewImg1.setImageBitmap(it) }
                    bmp2?.let { binding.ivReviewImg2.setImageBitmap(it) }
                }
            }
        }

        val imagesText = buildString {
            if (hasImg1) appendLine("Door Pic: captured")
            if (hasImg2) appendLine("Front View: captured")
            if (survey.imgOne.isNotBlank()) appendLine("Server Img1: ${survey.imgOne}")
            if (survey.imgTwo.isNotBlank()) appendLine("Server Img2: ${survey.imgTwo}")
        }

        if (imagesText.isNotBlank()) {
            binding.tvPendingImages.text = imagesText.trim()
            binding.tvPendingImages.visibility = View.VISIBLE
        } else {
            binding.tvPendingImages.text = "No images captured"
            binding.tvPendingImages.visibility = View.VISIBLE
        }
    }

    private fun addSectionHeader(iconRes: Int, titleRes: Int) {
        val header = layoutInflater.inflate(R.layout.item_change_section_header, binding.layoutDetails, false)
        header.findViewById<ImageView>(R.id.ivSectionIcon).setImageResource(iconRes)
        header.findViewById<MaterialTextView>(R.id.tvSectionTitle).text = getString(titleRes)
        binding.layoutDetails.addView(header)
    }

    private fun addDetailRow(label: String, value: String) {
        val row = layoutInflater.inflate(R.layout.item_change_detail_row, binding.layoutDetails, false)
        row.findViewById<MaterialTextView>(R.id.tvFieldLabel).text = label
        row.findViewById<MaterialTextView>(R.id.tvFieldValue).text = value
        binding.layoutDetails.addView(row)
    }

    private fun addSectionDivider() {
        binding.layoutDetails.addView(
            layoutInflater.inflate(R.layout.item_change_divider, binding.layoutDetails, false)
        )
    }

    private fun handleState(state: UiState<*>) {
        if (!isAdded) return
        when (state) {
            is UiState.Loading -> {
                binding.btnSubmit.isEnabled = false
                binding.btnSubmit.text = ""
                binding.submitProgressBar.visibility = View.VISIBLE
            }
            is UiState.Success -> {
                binding.submitProgressBar.visibility = View.GONE
                binding.btnSubmit.text = getString(R.string.btn_confirm_submit)
                binding.btnSubmit.isEnabled = true
                val ctx = requireContext().applicationContext
                val tm = RepositoryFactory.getTokenManager(ctx)
                tm.incrementNewSurveyCount()
                val surveyData = state.data as? com.ruda.survey.domain.model.SurveyItem
                if (surveyData != null && surveyData.id.isNotBlank()) {
                    tm.addUserSurveyId(surveyData.id)
                }
                viewModel.loadAllSurveys()
                animateCheckmark {
                    if (isAdded) {
                        findNavController().navigate(R.id.action_review_to_sheet)
                    }
                }
            }
            is UiState.Error -> {
                binding.submitProgressBar.visibility = View.GONE
                binding.btnSubmit.text = getString(R.string.btn_confirm_submit)
                binding.btnSubmit.isEnabled = true
                val message = if (state.message?.contains("Unable to resolve host") == true ||
                    state.message?.contains("timeout") == true ||
                    state.message?.contains("ENETUNREACH") == true) {
                    "No internet connection. Survey saved locally and will sync when online."
                } else {
                    state.message ?: "Submission failed"
                }
                Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
            }
            is UiState.Empty -> { }
        }
    }

    private fun animateEntrance() {
        val cards = listOf(
            binding.layoutDetails.parent?.parent as? View
        ).filterNotNull()
        cards.staggerFadeIn()
    }

    private fun animateCheckmark(onComplete: () -> Unit) {
        val reduced = view?.isReducedMotionEnabled() == true
        if (reduced) {
            onComplete()
            return
        }

        binding.btnSubmit.animate()
            .scaleX(1.05f).scaleY(1.05f)
            .setDuration(200)
            .setInterpolator(MotionConstants.EASING_ENTRANCE)
            .withEndAction {
                binding.btnSubmit.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(MotionConstants.EASING_EXIT)
                    .withEndAction { onComplete() }
                    .start()
            }
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.ruda.survey.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
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

        binding.tvParcelCode.text = survey.village.ifBlank { "New Survey" }
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
        val text = buildString {
            appendLine("SR No: ${survey.srNo}")
            if (survey.parcelId.isNotBlank()) appendLine("Parcel ID: ${survey.parcelId}")
            appendLine("Village: ${survey.village}")
            appendLine("Owner: ${survey.ownerName}")
            appendLine("Father: ${survey.fName}")
            if (survey.cnic.isNotBlank()) appendLine("CNIC: ${survey.cnic}")
            if (survey.phone.isNotBlank()) appendLine("Phone: ${survey.phone}")
            if (survey.landOwnerDoc.isNotBlank() || survey.landOwnerDocBytes != null) {
                val status = if (survey.landOwnerDocBytes != null) "New Attachment (${survey.landOwnerDocName})" else "Attached (Server)"
                appendLine("Land Owner Doc: $status")
            }
            appendLine("")
            appendLine("Location:")
            if (survey.khasraNo.isNotBlank()) appendLine("  Khasra No: ${survey.khasraNo}")
            if (survey.electricityConnectionName.isNotBlank()) appendLine("  Electricity: ${survey.electricityConnectionName}")
            if (survey.landArea.isNotBlank()) appendLine("  Land Area: ${survey.landArea}")
            if (survey.lat != 0.0 || survey.lng != 0.0) appendLine("  GPS: ${survey.lat}, ${survey.lng}")
            appendLine("")
            appendLine("Structure:")
            appendLine("  Name: ${survey.structuralName}")
            appendLine("  Status: ${survey.status}")
            appendLine("  Construction: ${survey.natureOfConstruction}")
            if (survey.rd.isNotBlank()) appendLine("  RD: ${survey.rd}")
            if (survey.pkg.isNotBlank()) appendLine("  Package: ${survey.pkg}")
            if (survey.length.isNotBlank()) appendLine("  Length: ${survey.length}")
            if (survey.width.isNotBlank()) appendLine("  Width: ${survey.width}")
            if (survey.area.isNotBlank()) appendLine("  Area: ${survey.area}")
        }
        binding.tvParcelCode.text = text

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
                Snackbar.make(requireView(), state.message, Snackbar.LENGTH_LONG).show()
            }
            is UiState.Empty -> { }
        }
    }

    private fun animateEntrance() {
        val cards = listOf(
            binding.tvParcelCode.parent?.parent as? View
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

package com.ruda.survey.ui.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentSurveyFormBinding
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.MotionConstants
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.isReducedMotionEnabled
import kotlinx.coroutines.launch

class SurveyFormFragment : Fragment() {
    private var _binding: FragmentSurveyFormBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveyFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository, appContext = requireContext().applicationContext)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        viewModel.loadOriginal()
        viewModel.loadCurrent()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.originalState.collect { state ->
                if (state is UiState.Success) {
                    val fields = state.data.fields
                    populateFields(fields)
                    binding.tvParcelCode.text = state.data.parcelCode
                    viewModel.startDraft(fields, state.data.revisionNo)
                    animateSectionsIn()
                }
            }
        }

        setupClickListeners()
        observeImageStatus()
    }

    private fun populateFields(fields: Map<String, Any?>) {
        binding.etParcelCode.setText(fields["parcel_code"]?.toString() ?: "")
        binding.etRdValue.setText(fields["rd_value"]?.toString() ?: "")
        binding.etPackageNo.setText(fields["package_no"]?.toString() ?: "")
        binding.etLatitude.setText(fields["latitude"]?.toString() ?: "")
        binding.etLongitude.setText(fields["longitude"]?.toString() ?: "")
        binding.etOwnerName.setText(fields["owner_name"]?.toString() ?: "")
        binding.etFatherName.setText(fields["father_name"]?.toString() ?: "")
        binding.etCnic.setText(fields["cnic_no"]?.toString() ?: "")
        binding.etContact.setText(fields["contact_number"]?.toString() ?: "")
        binding.etLandOwnerDoc.setText(fields["land_owner_doc"]?.toString() ?: "")
        binding.etVillage.setText(fields["village"]?.toString() ?: "")
        binding.etKhasraNumber.setText(fields["khasra_number"]?.toString() ?: "")
        binding.etElectricityConnection.setText(fields["electricity_connection_name"]?.toString() ?: "")
        binding.etLandArea.setText(fields["land_area"]?.toString() ?: "")
        binding.etStructureStatus.setText(fields["structure_status"]?.toString() ?: "")
        binding.etStructureName.setText(fields["structure_name"]?.toString() ?: "")
        binding.etLengthFt.setText(fields["length_ft"]?.toString() ?: "")
        binding.etWidthFt.setText(fields["width_ft"]?.toString() ?: "")
        binding.etAreaSqft.setText(fields["area_sqft"]?.toString() ?: "")
        binding.etConstructionNature.setText(fields["construction_nature"]?.toString() ?: "")
    }

    private fun setupClickListeners() {
        binding.btnSaveDraft.setOnClickListener {
            it.animateTapFeedback {
                saveDraftFields()
                Snackbar.make(binding.root, "Draft saved", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnPointImage1.setOnClickListener {
            it.animateTapFeedback {
                saveDraftFields()
                viewModel.pendingImageType = "POINT_1"
                findNavController().navigate(R.id.action_form_to_camera)
            }
        }

        binding.btnPointImage2.setOnClickListener {
            it.animateTapFeedback {
                saveDraftFields()
                viewModel.pendingImageType = "POINT_2"
                findNavController().navigate(R.id.action_form_to_camera)
            }
        }

        binding.btnReviewChanges.setOnClickListener {
            it.animateTapFeedback {
                saveDraftFields()
                findNavController().navigate(R.id.action_form_to_review)
            }
        }

        updateImageButtonText()
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
        val hasPoint1 = pending.any { it.imageType == "POINT_1" }
        val hasPoint2 = pending.any { it.imageType == "POINT_2" }

        binding.btnPointImage1.text = if (hasPoint1) "Door Pic (captured)" else "Door Pic"
        binding.btnPointImage2.text = if (hasPoint2) "Front View (captured)" else "Front View"
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
                    val view = linearLayout.getChildAt(j)
                    if (view is com.google.android.material.textview.MaterialTextView) {
                        val text = view.text?.toString() ?: ""
                        if (text in listOf("Parcel Information", "Coordinates", "Ownership", "Location", "Structure")) {
                            sectionHeaders.add(view)
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

    private fun saveDraftFields() {
        viewModel.updateDraftField("parcel_code", binding.etParcelCode.text.toString().ifBlank { null })
        viewModel.updateDraftField("rd_value", binding.etRdValue.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("package_no", binding.etPackageNo.text.toString().ifBlank { null })
        viewModel.updateDraftField("latitude", binding.etLatitude.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("longitude", binding.etLongitude.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("owner_name", binding.etOwnerName.text.toString())
        viewModel.updateDraftField("father_name", binding.etFatherName.text.toString().ifBlank { null })
        viewModel.updateDraftField("cnic_no", binding.etCnic.text.toString().ifBlank { null })
        viewModel.updateDraftField("contact_number", binding.etContact.text.toString().ifBlank { null })
        viewModel.updateDraftField("land_owner_doc", binding.etLandOwnerDoc.text.toString().ifBlank { null })
        viewModel.updateDraftField("village", binding.etVillage.text.toString().ifBlank { null })
        viewModel.updateDraftField("khasra_number", binding.etKhasraNumber.text.toString().ifBlank { null })
        viewModel.updateDraftField("electricity_connection_name", binding.etElectricityConnection.text.toString().ifBlank { null })
        viewModel.updateDraftField("land_area", binding.etLandArea.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("structure_status", binding.etStructureStatus.text.toString().ifBlank { null })
        viewModel.updateDraftField("structure_name", binding.etStructureName.text.toString().ifBlank { null })
        viewModel.updateDraftField("length_ft", binding.etLengthFt.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("width_ft", binding.etWidthFt.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("area_sqft", binding.etAreaSqft.text.toString().toDoubleOrNull())
        viewModel.updateDraftField("construction_nature", binding.etConstructionNature.text.toString().ifBlank { null })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

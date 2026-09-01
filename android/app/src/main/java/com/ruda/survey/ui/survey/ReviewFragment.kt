package com.ruda.survey.ui.survey

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import kotlinx.coroutines.launch

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
        val factory = SurveyViewModelFactory(repository, appContext = requireContext().applicationContext)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        viewModel.resetSubmitState()

        val draft = viewModel.draftState.value
        if (draft == null) {
            findNavController().popBackStack()
            return
        }

        binding.tvParcelCode.text = draft.parcelCode
        binding.tvBaseRevision.text = "Base: Revision ${draft.baseRevisionNo}"

        val pendingImages = viewModel.pendingImages.value
        if (pendingImages.isNotEmpty()) {
            val names = pendingImages.joinToString(", ") { it.imageType }
            binding.tvPendingImages.text = "${pendingImages.size} photo(s) will be uploaded: $names"
            binding.tvPendingImages.visibility = View.VISIBLE
        } else {
            binding.tvPendingImages.visibility = View.GONE
        }

        binding.etChangeReason.setText(draft.changeReason)

        // Stagger card entrance
        animateEntrance()

        binding.btnSubmit.setOnClickListener {
            it.animateTapFeedback {
                val reason = binding.etChangeReason.text.toString()
                viewModel.setChangeReason(reason)
                viewModel.submitRevision()
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.submitState.collect { state ->
                if (!isAdded) return@collect
                when (state) {
                    is UiState.Loading -> {
                        binding.btnSubmit.isEnabled = false
                        binding.btnSubmit.text = ""
                        binding.btnSubmit.setIconResource(0)
                        binding.submitProgressBar.visibility = View.VISIBLE
                    }
                    is UiState.Success -> {
                        binding.submitProgressBar.visibility = View.GONE
                        binding.btnSubmit.text = getString(R.string.btn_confirm_submit)
                        binding.btnSubmit.setIconResource(R.drawable.ic_check_circle)
                        binding.btnSubmit.isEnabled = true

                        val result = state.data
                        if (result.replayed) {
                            Snackbar.make(view, "Revision already submitted (replayed)", Snackbar.LENGTH_SHORT).show()
                        } else {
                            viewModel.flushPendingImages()
                            animateCheckmark {
                                if (isAdded) {
                                    findNavController().navigate(R.id.action_review_to_sheet)
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        binding.submitProgressBar.visibility = View.GONE
                        binding.btnSubmit.text = getString(R.string.btn_confirm_submit)
                        binding.btnSubmit.setIconResource(R.drawable.ic_check_circle)
                        binding.btnSubmit.isEnabled = true
                        Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                    }
                    is UiState.Empty -> { }
                }
            }
        }
    }

    private fun animateEntrance() {
        val cards = listOf(
            binding.tvParcelCode.parent?.parent as? View,
            binding.etChangeReason.parent?.parent?.parent as? View
        ).filterNotNull()
        cards.staggerFadeIn()
    }

    private fun animateCheckmark(onComplete: () -> Unit) {
        val reduced = view?.isReducedMotionEnabled() == true
        if (reduced) {
            onComplete()
            return
        }

        // Circle outline draws, then check draws
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

package com.ruda.survey.ui.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentLoginBinding
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.MotionConstants
import com.ruda.survey.utils.shake
import com.ruda.survey.utils.isReducedMotionEnabled
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        viewModel = AuthViewModel(repository)

        // Screen entrance: stagger top-to-bottom
        animateEntrance()

        binding.btnLogin.setOnClickListener {
            // Tap feedback on button
            val reduced = view.isReducedMotionEnabled()
            if (!reduced) {
                it.animate()
                    .scaleX(0.95f).scaleY(0.95f)
                    .setDuration(75)
                    .withEndAction {
                        it.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(75)
                            .withEndAction { performLogin() }
                            .start()
                    }
                    .start()
            } else {
                performLogin()
            }
        }

        // Hint chips: fade-substitute text on tap
        binding.chipSurveyor.setOnClickListener {
            binding.etUsername.setText("surveyor1")
            binding.etPassword.setText("")
            binding.etPassword.requestFocus()
        }

        binding.chipSupervisor.setOnClickListener {
            binding.etUsername.setText("supervisor1")
            binding.etPassword.setText("")
            binding.etPassword.requestFocus()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        // Cross-fade button label to inline progress
                        binding.btnLogin.isEnabled = false
                        binding.btnLogin.text = ""
                        binding.btnLogin.setIconResource(0)
                        binding.buttonProgressBar.visibility = View.VISIBLE
                    }
                    is UiState.Success -> {
                        binding.buttonProgressBar.visibility = View.GONE
                        binding.btnLogin.text = getString(R.string.btn_login)
                        if (isAdded) findNavController().navigate(R.id.action_login_to_dashboard)
                    }
                    is UiState.Error -> {
                        binding.btnLogin.isEnabled = true
                        binding.buttonProgressBar.visibility = View.GONE
                        binding.btnLogin.text = getString(R.string.btn_login)
                        binding.btnLogin.setIconResource(R.drawable.ic_login)

                        // Shake the fields + fade in error text
                        binding.tilUsername.shake()
                        binding.tilPassword.shake()
                        binding.tvError.text = state.message
                        binding.tvError.alpha = 0f
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    is UiState.Empty -> {
                        binding.btnLogin.isEnabled = true
                        binding.buttonProgressBar.visibility = View.GONE
                        binding.btnLogin.text = getString(R.string.btn_login)
                    }
                }
            }
        }
    }

    private fun performLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        viewModel.login(username, password)
    }

    private fun animateEntrance() {
        val reduced = view?.isReducedMotionEnabled() ?: return
        val views = listOf(
            binding.ivLogo,
            binding.tvTitle,
            binding.tvSubtitle,
            binding.tilUsername,
            binding.tilPassword,
            binding.btnLogin
        )
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = if (reduced) 0f else 24f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(if (reduced) MotionConstants.REDUCED_MOTION_DURATION else 300L)
                .setStartDelay(if (reduced) 0L else (index * 60L))
                .setInterpolator(MotionConstants.EASING_ENTRANCE)
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

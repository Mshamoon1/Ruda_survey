package com.ruda.survey.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ruda.survey.BuildConfig
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentProfileBinding
import com.ruda.survey.utils.animateTapFeedback

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupProfile()
        setupLogout()
    }

    private fun setupProfile() {
        val tokenManager = RepositoryFactory.getTokenManager(requireContext().applicationContext)
        val savedEmail = tokenManager.getUserEmail()

        binding.tvUserName.text = tokenManager.getUserName() ?: "Surveyor"
        binding.tvUserRole.text = "Field Surveyor"
        binding.tvProfileRole.text = "Field Surveyor"
        binding.tvUserEmail.text = savedEmail ?: "No email available"
        binding.tvAppVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    private fun setupLogout() {
        binding.btnProfileLogout.setOnClickListener {
            it.animateTapFeedback {
                showLogoutConfirmation()
            }
        }
    }

    private fun showLogoutConfirmation() {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.btn_logout))
        builder.setMessage(getString(R.string.btn_logout_confirm))
        builder.setPositiveButton(getString(R.string.btn_logout_confirm_action)) { _, _ ->
            RepositoryFactory.getTokenManager(requireContext().applicationContext).resetNewSurveyCount()
            if (isAdded) findNavController().navigate(R.id.action_profile_to_login)
        }
        builder.setNegativeButton(getString(R.string.btn_logout_cancel), null)
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.ruda.survey.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ruda.survey.BuildConfig
import com.ruda.survey.R
import com.ruda.survey.databinding.FragmentDashboardBinding
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.staggerFadeIn

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        animateEntrance()
    }

    private fun setupUI() {
        binding.tvWelcome.text = getString(R.string.dashboard_welcome, "Surveyor")
        binding.tvRole.text = getString(R.string.dashboard_role, "Field Surveyor")

        binding.btnSyncNow.visibility = View.GONE
        binding.cardSyncStatus.visibility = View.GONE

        binding.btnNewSurvey.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) findNavController().navigate(R.id.action_dashboard_to_newSurvey)
            }
        }

        binding.btnLogout.setOnClickListener {
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
            if (isAdded) findNavController().navigate(R.id.action_dashboard_to_login)
        }
        builder.setNegativeButton(getString(R.string.btn_logout_cancel), null)
        builder.show()
    }

    private fun animateEntrance() {
        val cards = listOf(
            binding.tvWelcome.parent as? View,
            binding.btnNewSurvey.parent as? View
        ).filterNotNull()
        cards.staggerFadeIn()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

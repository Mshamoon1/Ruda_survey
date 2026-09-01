package com.ruda.survey.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ruda.survey.BuildConfig
import com.ruda.survey.R
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.remote.ApiClient
import com.ruda.survey.data.remote.SecureTokenManager
import com.ruda.survey.data.sync.ConnectivityObserver
import com.ruda.survey.data.sync.SyncRepository
import com.ruda.survey.databinding.FragmentDashboardBinding
import com.ruda.survey.domain.model.SyncState
import com.ruda.survey.ui.sync.SyncViewModel
import com.ruda.survey.utils.animateCountUp
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.staggerFadeIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var syncViewModel: SyncViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext().applicationContext

        if (BuildConfig.DEMO_MODE) {
            setupDemoUI()
        } else {
            val database = SurveyDatabase.getInstance(context)
            val api = ApiClient.createApi(context)
            val tokenManager = SecureTokenManager(context)

            val syncRepository = SyncRepository(
                api = api,
                dao = database.syncDao(),
                tokenManager = tokenManager
            )

            val connectivityObserver = ConnectivityObserver(context)

            syncViewModel = ViewModelProvider(requireActivity(),
                SyncViewModelFactory(syncRepository, database.syncDao(), connectivityObserver, context)
            )[SyncViewModel::class.java]

            setupUI()
            observeSyncState()
        }
        animateEntrance()
    }

    private fun setupUI() {
        val username = "Surveyor"
        binding.tvWelcome.text = getString(R.string.dashboard_welcome, username)
        binding.tvRole.text = getString(R.string.dashboard_role, "Field Surveyor")

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

        binding.btnSyncNow.setOnClickListener {
            it.animateTapFeedback {
                syncViewModel.triggerSync()
            }
        }
    }

    private fun setupDemoUI() {
        binding.tvWelcome.text = getString(R.string.dashboard_welcome, "DEMO001")
        binding.tvRole.text = "Demo Mode \u2014 Local Data"

        binding.btnSyncNow.visibility = View.GONE
        binding.cardSyncStatus.visibility = View.GONE

        val pendingCard = binding.tvPendingCount.parent?.parent?.parent as? View
        pendingCard?.visibility = View.GONE

        val lastSyncCard = binding.tvLastSync.parent?.parent?.parent as? View
        lastSyncCard?.visibility = View.GONE

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
            binding.tvPendingCount.parent?.parent?.parent as? View,
            binding.tvLastSync.parent?.parent?.parent as? View
        ).filterNotNull()
        cards.staggerFadeIn()
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            syncViewModel.syncState.collect { state ->
                updateSyncUI(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            syncViewModel.syncOutcome.collect { outcome ->
                outcome?.let {
                    syncViewModel.clearOutcome()
                }
            }
        }
    }

    private fun updateSyncUI(state: SyncState) {
        binding.cardSyncStatus.visibility = View.VISIBLE

        val statusText = when {
            state.isSyncing -> "Syncing..."
            state.hasConflicts && state.hasPendingItems ->
                "${state.pendingCount} pending \u00B7 ${state.conflictCount} conflicts"
            state.hasConflicts -> "${state.conflictCount} conflicts"
            state.hasPendingItems -> "${state.pendingCount} pending"
            !state.isOnline -> "Offline \u2014 waiting for connection"
            else -> getString(R.string.sync_complete)
        }
        binding.tvSyncStatus.text = statusText

        val targetCount = state.pendingCount
        val currentText = binding.tvPendingCount.text?.toString()?.toIntOrNull() ?: 0
        if (targetCount != currentText) {
            animateCountUp(targetCount) { value ->
                binding.tvPendingCount.text = value.toString()
            }
        }

        state.lastSyncTime?.let { time ->
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            binding.tvLastSync.text = sdf.format(Date(time))
        } ?: run {
            binding.tvLastSync.text = getString(R.string.never)
        }

        binding.btnSyncNow.isEnabled = state.canSync && state.hasPendingItems
        binding.btnSyncNow.text = if (state.isSyncing) "Syncing..." else getString(R.string.btn_sync_now)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

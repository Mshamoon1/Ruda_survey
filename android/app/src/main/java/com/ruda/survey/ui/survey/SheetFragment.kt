package com.ruda.survey.ui.survey

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentSheetBinding
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.MotionConstants
import com.ruda.survey.utils.animateTapFeedback
import com.ruda.survey.utils.isReducedMotionEnabled
import com.ruda.survey.utils.staggerFadeIn
import kotlinx.coroutines.launch
import java.io.File

class SheetFragment : Fragment() {
    private var _binding: FragmentSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel
    private var currentParcelCode: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository, appContext = requireContext().applicationContext)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        if (savedInstanceState != null) {
            currentParcelCode = savedInstanceState.getString("parcel_code", "")
        }

        viewModel.loadSheet()

        binding.toolbar.setNavigationOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        setupClickListeners()
        observeStates()
    }

    private fun setupClickListeners() {
        binding.btnDownloadPdf.setOnClickListener {
            it.animateTapFeedback {
                viewModel.requestDownloadPdf()
            }
        }

        binding.btnSharePdf.setOnClickListener {
            it.animateTapFeedback {
                viewModel.requestSharePdf()
            }
        }

        binding.btnExportExcel.setOnClickListener {
            it.animateTapFeedback {
                viewModel.downloadExcel()
            }
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sheetState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.contentLayout.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.contentLayout.visibility = View.VISIBLE
                        val data = state.data
                        currentParcelCode = data.parcelCode
                        binding.tvParcelCode.text = data.parcelCode
                        binding.tvSource.text = "Source: ${data.source} (Rev ${data.revisionNo})"
                        binding.tvSurveyor.text = data.surveyor ?: "N/A"
                        binding.tvChangedAt.text = data.changedAt ?: "N/A"

                        val khasra = data.fields["khasra_number"]?.toString()
                        val mauza = data.fields["mauza_number"]?.toString()
                        if (!khasra.isNullOrBlank() || !mauza.isNullOrBlank()) {
                            val khasraMauzaText = buildString {
                                if (!khasra.isNullOrBlank()) append("Khasra: $khasra")
                                if (!mauza.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" | ")
                                    append("Mauza: $mauza")
                                }
                            }
                            binding.tvKhasraMauza.text = khasraMauzaText
                            binding.tvKhasraMauza.visibility = View.VISIBLE
                        } else {
                            binding.tvKhasraMauza.visibility = View.GONE
                        }

                        val fieldsText = data.fields.entries.joinToString("\n") { (k, v) ->
                            "$k: ${v ?: "\u2014"}"
                        }
                        binding.tvFields.text = fieldsText

                        val imagesText = data.images.joinToString("\n") {
                            "${it.imageType}: ${it.contentType ?: "unknown"}"
                        }
                        binding.tvImages.text = imagesText.ifBlank { "No images" }

                        // Fade-in content with stagger
                        animateContentIn()
                    }
                    is UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.text = state.message
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.alpha = 0f
                        binding.tvError.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    is UiState.Empty -> { }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pdfState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.pdfProgressLayout.visibility = View.VISIBLE
                        binding.tvPdfStatus.text = getString(R.string.loading)
                        binding.btnDownloadPdf.isEnabled = false
                        binding.btnSharePdf.isEnabled = false
                    }
                    is UiState.Success -> {
                        binding.pdfProgressLayout.visibility = View.GONE
                        binding.btnDownloadPdf.isEnabled = true
                        binding.btnSharePdf.isEnabled = true
                        val pdfBytes = state.data
                        if (viewModel.pendingShareAction) {
                            sharePdfDirectly(pdfBytes)
                        } else {
                            savePdfToDownloads(pdfBytes)
                        }
                        viewModel.resetPdfState()
                    }
                    is UiState.Error -> {
                        binding.pdfProgressLayout.visibility = View.GONE
                        binding.btnDownloadPdf.isEnabled = true
                        binding.btnSharePdf.isEnabled = true
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetPdfState()
                    }
                    is UiState.Empty -> { }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.excelState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.btnExportExcel.isEnabled = false
                    }
                    is UiState.Success -> {
                        binding.btnExportExcel.isEnabled = true
                        val excelBytes = state.data
                        saveExcelFile(excelBytes)
                        viewModel.resetExcelState()
                    }
                    is UiState.Error -> {
                        binding.btnExportExcel.isEnabled = true
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetExcelState()
                    }
                    is UiState.Empty -> { }
                }
            }
        }
    }

    private fun animateContentIn() {
        val reduced = view?.isReducedMotionEnabled() ?: return
        if (reduced) return

        val views = listOf(
            binding.tvParcelCode.parent?.parent as? View,
            binding.btnDownloadPdf.parent?.parent as? View,
            binding.tvFields.parent?.parent as? View,
            binding.tvImages.parent?.parent as? View
        ).filterNotNull()

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 16f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MotionConstants.DURATION_STANDARD)
                .setStartDelay(index * MotionConstants.CARD_STAGGER)
                .setInterpolator(MotionConstants.EASING_ENTRANCE)
                .start()
        }
    }

    private fun savePdfToDownloads(pdfBytes: ByteArray) {
        try {
            val fileName = "survey_$currentParcelCode.pdf"
            val uri = saveToDownloads(fileName, "application/pdf", pdfBytes)
            if (uri != null) {
                Snackbar.make(binding.root, "PDF saved to Downloads", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Failed to save PDF", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to save PDF: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun sharePdfDirectly(pdfBytes: ByteArray) {
        try {
            val fileName = "survey_$currentParcelCode.pdf"
            val pdfsDir = File(requireContext().cacheDir, "pdfs")
            pdfsDir.mkdirs()
            val tempFile = File(pdfsDir, "share_$fileName")
            tempFile.writeBytes(pdfBytes)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                tempFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to share PDF: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveExcelFile(excelBytes: ByteArray) {
        try {
            val fileName = "survey_$currentParcelCode.xlsx"
            val uri = saveToDownloads(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes)

            if (uri != null) {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(openIntent)
                Snackbar.make(binding.root, "Excel saved to Downloads", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Failed to save Excel", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to save Excel: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray): android.net.Uri? {
        val resolver = requireContext().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RUDA Survey")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("parcel_code", currentParcelCode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

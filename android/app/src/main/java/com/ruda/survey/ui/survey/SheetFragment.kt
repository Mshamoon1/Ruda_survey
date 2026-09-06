package com.ruda.survey.ui.survey

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.databinding.FragmentSheetBinding
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.UiState
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.utils.animateTapFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SheetFragment : Fragment() {
    private var _binding: FragmentSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repository: SurveyRepository = RepositoryFactory.create(requireContext().applicationContext)
        val factory = SurveyViewModelFactory(repository)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        val survey = viewModel.currentSurvey
        if (survey == null) {
            findNavController().popBackStack()
            return
        }

        displaySurvey(survey)
        displayImages(survey)
        setupClickListeners(survey)

        binding.toolbar.setNavigationOnClickListener {
            if (isAdded) findNavController().popBackStack(R.id.surveyFormFragment, false)
        }
    }

    private fun displaySurvey(survey: SurveyItem) {
        binding.tvParcelCode.text = survey.village.ifBlank { "N/A" }
        binding.tvSource.text = "SR No: ${survey.srNo}"
        binding.tvSurveyor.text = survey.ownerName.ifBlank { "N/A" }
        binding.tvChangedAt.text = survey.structuralName.ifBlank { "N/A" }

        val khasra = survey.khasraNo
        if (khasra.isNotBlank()) {
            binding.tvKhasraMauza.text = "Khasra: $khasra"
            binding.tvKhasraMauza.visibility = View.VISIBLE
        } else {
            binding.tvKhasraMauza.visibility = View.GONE
        }

        val fieldsText = buildString {
            appendLine("Parcel ID: ${survey.parcelId.ifBlank { "N/A" }}")
            appendLine("Owner: ${survey.ownerName.ifBlank { "N/A" }}")
            appendLine("Father: ${survey.fName.ifBlank { "N/A" }}")
            appendLine("CNIC: ${survey.cnic.ifBlank { "N/A" }}")
            appendLine("Phone: ${survey.phone.ifBlank { "N/A" }}")
            appendLine("Village: ${survey.village.ifBlank { "N/A" }}")
            appendLine("Khasra: ${survey.khasraNo.ifBlank { "N/A" }}")
            appendLine("Land Owner Doc: ${survey.landOwnerDoc.ifBlank { "N/A" }}")
            appendLine("Electricity: ${survey.electricityConnectionName.ifBlank { "N/A" }}")
            appendLine("Land Area: ${survey.landArea.ifBlank { "N/A" }}")
            appendLine("RD: ${survey.rd.ifBlank { "N/A" }}")
            appendLine("Package: ${survey.pkg.ifBlank { "N/A" }}")
            appendLine("")
            appendLine("Structure:")
            appendLine("  Name: ${survey.structuralName.ifBlank { "N/A" }}")
            appendLine("  Status: ${survey.status.ifBlank { "N/A" }}")
            appendLine("  Construction: ${survey.natureOfConstruction.ifBlank { "N/A" }}")
            appendLine("  Length: ${survey.length.ifBlank { "N/A" }}")
            appendLine("  Width: ${survey.width.ifBlank { "N/A" }}")
            appendLine("  Area: ${survey.area.ifBlank { "N/A" }}")
            appendLine("")
            if (survey.lat != 0.0 || survey.lng != 0.0) {
                appendLine("GPS: ${survey.lat}, ${survey.lng}")
            } else {
                appendLine("GPS: Not available")
            }
        }
        binding.tvFields.text = fieldsText

        binding.contentLayout.visibility = View.VISIBLE
    }

    private fun displayImages(survey: SurveyItem) {
        val pendingImages = viewModel.pendingImages.value
        val imageViews = mutableListOf<View>()

        val img1Bytes = pendingImages.find { it.imageType == "imgOne" }?.stampedBytes
            ?: pendingImages.find { it.imageType == "imgOne" }?.originalBytes
            ?: survey.image1Bytes

        val img2Bytes = pendingImages.find { it.imageType == "imgTwo" }?.stampedBytes
            ?: pendingImages.find { it.imageType == "imgTwo" }?.originalBytes
            ?: survey.image2Bytes

        if (img1Bytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(img1Bytes, 0, img1Bytes.size)
            if (bitmap != null) {
                binding.ivImage1.setImageBitmap(bitmap)
                binding.ivImage1.visibility = View.VISIBLE
                binding.tvImage1Label.visibility = View.VISIBLE
                binding.tvImage1Label.text = "Door Pic"
            }
        } else if (survey.imgOne.isNotBlank()) {
            binding.tvImage1Label.text = "Door Pic (server): ${survey.imgOne}"
            binding.tvImage1Label.visibility = View.VISIBLE
        }

        if (img2Bytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(img2Bytes, 0, img2Bytes.size)
            if (bitmap != null) {
                binding.ivImage2.setImageBitmap(bitmap)
                binding.ivImage2.visibility = View.VISIBLE
                binding.tvImage2Label.visibility = View.VISIBLE
                binding.tvImage2Label.text = "Front View"
            }
        } else if (survey.imgTwo.isNotBlank()) {
            binding.tvImage2Label.text = "Front View (server): ${survey.imgTwo}"
            binding.tvImage2Label.visibility = View.VISIBLE
        }

        val hasAny = img1Bytes != null || img2Bytes != null || survey.imgOne.isNotBlank() || survey.imgTwo.isNotBlank()
        if (!hasAny) {
            binding.tvImages.text = "No images available"
        }
    }

    private fun setupClickListeners(survey: SurveyItem) {
        binding.btnDownloadPdf.setOnClickListener {
            it.animateTapFeedback {
                generateAndSavePdf(survey)
            }
        }

        binding.btnSharePdf.setOnClickListener {
            it.animateTapFeedback {
                sharePdf(survey)
            }
        }

        binding.btnExportExcel.setOnClickListener {
            it.animateTapFeedback {
                generateAndSaveExcel(survey)
            }
        }
    }

    private fun generateAndSavePdf(survey: SurveyItem) {
        binding.pdfProgressLayout.visibility = View.VISIBLE
        binding.tvPdfStatus.text = "Generating PDF..."
        binding.btnDownloadPdf.isEnabled = false
        binding.btnSharePdf.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfBytes = withContext(Dispatchers.IO) {
                    com.ruda.survey.utils.PdfGenerator.generate(survey)
                }
                val fileName = "survey_${survey.srNo}.pdf"
                val uri = saveToDownloads(fileName, "application/pdf", pdfBytes)
                binding.pdfProgressLayout.visibility = View.GONE
                binding.btnDownloadPdf.isEnabled = true
                binding.btnSharePdf.isEnabled = true
                if (uri != null) {
                    Snackbar.make(binding.root, "PDF saved to Downloads", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Failed to save PDF", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.pdfProgressLayout.visibility = View.GONE
                binding.btnDownloadPdf.isEnabled = true
                binding.btnSharePdf.isEnabled = true
                Snackbar.make(binding.root, "PDF error: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun sharePdf(survey: SurveyItem) {
        binding.pdfProgressLayout.visibility = View.VISIBLE
        binding.tvPdfStatus.text = "Generating PDF..."
        binding.btnSharePdf.isEnabled = false
        binding.btnDownloadPdf.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfBytes = withContext(Dispatchers.IO) {
                    com.ruda.survey.utils.PdfGenerator.generate(survey)
                }
                val pdfsDir = File(requireContext().cacheDir, "pdfs")
                pdfsDir.mkdirs()
                val tempFile = File(pdfsDir, "survey_${survey.srNo}.pdf")
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
                binding.pdfProgressLayout.visibility = View.GONE
                binding.btnSharePdf.isEnabled = true
                binding.btnDownloadPdf.isEnabled = true
            } catch (e: Exception) {
                binding.pdfProgressLayout.visibility = View.GONE
                binding.btnSharePdf.isEnabled = true
                binding.btnDownloadPdf.isEnabled = true
                Snackbar.make(binding.root, "PDF error: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun generateAndSaveExcel(survey: SurveyItem) {
        binding.btnExportExcel.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val excelBytes = withContext(Dispatchers.IO) {
                    com.ruda.survey.utils.ExcelExporter.generate(survey)
                }
                val fileName = "survey_${survey.srNo}.xlsx"
                val uri = saveToDownloads(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes)
                binding.btnExportExcel.isEnabled = true
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
                binding.btnExportExcel.isEnabled = true
                Snackbar.make(binding.root, "Excel error: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

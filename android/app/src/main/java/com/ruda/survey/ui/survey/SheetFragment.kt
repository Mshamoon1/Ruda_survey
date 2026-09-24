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
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SheetFragment : Fragment() {
    private var _binding: FragmentSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SurveyViewModel
    private var submittedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        submittedAt = savedInstanceState?.getLong(KEY_SUBMITTED_AT) ?: System.currentTimeMillis()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_SUBMITTED_AT, submittedAt)
    }

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
        binding.tvSummaryVillage.text = displayValue(survey.village)
        binding.tvSummarySrNo.text = "${getString(R.string.field_sr_no)} ${survey.srNo}"
        binding.tvSummaryOwner.text = displayValue(survey.ownerName)
        binding.tvSummaryStructure.text = displayValue(survey.structuralName)

        binding.tvSummaryDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .format(Date(submittedAt))
        binding.tvSummaryTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault())
            .format(Date(submittedAt))

        binding.layoutDetails.removeAllViews()

        val generalFields = listOf(
            getString(R.string.field_parcel_id) to displayValue(survey.parcelId),
            getString(R.string.field_sr_no) to displayValue(survey.srNo.toString()),
            getString(R.string.label_village) to displayValue(survey.village),
            getString(R.string.field_rd) to displayValue(survey.rd),
            getString(R.string.label_package_no) to displayValue(survey.pkg)
        )

        val gpsAvailable = survey.lat != 0.0 || survey.lng != 0.0
        val locationFields = listOf(
            getString(R.string.field_khasra_no) to displayValue(survey.khasraNo),
            getString(R.string.field_latitude) to displayValue(
                if (gpsAvailable) survey.lat.toString() else null
            ),
            getString(R.string.field_longitude) to displayValue(
                if (gpsAvailable) survey.lng.toString() else null
            )
        )

        val landOwnerDoc = survey.landOwnerDoc.ifBlank {
            survey.landOwnerDocName.orEmpty()
        }
        val ownerFields = listOf(
            getString(R.string.label_owner_name) to displayValue(survey.ownerName),
            getString(R.string.label_father_name) to displayValue(survey.fName),
            getString(R.string.field_cnic) to displayValue(survey.cnic),
            getString(R.string.field_contact_number) to displayValue(survey.phone),
            getString(R.string.label_land_owner_doc) to displayValue(landOwnerDoc)
        )

        val landFields = listOf(
            getString(R.string.label_electricity_connection) to displayValue(
                survey.electricityConnectionName
            ),
            getString(R.string.label_land_area) to displayValue(survey.landArea)
        )

        val structureStatus = survey.status.replace("_", " ").uppercase()
        val structureFields = listOf(
            getString(R.string.label_structure_status) to displayValue(structureStatus),
            getString(R.string.label_structure_name) to displayValue(survey.structuralName),
            getString(R.string.label_construction_nature) to displayValue(
                survey.natureOfConstruction
            ),
            getString(R.string.label_length_ft) to displayValue(survey.length),
            getString(R.string.label_width_ft) to displayValue(survey.width),
            getString(R.string.label_area_sqft) to displayValue(survey.area)
        )

        val sections = listOf(
            Triple(R.drawable.ic_document, R.string.section_01_general, generalFields),
            Triple(R.drawable.ic_location, R.string.section_02_location, locationFields),
            Triple(R.drawable.ic_person, R.string.section_03_owner, ownerFields),
            Triple(R.drawable.ic_landscape, R.string.section_04_land, landFields),
            Triple(R.drawable.ic_home_24, R.string.section_05_structure_details, structureFields)
        )

        for ((iconRes, titleRes, fields) in sections) {
            if (fields.isEmpty()) continue
            addSectionHeader(iconRes, titleRes)
            for ((label, value) in fields) addDetailRow(label, value)
        }

        binding.contentLayout.visibility = View.VISIBLE
    }

    private fun displayValue(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: getString(R.string.value_placeholder)

    private fun addSectionHeader(iconRes: Int, titleRes: Int) {
        val header = layoutInflater.inflate(
            R.layout.item_sheet_section_header, binding.layoutDetails, false
        )
        header.findViewById<ImageView>(R.id.ivSectionIcon).setImageResource(iconRes)
        header.findViewById<MaterialTextView>(R.id.tvSectionTitle).text = getString(titleRes)
        binding.layoutDetails.addView(header)
    }

    private fun addDetailRow(label: String, value: String) {
        val row = layoutInflater.inflate(
            R.layout.item_sheet_detail_row, binding.layoutDetails, false
        )
        row.findViewById<MaterialTextView>(R.id.tvFieldLabel).text = label
        row.findViewById<MaterialTextView>(R.id.tvFieldValue).text = value
        binding.layoutDetails.addView(row)
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
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(img1Bytes, 0, img1Bytes.size)
                }
                if (bitmap != null) {
                    binding.ivImage1.setImageBitmap(bitmap)
                    binding.ivImage1.visibility = View.VISIBLE
                    binding.tvImage1Label.visibility = View.VISIBLE
                    binding.tvImage1Label.text = "Door Pic"
                }
            }
        } else if (survey.imgOne.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) { downloadImage(survey.imgOne) }
                if (bytes != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        binding.ivImage1.setImageBitmap(bitmap)
                        binding.ivImage1.visibility = View.VISIBLE
                        binding.tvImage1Label.visibility = View.VISIBLE
                        binding.tvImage1Label.text = "Door Pic"
                    }
                } else {
                    binding.tvImage1Label.text = "Door Pic (server)"
                    binding.tvImage1Label.visibility = View.VISIBLE
                }
            }
        }

        if (img2Bytes != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(img2Bytes, 0, img2Bytes.size)
                }
                if (bitmap != null) {
                    binding.ivImage2.setImageBitmap(bitmap)
                    binding.ivImage2.visibility = View.VISIBLE
                    binding.tvImage2Label.visibility = View.VISIBLE
                    binding.tvImage2Label.text = "Front View"
                }
            }
        } else if (survey.imgTwo.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) { downloadImage(survey.imgTwo) }
                if (bytes != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        binding.ivImage2.setImageBitmap(bitmap)
                        binding.ivImage2.visibility = View.VISIBLE
                        binding.tvImage2Label.visibility = View.VISIBLE
                        binding.tvImage2Label.text = "Front View"
                    }
                } else {
                    binding.tvImage2Label.text = "Front View (server)"
                    binding.tvImage2Label.visibility = View.VISIBLE
                }
            }
        }

        val hasAny = img1Bytes != null || img2Bytes != null || survey.imgOne.isNotBlank() || survey.imgTwo.isNotBlank()
        if (!hasAny) {
            binding.tvImages.text = "No images available"
            binding.tvImages.visibility = View.VISIBLE
        } else {
            binding.tvImages.visibility = View.GONE
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

        binding.btnDone.setOnClickListener {
            it.animateTapFeedback {
                if (isAdded) {
                    val popped = findNavController().popBackStack(R.id.dashboardFragment, false)
                    if (!popped) {
                        findNavController().navigate(R.id.action_sheet_to_dashboard)
                    }
                }
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
                    val resolved = resolveImages(survey)
                    com.ruda.survey.utils.PdfGenerator.generate(resolved)
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
                    val resolved = resolveImages(survey)
                    com.ruda.survey.utils.PdfGenerator.generate(resolved)
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

    private fun resolveImages(survey: SurveyItem): SurveyItem {
        android.util.Log.d("SheetFragment", "resolveImages: img1Bytes=${survey.image1Bytes != null} img2Bytes=${survey.image2Bytes != null} imgOne='${survey.imgOne}' imgTwo='${survey.imgTwo}'")
        val pendingImages = viewModel.pendingImages.value
        val img1 = survey.image1Bytes
            ?: pendingImages.find { it.imageType == "imgOne" }?.stampedBytes
            ?: pendingImages.find { it.imageType == "imgOne" }?.originalBytes
            ?: downloadImage(survey.imgOne)
        val img2 = survey.image2Bytes
            ?: pendingImages.find { it.imageType == "imgTwo" }?.stampedBytes
            ?: pendingImages.find { it.imageType == "imgTwo" }?.originalBytes
            ?: downloadImage(survey.imgTwo)
        android.util.Log.d("SheetFragment", "resolveImages result: img1=${img1?.size} img2=${img2?.size}")
        return survey.copy(image1Bytes = img1, image2Bytes = img2)
    }

    private fun downloadImage(url: String): ByteArray? {
        if (url.isBlank()) return null
        return try {
            val fullUrl = if (url.startsWith("http")) url else "https://api.ruda-surv.nespakprogresscenter.com/$url"
            val tokenManager = RepositoryFactory.getTokenManager(requireContext().applicationContext)
            val token = tokenManager.getAccessToken()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val builder = Request.Builder().url(fullUrl)
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            val request = builder.build()
            android.util.Log.d("SheetFragment", "Downloading image: $fullUrl")
            val response = client.newCall(request).execute()
            android.util.Log.d("SheetFragment", "Image response: code=${response.code} contentLength=${response.body?.contentLength()}")
            if (response.isSuccessful) response.body?.bytes() else null
        } catch (e: Exception) {
            android.util.Log.e("SheetFragment", "Failed to download image: $url", e)
            null
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

    companion object {
        private const val KEY_SUBMITTED_AT = "sheet_submitted_at"
    }
}

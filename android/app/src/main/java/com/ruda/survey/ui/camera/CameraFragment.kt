package com.ruda.survey.ui.camera

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import com.ruda.survey.R
import com.ruda.survey.data.remote.ApiClient
import com.ruda.survey.data.remote.SecureTokenManager
import com.ruda.survey.data.local.SurveyDatabase
import com.ruda.survey.data.repository.SurveyRepositoryImpl
import com.ruda.survey.databinding.FragmentCameraBinding
import com.ruda.survey.domain.model.PendingImage
import com.ruda.survey.domain.repository.SurveyRepository
import com.ruda.survey.ui.survey.SurveyViewModel
import com.ruda.survey.ui.survey.SurveyViewModelFactory
import com.ruda.survey.utils.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private var imageCapture: ImageCapture? = null
    private var imageType: String = "FRONT"
    private lateinit var viewModel: SurveyViewModel
    private lateinit var locationHelper: LocationHelper
    private val handler = Handler(Looper.getMainLooper())

    private var capturedOriginalFile: File? = null
    private var capturedStampData: ImageStampProcessor.StampData? = null
    private var isPreviewMode = false

    private val imageTypes = listOf("FRONT", "SECOND", "POINT_1", "POINT_2")

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else
            Snackbar.make(binding.root, "Camera permission required", Snackbar.LENGTH_LONG).show()
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startLocationUpdates()
        } else {
            binding.tvGpsStatus.text = "Location permission denied"
            binding.gpsProgress.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tokenManager = SecureTokenManager(requireContext().applicationContext)
        val api = ApiClient.createApi(requireContext().applicationContext)
        val db = SurveyDatabase.getInstance(requireContext().applicationContext)
        val repository: SurveyRepository = SurveyRepositoryImpl(api, db.surveyDao(), tokenManager)
        val factory = SurveyViewModelFactory(repository, appContext = requireContext().applicationContext)
        viewModel = ViewModelProvider(requireActivity(), factory)[SurveyViewModel::class.java]

        locationHelper = LocationHelper(requireContext())

        setupImageTypeSpinner()
        setupPreviewControls()

        val pendingType = viewModel.pendingImageType
        if (pendingType in imageTypes) {
            imageType = pendingType
            binding.spinnerImageType.setText(pendingType, false)
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        requestLocationPermissions()
        binding.btnCapture.setOnClickListener { animateCapture() }
    }

    private fun requestLocationPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (perms.isNotEmpty()) {
            requestLocationPermission.launch(perms.toTypedArray())
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        binding.tvGpsStatus.text = "Acquiring location..."
        binding.gpsProgress.visibility = View.VISIBLE

        locationHelper.startUpdates { location ->
            binding.gpsProgress.visibility = View.GONE
            binding.ivGpsIcon.visibility = View.VISIBLE
            val accuracyStr = String.format(Locale.US, "%.0f", location.accuracy)
            binding.tvGpsStatus.text = "GPS Ready (${accuracyStr}m)"
        }
    }

    private fun setupImageTypeSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            imageTypes
        )
        binding.spinnerImageType.setAdapter(adapter)
        binding.spinnerImageType.setText("FRONT", false)
        binding.spinnerImageType.setOnItemClickListener { _, _, position, _ ->
            imageType = imageTypes[position]
        }
    }

    private fun setupPreviewControls() {
        binding.btnRetake.setOnClickListener { retakePhoto() }
        binding.btnUsePhoto.setOnClickListener { usePhoto() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture
                )
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Camera start failed", Snackbar.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun animateCapture() {
        val reduced = view?.isReducedMotionEnabled()

        if (reduced != true) {
            binding.flashOverlay.alpha = 1f
            binding.flashOverlay.visibility = View.VISIBLE
            binding.flashOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.flashOverlay.visibility = View.GONE }
                .start()

            binding.btnCapture.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .setDuration(75)
                .withEndAction {
                    binding.btnCapture.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(75)
                        .start()
                }
                .start()
        }

        takePhoto()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(requireContext().cacheDir, "IMG_${imageType}_${fileName}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedOriginalFile = file
                    processAndPreview(file)
                }
                override fun onError(exc: ImageCaptureException) {
                    Snackbar.make(binding.root, "Capture failed: ${exc.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun processAndPreview(file: File) {
        val location = locationHelper.currentLocation ?: run {
            binding.tvGpsStatus.text = "Using last known location..."
            null
        }

        if (location == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val lastLocation = locationHelper.getLastLocation()
                if (lastLocation != null) {
                    processImageWithLocation(file, lastLocation)
                } else {
                    val draft = viewModel.draftState.value
                    val formLat = draft?.fields?.get("latitude")?.toString()?.toDoubleOrNull()
                    val formLng = draft?.fields?.get("longitude")?.toString()?.toDoubleOrNull()
                    if (formLat != null && formLng != null && formLat != 0.0 && formLng != 0.0) {
                        val fallbackLocation = Location("form").apply {
                            latitude = formLat
                            longitude = formLng
                            accuracy = 100f
                            time = System.currentTimeMillis()
                        }
                        processImageWithLocation(file, fallbackLocation)
                        view?.post {
                            Snackbar.make(binding.root, "Using form location", Snackbar.LENGTH_SHORT).show()
                        }
                    } else {
                        view?.post {
                            Snackbar.make(
                                binding.root,
                                "Location unavailable. Please enable GPS and retry.",
                                Snackbar.LENGTH_LONG
                            ).setAction("Retry") { takePhoto() }.show()
                        }
                    }
                }
            }
            return
        }

        processImageWithLocation(file, location)
    }

    private fun processImageWithLocation(file: File, location: Location) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        executor.execute {
            val originalBitmap = decodeSampledBitmap(file.absolutePath, 2048, 2048)
            if (originalBitmap == null) {
                view?.post {
                    Snackbar.make(binding.root, "Failed to decode image", Snackbar.LENGTH_LONG).show()
                }
                executor.shutdown()
                return@execute
            }

            val gpsLocation = locationHelper.buildGpsLocation(location)
            if (gpsLocation == null) {
                val fallbackLoc = Location("fallback").apply {
                    latitude = location.latitude
                    longitude = location.longitude
                    accuracy = location.accuracy
                    time = location.time
                }
                val fallbackGps = GpsLocation(
                    latitude = fallbackLoc.latitude,
                    longitude = fallbackLoc.longitude,
                    accuracy = fallbackLoc.accuracy,
                    timestamp = fallbackLoc.time,
                    areaName = null
                )
                processImageWithGps(file, originalBitmap, fallbackGps)
            } else {
                processImageWithGps(file, originalBitmap, gpsLocation)
            }
            executor.shutdown()
        }
    }

    private fun processImageWithGps(file: File, originalBitmap: android.graphics.Bitmap, gpsLocation: GpsLocation) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        executor.execute {
            val evidenceId = UUID.randomUUID().toString()
            val draft = viewModel.draftState.value
            val parcelCode = draft?.parcelCode ?: "UNKNOWN"
            val revisionNo = draft?.baseRevisionNo

            val qrBitmap = QrCodeGenerator.generate(
                evidenceId = evidenceId,
                parcelCode = parcelCode,
                revisionNo = revisionNo,
                pointId = imageType,
                latitude = gpsLocation.latitude,
                longitude = gpsLocation.longitude,
                capturedAt = gpsLocation.timestamp
            )

            val stampData = ImageStampProcessor.StampData(
                latitude = gpsLocation.latitude,
                longitude = gpsLocation.longitude,
                accuracy = gpsLocation.accuracy,
                areaName = gpsLocation.areaName,
                capturedAt = gpsLocation.timestamp,
                parcelCode = parcelCode,
                pointId = imageType,
                qrBitmap = qrBitmap
            )

            val stampedBitmap = ImageStampProcessor.stampImage(originalBitmap, stampData)

            val stampedFile = File(requireContext().cacheDir, "STAMPED_${file.name}")
            stampedFile.outputStream().use { out ->
                stampedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }

            view?.post {
                capturedStampData = stampData

                binding.previewView.visibility = View.GONE
                binding.ivPreview.setImageBitmap(stampedBitmap)
                binding.ivPreview.visibility = View.VISIBLE

                binding.captureControls.visibility = View.GONE
                binding.previewControls.visibility = View.VISIBLE
                binding.gpsStatusLayout.visibility = View.GONE

                isPreviewMode = true
            }
            executor.shutdown()
        }
    }

    private fun retakePhoto() {
        isPreviewMode = false
        capturedOriginalFile = null
        capturedStampData = null

        binding.ivPreview.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE

        binding.previewControls.visibility = View.GONE
        binding.captureControls.visibility = View.VISIBLE
        binding.gpsStatusLayout.visibility = View.VISIBLE
    }

    private fun usePhoto() {
        val file = capturedOriginalFile ?: return
        val stampData = capturedStampData ?: return

        val originalBytes = file.readBytes()
        val stampedFile = File(requireContext().cacheDir, "STAMPED_${file.name}")
        val stampedBytes = if (stampedFile.exists()) stampedFile.readBytes() else originalBytes

        val qrPayload = QrCodeGenerator.buildPayload(
            evidenceId = UUID.randomUUID().toString(),
            parcelCode = stampData.parcelCode,
            revisionNo = viewModel.draftState.value?.baseRevisionNo,
            pointId = stampData.pointId,
            latitude = stampData.latitude,
            longitude = stampData.longitude,
            capturedAt = stampData.capturedAt
        )

        val pending = PendingImage(
            imageType = imageType,
            originalBytes = originalBytes,
            stampedBytes = stampedBytes,
            fileName = file.name,
            latitude = stampData.latitude,
            longitude = stampData.longitude,
            accuracy = stampData.accuracy,
            areaName = stampData.areaName,
            capturedAt = stampData.capturedAt,
            pointId = stampData.pointId,
            qrPayload = qrPayload
        )

        viewModel.queueGpsImage(pending)

        Snackbar.make(binding.root, "Photo saved ($imageType)", Snackbar.LENGTH_SHORT).show()

        handler.postDelayed({
            if (isAdded) {
                findNavController().popBackStack()
            }
        }, 500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationHelper.stopUpdates()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

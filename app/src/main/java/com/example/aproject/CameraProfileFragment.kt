package com.example.aproject

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.aproject.databinding.FragmentCameraBinding
import com.example.aproject.databinding.FragmentCameraProfileBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraProfileFragment: Fragment() {
    lateinit var binding: FragmentCameraProfileBinding
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var outputDirectory: File
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    lateinit var photoFile: File
    var photoSaved = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val FILENAME_FORMAT = "_yyyyMMddHHmmss"
    }
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentCameraProfileBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        binding.takePhoto.setOnClickListener {
            takePhoto()
        }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        val bitmapPrevious = BitmapFactory.decodeResource(requireActivity().resources, R.drawable.photo)
        val width = bitmapPrevious.width / (bitmapPrevious.height / 160)
        val height = bitmapPrevious.height / (bitmapPrevious.height / 160)
        val  bitmapScaled = Bitmap.createScaledBitmap(bitmapPrevious, width, height, false)
        binding.previousPhoto.setImageBitmap(doInvert(bitmapScaled))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>,
            grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Permissions not granted by the user",
                        Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            imageAnalyzer = ImageAnalysis.Builder().build()
            val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview,
                        imageCapture, imageAnalyzer)
                preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            } catch (exc: Exception) {
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun getOutputDirectory(): File {
        val mediaDir = activity?.externalMediaDirs?.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else activity?.filesDir!!
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        photoFile = File(outputDirectory, resources.getString(R.string.app_name)
                + SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {}
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        //val bitmap = MediaStore.Images.Media.getBitmap(requireActivity()
                        //.contentResolver, Uri.fromFile(photoFile))
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        val ei = ExifInterface(photoFile.absolutePath)
                        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED)
                        var rotatedBitmap: Bitmap? = null
                        rotatedBitmap = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90.0f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180.0f)
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270.0f)
                            ExifInterface.ORIENTATION_NORMAL -> {
                                bitmap
                            }
                            else -> {
                                bitmap
                            }
                        }
                        binding.newPhoto.setImageBitmap(rotatedBitmap)
                        binding.newPhoto.visibility = View.VISIBLE
                        photoSaved = true
                    }
                })
    }

    fun rotateImage(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height,
                matrix, true)
    }

    private fun doInvert(bitmap: Bitmap): Bitmap? {
        val bitmapOut = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val height = bitmap.height
        val width = bitmap.width
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelColor = bitmap.getPixel(x, y)
                val A = 150
                val R = Color.red(pixelColor)
                val G = Color.green(pixelColor)
                val B = Color.blue(pixelColor)
                bitmapOut.setPixel(x, y, Color.argb(A, R, G, B))
            }
        }
        return bitmapOut
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
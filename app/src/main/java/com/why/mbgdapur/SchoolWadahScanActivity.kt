package com.why.mbgdapur

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.why.mbgdapur.databinding.ActivityVendorWadahScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SchoolWadahScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVendorWadahScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var isProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorWadahScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.tvScanTitle.text = "KONFIRMASI PENERIMAAN"
        binding.tvScanSubtitle.text = "Arahkan kamera ke QR Code di HP Driver"

        binding.btnBackWadah.setOnClickListener { finish() }
        
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewViewWadah.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !isProcessed) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            processLiveScan(image, imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka kamera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processLiveScan(image: InputImage, imageProxy: androidx.camera.core.ImageProxy) {
        val options = BarcodeScannerOptions.Builder().build()
        val scanner = BarcodeScanning.getClient(options)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val rawValue = barcodes[0].rawValue?.trim()?.lowercase() ?: ""
                    if (rawValue.isNotEmpty()) {
                        isProcessed = true
                        handleResult(rawValue)
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleResult(driverEmail: String) {
        // Logika verifikasi kedatangan driver di sekolah
        db.collection("drivers").document(driverEmail).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                db.collection("drivers").document(driverEmail)
                    .update("statusDelivery", "ARRIVED")
                    .addOnSuccessListener {
                        Toast.makeText(this, "Kedatangan Driver Berhasil Diverifikasi!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener {
                        isProcessed = false
                        Toast.makeText(this, "Gagal memperbarui status driver", Toast.LENGTH_SHORT).show()
                    }
            } else {
                isProcessed = false
                Toast.makeText(this, "QR Code tidak valid (Driver tidak ditemukan)", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            isProcessed = false
            Toast.makeText(this, "Koneksi bermasalah", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

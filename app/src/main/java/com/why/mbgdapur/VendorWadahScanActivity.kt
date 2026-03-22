package com.why.mbgdapur

import android.app.Activity
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VendorWadahScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVendorWadahScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var isProcessed = false
    private var scanMode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorWadahScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        scanMode = intent.getStringExtra("SCAN_MODE")
        
        when (scanMode) {
            "COURIER_VERIFICATION" -> {
                binding.tvScanTitle.text = "VERIFIKASI KURIR"
                binding.tvScanSubtitle.text = "Arahkan kamera ke QR Code di HP Driver"
            }
            "RETUR_VERIFICATION" -> {
                binding.tvScanTitle.text = "VERIFIKASI PENJEMPUTAN"
                binding.tvScanSubtitle.text = "Scan QR Driver untuk mulai Penjemputan"
            }
            else -> {
                binding.tvScanTitle.text = "PENERIMAAN WADAH"
                binding.tvScanSubtitle.text = "Scan QR Driver untuk konfirmasi pengembalian boks"
            }
        }

        checkExistingVerification()
        binding.btnBackWadah.setOnClickListener { finish() }
    }

    private fun checkExistingVerification() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorRef = docs.documents[0].reference
                vendorRef.collection("daily_schedules").document(today).get().addOnSuccessListener { schedule ->
                    if (schedule.exists()) {
                        val step = schedule.getLong("currentStep") ?: 0
                        // Kunci jika sudah melewati tahap scan wadah (Step 7)
                        if (step >= 8 && scanMode != "COURIER_VERIFICATION" && scanMode != "RETUR_VERIFICATION") {
                            lockUI()
                        } else {
                            startCamera()
                        }
                    } else {
                        startCamera()
                    }
                }
            }
        }
    }

    private fun lockUI() {
        isProcessed = true
        binding.tvScanTitle.text = "SUDAH TERVERIFIKASI"
        binding.tvScanSubtitle.text = "Wadah kotor sudah diterima oleh dapur."
        binding.previewViewWadah.visibility = View.GONE
        Toast.makeText(this, "Proses ini sudah selesai dan terkunci.", Toast.LENGTH_SHORT).show()
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

    private fun handleResult(email: String) {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val userEmail = auth.currentUser?.email

        when (scanMode) {
            "COURIER_VERIFICATION" -> {
                db.collection("drivers").document(email)
                    .update("statusHandover", "VERIFIED")
                    .addOnSuccessListener {
                        val resultIntent = Intent()
                        resultIntent.putExtra("SCANNED_EMAIL", email)
                        setResult(101, resultIntent)
                        Toast.makeText(this, "Driver Terverifikasi!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        isProcessed = false
                        Toast.makeText(this, "Driver tidak ditemukan!", Toast.LENGTH_SHORT).show()
                    }
            }
            "RETUR_VERIFICATION" -> {
                db.collection("drivers").document(email)
                    .update("statusDelivery", "READY_RETUR")
                    .addOnSuccessListener {
                        Toast.makeText(this, "Penjemputan Diizinkan!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        isProcessed = false
                        Toast.makeText(this, "ID Driver tidak valid", Toast.LENGTH_SHORT).show()
                    }
            }
            else -> {
                val batch = db.batch()
                val driverRef = db.collection("drivers").document(email)
                
                batch.update(driverRef, mapOf(
                    "statusDelivery" to "RETUR_DONE",
                    "statusHandover" to "VERIFIED"
                ))

                if (userEmail != null) {
                    db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { vendors ->
                        if (!vendors.isEmpty) {
                            val vendorRef = vendors.documents[0].reference
                            val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                            
                            val updates = mapOf(
                                "currentStep" to 8,
                                "statusProduksi" to "SANITASI PERALATAN"
                            )
                            batch.update(vendorRef, updates)
                            batch.update(scheduleRef, updates)
                            
                            batch.commit().addOnSuccessListener {
                                Toast.makeText(this, "Wadah Diterima! Tahap Sanitasi Terbuka.", Toast.LENGTH_LONG).show()
                                finish()
                            }.addOnFailureListener {
                                isProcessed = false
                                Toast.makeText(this, "Gagal memperbarui data", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    batch.commit().addOnSuccessListener { finish() }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

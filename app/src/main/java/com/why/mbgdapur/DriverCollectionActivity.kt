package com.why.mbgdapur

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import android.graphics.Paint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.why.mbgdapur.databinding.DriverCollectionBinding
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DriverCollectionActivity : AppCompatActivity() {

    private lateinit var binding: DriverCollectionBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var boxCount = 0
    private var isScanDone = false
    private var isPhotoDone = false
    private var isSignatureDone = false
    private var isScannerActive = false
    private var verifiedGuruEmail: String = ""
    
    private var stopNumber = 1
    private var targetSchoolEmail: String = ""
    private var targetSchoolName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        stopNumber = intent.getIntExtra("STOP_NUMBER", 1)
        targetSchoolEmail = intent.getStringExtra("TARGET_SCHOOL_EMAIL") ?: ""
        
        binding.tvBoxCount.text = "0"
        
        loadTargetData()
        setupCounter()
        setupActions()
    }

    private fun loadTargetData() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        if (targetSchoolEmail.isNotEmpty()) {
            db.collection("drivers").document(email).get().addOnSuccessListener { driverDoc ->
                val vEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
                
                db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendors ->
                    if (!vendors.isEmpty) {
                        val vendorRef = vendors.documents[0].reference
                        
                        vendorRef.collection("daily_schedules").document(today)
                            .collection("destinations").document(targetSchoolEmail).get().addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    targetSchoolName = doc.getString("schoolName") ?: "Sekolah"
                                    val targetPorsi = doc.getLong("targetPorsi") ?: 0L
                                    boxCount = targetPorsi.toInt()
                                    
                                    binding.tvCollectionSchool.text = targetSchoolName
                                    binding.tvBoxCount.text = boxCount.toString()
                                }
                            }
                    }
                }
            }
        }
    }

    private fun setupCounter() {
        binding.btnPlus.setOnClickListener { boxCount++; binding.tvBoxCount.text = boxCount.toString() }
        binding.btnMinus.setOnClickListener { if (boxCount > 0) { boxCount--; binding.tvBoxCount.text = boxCount.toString() } }
    }

    private fun setupActions() {
        binding.btnScanCollect.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openLiveScanner()
            else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }

        val photoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(imageBitmap, 640, 480, true)
                binding.ivPhotoPreviewCollect.setImageBitmap(scaledBitmap)
                binding.ivPhotoPreviewCollect.visibility = View.VISIBLE
                binding.cardStepPhotoCollect.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
                isPhotoDone = true; checkValidation()
            }
        }

        binding.btnPhotoCollect.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            photoLauncher.launch(intent)
        }

        binding.signaturePadCollect.setOnSignedListener {
            if (!isSignatureDone) {
                isSignatureDone = true
                binding.cardStepSignatureCollect.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
                checkValidation()
            }
        }

        binding.btnClearSignatureCollect.setOnClickListener {
            binding.signaturePadCollect.clear()
            isSignatureDone = false
            binding.cardStepSignatureCollect.strokeColor = ContextCompat.getColor(this, android.R.color.darker_gray)
            binding.btnFinishCollection.isEnabled = false
            binding.btnFinishCollection.alpha = 0.5f
        }

        binding.btnFinishCollection.setOnClickListener { 
            binding.btnFinishCollection.isEnabled = false
            binding.btnFinishCollection.text = "MEMPROSES..."
            saveCollectionData() 
        }
    }

    private fun openLiveScanner() { binding.layoutLiveScannerCollect.visibility = View.VISIBLE; isScannerActive = true; startCameraX() }
    private fun closeLiveScanner() { binding.layoutLiveScannerCollect.visibility = View.GONE; isScannerActive = false }

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewViewCollect.surfaceProvider) }
            val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy -> processImageProxy(imageProxy) }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && isScannerActive) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            BarcodeScanning.getClient().process(image).addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty() && isScannerActive) {
                    val qrValue = barcodes[0].rawValue ?: ""
                    if (qrValue.isNotEmpty()) runOnUiThread { onScanSuccess(qrValue) }
                }
            }.addOnCompleteListener { imageProxy.close() }
        } else imageProxy.close()
    }

    private fun onScanSuccess(value: String) {
        val parts = value.split("|")
        val scannedEmail = parts[0].trim().lowercase()
        val scannedMode = if (parts.size > 1) parts[1] else ""

        if (scannedEmail == targetSchoolEmail.trim().lowercase() && scannedMode == "RETUR") {
            isScannerActive = false; isScanDone = true; verifiedGuruEmail = scannedEmail; closeLiveScanner()
            binding.tvScanStatusCollect.text = "Terverifikasi: $scannedEmail"
            binding.cardStepScanCollect.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
            checkValidation()
        } else if (scannedMode == "TERIMA") {
            Toast.makeText(this, "Salah QR! Ini QR untuk Penerimaan Makan.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "QR tidak valid untuk penjemputan!", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCollectionData() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        try {
            val fotoDrawable = binding.ivPhotoPreviewCollect.drawable as? BitmapDrawable
            val fotoBase64 = if (fotoDrawable != null) encodeBitmapToBase64(fotoDrawable.bitmap) else ""
            
            val signatureBitmap = Bitmap.createBitmap(binding.signaturePadCollect.width, binding.signaturePadCollect.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(signatureBitmap)
            canvas.drawColor(Color.WHITE)
            binding.signaturePadCollect.draw(canvas)

            val scaledSignature = Bitmap.createScaledBitmap(signatureBitmap, 400, 200, true)
            val signatureBase64 = encodeBitmapToBase64(scaledSignature)

            val collectionLog = hashMapOf(
                "returStatus" to "DONE",
                "returBoxCount" to boxCount,
                "returFoto" to fotoBase64,
                "returSignature" to signatureBase64,
                "returWaktu" to FieldValue.serverTimestamp(),
                "returGuru" to verifiedGuruEmail,
                "schoolName" to targetSchoolName,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val logId = "${today}_$targetSchoolEmail"
            val driverRef = db.collection("drivers").document(userEmail)

            db.collection("drivers").document(userEmail).get().addOnSuccessListener { driverDoc ->
                val vEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
                db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendors ->
                    if (!vendors.isEmpty) {
                        val vendorRef = vendors.documents[0].reference
                        vendorRef.collection("daily_schedules").document(today)
                            .collection("destinations").get().addOnSuccessListener { destinations ->
                                
                                val totalSchools = destinations.size()
                                
                                driverRef.collection("delivery_logs").get().addOnSuccessListener { logs ->
                                    val doneReturCount = logs.filter { 
                                        it.id.startsWith(today) && it.getString("returStatus") == "DONE" 
                                    }.size + 1
                                    
                                    val batch = db.batch()
                                    batch.set(driverRef.collection("delivery_logs").document(logId), collectionLog, SetOptions.merge())

                                    if (doneReturCount >= totalSchools) {
                                        batch.update(driverRef, mapOf(
                                            "statusDelivery" to "RETUR_DONE",
                                            "currentNavigatingSchool" to "",
                                            "lastUpdate" to FieldValue.serverTimestamp()
                                        ))
                                    } else {
                                        batch.update(driverRef, mapOf(
                                            "statusDelivery" to "READY_RETUR",
                                            "currentNavigatingSchool" to "",
                                            "lastUpdate" to FieldValue.serverTimestamp()
                                        ))
                                    }

                                    batch.commit().addOnSuccessListener {
                                        Toast.makeText(this, "Berhasil Menjemput: $targetSchoolName", Toast.LENGTH_SHORT).show()
                                        val intent = Intent(this, DriverDashboardActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                        startActivity(intent)
                                        finish()
                                    }.addOnFailureListener { e ->
                                        binding.btnFinishCollection.isEnabled = true
                                        binding.btnFinishCollection.text = "KONFIRMASI PENJEMPUTAN"
                                        Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                    }
                }
            }
        } catch (e: Exception) {
            binding.btnFinishCollection.isEnabled = true
            binding.btnFinishCollection.text = "KONFIRMASI PENJEMPUTAN"
            Toast.makeText(this, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkValidation() {
        if (isScanDone && isPhotoDone && isSignatureDone) {
            binding.btnFinishCollection.isEnabled = true
            binding.btnFinishCollection.alpha = 1.0f
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}

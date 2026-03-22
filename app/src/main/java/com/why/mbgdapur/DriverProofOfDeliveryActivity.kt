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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.why.mbgdapur.databinding.DriverProofOfDeliveryBinding
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DriverProofOfDeliveryActivity : AppCompatActivity() {

    private lateinit var binding: DriverProofOfDeliveryBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var isScanDone = false
    private var isPhotoDone = false
    private var isSignatureDone = false
    private var stopNumber = 1
    private var isScannerActive = false
    private var verifiedGuruEmail: String = ""
    private var targetSchoolEmail: String = ""
    private var targetSchoolName: String = "Sekolah"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverProofOfDeliveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        stopNumber = intent.getIntExtra("STOP_NUMBER", 1)
        targetSchoolEmail = intent.getStringExtra("TARGET_SCHOOL_EMAIL") ?: ""
        
        loadTargetData()
        setupActions()
        setupSignaturePad()
    }

    private fun loadTargetData() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        if (targetSchoolEmail.isNotEmpty()) {
            db.collection("drivers").document(email).get().addOnSuccessListener { driverDoc ->
                val vEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
                db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendorDocs ->
                    if (!vendorDocs.isEmpty) {
                        vendorDocs.documents[0].reference.collection("daily_schedules").document(today)
                            .collection("destinations").document(targetSchoolEmail).get().addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    targetSchoolName = doc.getString("schoolName") ?: "Sekolah"
                                    binding.tvSchoolTarget.text = targetSchoolName
                                    binding.tvDropSubtitle.text = "Target: ${doc.getLong("targetPorsi") ?: 0} Boks Makanan"
                                }
                            }
                    }
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnOpenScanner.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openLiveScanner()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
            }
        }

        binding.btnCloseScanner.setOnClickListener { closeLiveScanner() }

        val boxPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap
                binding.ivPhotoPreviewDrop.setImageBitmap(imageBitmap)
                binding.ivPhotoPreviewDrop.visibility = View.VISIBLE
                binding.cardStepPhoto.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
                isPhotoDone = true
                checkAllRequirements()
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            boxPhotoLauncher.launch(intent)
        }

        binding.btnFinishDelivery.setOnClickListener { saveProofOfDelivery() }
    }

    private fun openLiveScanner() {
        binding.layoutLiveScanner.visibility = View.VISIBLE
        isScannerActive = true
        startCameraX()
    }

    private fun closeLiveScanner() {
        binding.layoutLiveScanner.visibility = View.GONE
        isScannerActive = false
    }

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewViewProof.surfaceProvider) }
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
        } else { imageProxy.close() }
    }

    private fun onScanSuccess(value: String) {
        val parts = value.split("|")
        val scannedEmail = parts[0].trim().lowercase()
        val scannedMode = if (parts.size > 1) parts[1] else ""

        if (scannedEmail == targetSchoolEmail.trim().lowercase() && scannedMode == "TERIMA") {
            isScannerActive = false; isScanDone = true; verifiedGuruEmail = scannedEmail; closeLiveScanner()
            binding.ivScanCheck.visibility = View.VISIBLE
            binding.tvScanStatus.text = "Terverifikasi: $scannedEmail"
            binding.cardStepScan.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
            checkAllRequirements()
        } else if (scannedMode == "RETUR") {
            Toast.makeText(this, "Salah QR! Ini QR untuk Penjemputan Boks kotor.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "QR tidak valid untuk sekolah ini!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSignaturePad() {
        binding.signaturePad.setOnSignedListener {
            if (!isSignatureDone) {
                isSignatureDone = true
                binding.cardStepSignature.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
                checkAllRequirements()
            }
        }
        binding.btnClearSignature.setOnClickListener {
            binding.signaturePad.clear(); isSignatureDone = false
            binding.cardStepSignature.strokeColor = ContextCompat.getColor(this, android.R.color.darker_gray)
            checkAllRequirements()
        }
    }

    private fun checkAllRequirements() {
        if (isScanDone && isPhotoDone && isSignatureDone) {
            binding.btnFinishDelivery.isEnabled = true; binding.btnFinishDelivery.alpha = 1.0f
        }
    }

    private fun saveProofOfDelivery() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        val fotoDrawable = binding.ivPhotoPreviewDrop.drawable as? BitmapDrawable
        val fotoBase64 = if (fotoDrawable != null) encodeBitmapToBase64(fotoDrawable.bitmap) else ""

        val signatureBitmap = Bitmap.createBitmap(binding.signaturePad.width, binding.signaturePad.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(signatureBitmap)
        canvas.drawColor(Color.WHITE)
        binding.signaturePad.draw(canvas)
        val signatureBase64 = encodeBitmapToBase64(signatureBitmap)

        db.collection("drivers").document(userEmail).get().addOnSuccessListener { driverDoc ->
            val vEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener

            db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendorDocs ->
                if (vendorDocs.isEmpty) return@addOnSuccessListener

                val vendorRef = vendorDocs.documents[0].reference
                val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                val historyRef = vendorRef.collection("history").document(today)

                scheduleRef.get().addOnSuccessListener { scheduleDoc ->
                    val deadline = scheduleDoc.getString("deliveryDeadline") ?: "11:30"
                    val isLate = checkIfLate(deadline)
                    
                    updateDriverPerformance(userEmail, isLate)

                    val menuInfo = hashMapOf(
                        "menu" to (scheduleDoc.getString("menu") ?: "Menu MBG"),
                        "nama_menu" to (scheduleDoc.getString("menu") ?: "Menu MBG"),
                        "foto_menu" to (scheduleDoc.getString("menuPhotoBase64") ?: ""),
                        "menuConfig" to scheduleDoc.get("menuConfig"),
                        "statusLaporan" to "BERJALAN"
                    )
                    historyRef.set(menuInfo, SetOptions.merge())

                    scheduleRef.collection("destinations").document(targetSchoolEmail).get()
                        .addOnSuccessListener { destDoc ->
                            if (destDoc.exists()) {
                                val dataRiwayatSekolah = destDoc.data?.toMutableMap() ?: mutableMapOf()
                                dataRiwayatSekolah["pod_foto"] = fotoBase64
                                dataRiwayatSekolah["pod_signature"] = signatureBase64
                                dataRiwayatSekolah["pod_verifikasi_guru"] = verifiedGuruEmail
                                dataRiwayatSekolah["waktuSelesai"] = FieldValue.serverTimestamp()
                                dataRiwayatSekolah["statusDelivery"] = "DONE"
                                dataRiwayatSekolah["isLate"] = isLate

                                historyRef.collection("destinations").document(targetSchoolEmail).set(dataRiwayatSekolah)
                            }
                        }

                    scheduleRef.collection("media").get().addOnSuccessListener { mediaDocs ->
                        for (m in mediaDocs) {
                            historyRef.collection("media").document(m.id).set(m.data)
                        }
                    }
                }

                val batch = db.batch()
                val logRef = db.collection("drivers").document(userEmail)
                    .collection("delivery_logs").document("${today}_$targetSchoolEmail")

                batch.set(logRef, hashMapOf(
                    "status" to "DONE",
                    "pod_foto" to fotoBase64,
                    "pod_signature" to signatureBase64,
                    "pod_verifikasi_guru" to verifiedGuruEmail,
                    "waktuSelesai" to FieldValue.serverTimestamp()
                ), SetOptions.merge())

                batch.update(db.collection("drivers").document(userEmail), mapOf(
                    "statusDelivery" to "READY",
                    "currentNavigatingSchool" to ""
                ))

                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "Data Riwayat Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                    checkIfAllSchoolsDone(userEmail, today)
                }
            }
        }
    }

    private fun checkIfLate(deadline: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val deadlineTime = sdf.parse(deadline)
            val currentTime = sdf.parse(sdf.format(Date()))
            currentTime?.after(deadlineTime) ?: false
        } catch (e: Exception) { false }
    }

    private fun updateDriverPerformance(email: String, isLate: Boolean) {
        val driverRef = db.collection("drivers").document(email)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(driverRef)
            val totalDeliveries = (snapshot.getLong("totalDeliveries") ?: 0L) + 1
            val onTimeDeliveries = if (isLate) (snapshot.getLong("onTimeDeliveries") ?: 0L)
                                   else (snapshot.getLong("onTimeDeliveries") ?: 0L) + 1
            
            val newSla = (onTimeDeliveries.toDouble() / totalDeliveries.toDouble()) * 100
            transaction.update(driverRef, mapOf(
                "totalDeliveries" to totalDeliveries,
                "onTimeDeliveries" to onTimeDeliveries,
                "slaScore" to String.format(Locale.US, "%.0f%%", newSla)
            ))
        }
    }

    private fun checkIfAllSchoolsDone(userEmail: String, today: String) {
        db.collection("drivers").document(userEmail).get().addOnSuccessListener { driverDoc ->
            val vEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
            
            db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendors ->
                if (vendors.isEmpty) return@addOnSuccessListener
                
                vendors.documents[0].reference.collection("daily_schedules").document(today)
                    .collection("destinations").get().addOnSuccessListener { destinations ->
                        
                        db.collection("drivers").document(userEmail).collection("delivery_logs").get()
                            .addOnSuccessListener { logs ->
                                val todayDoneCount = logs.documents.count { 
                                    it.id.startsWith(today) && it.getString("status") == "DONE" 
                                }
                                
                                if (todayDoneCount >= destinations.size()) {
                                    db.collection("drivers").document(userEmail).update("statusDelivery", "START_RETUR")
                                        .addOnSuccessListener {
                                            val intent = Intent(this, DriverDeliverySuccessActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            finish()
                                        }
                                } else {
                                    val intent = Intent(this, DriverDashboardActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    startActivity(intent)
                                    finish()
                                }
                            }
                    }
            }
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}

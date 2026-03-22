package com.why.mbgdapur

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SchoolAnomaliReportActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var capturedBitmap: Bitmap? = null
    private var selectedCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_anomali_report)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Setup Dropdown Kategori - Emoticon dihapus sesuai permintaan
        val categories = arrayOf(
            "Porsi Susut / Kurang",
            "Menu Tidak Sesuai",
            "Indikasi Basi / Bau Berubah",
            "Benda Asing (Food Safety)",
            "Kemasan Hancur"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerCategory)
        spinner.setAdapter(adapter)
        spinner.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categories[position]
        }

        // Camera Click
        findViewById<View>(R.id.cardCamera).setOnClickListener {
            if (capturedBitmap == null) {
                takePhoto()
            } else {
                // Reset photo
                capturedBitmap = null
                findViewById<ImageView>(R.id.ivCapturedImage).visibility = View.GONE
                findViewById<View>(R.id.placeholderCamera).visibility = View.VISIBLE
                findViewById<View>(R.id.watermarkOverlay).visibility = View.GONE
                startCamera()
            }
        }

        // Submit Button
        findViewById<Button>(R.id.btnSubmitReport).setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun checkPermissions() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.viewFinder).surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                
                // Add Watermark
                val watermarked = addWatermark(bitmap)
                capturedBitmap = watermarked
                
                runOnUiThread {
                    findViewById<ImageView>(R.id.ivCapturedImage).setImageBitmap(watermarked)
                    findViewById<ImageView>(R.id.ivCapturedImage).visibility = View.VISIBLE
                    findViewById<View>(R.id.placeholderCamera).visibility = View.GONE
                    findViewById<View>(R.id.watermarkOverlay).visibility = View.VISIBLE
                    
                    // Update UI watermarks
                    val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                    findViewById<TextView>(R.id.tvWatermarkTime).text = "Waktu: ${sdf.format(Date())}"
                    // Note: In real app, get actual GPS. Using dummy for now.
                    findViewById<TextView>(R.id.tvWatermarkLocation).text = "Lokasi: -6.2088, 106.8456 (Sekolah)"
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(baseContext, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun addWatermark(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val config = src.config ?: Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(w, h, config)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)

        val paint = Paint()
        paint.color = Color.WHITE
        paint.textSize = w / 25f
        paint.isAntiAlias = true
        paint.setShadowLayer(2f, 1f, 1f, Color.BLACK)

        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val dateString = "Waktu: " + sdf.format(Date())
        val gpsString = "Lokasi: -6.2088, 106.8456"

        canvas.drawText(dateString, 20f, h - 80f, paint)
        canvas.drawText(gpsString, 20f, h - 30f, paint)
        
        return result
    }

    private fun validateAndSubmit() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "Wajib mengambil foto bukti!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedCategory == null) {
            Toast.makeText(this, "Pilih kategori anomali!", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Laporan")
            .setMessage("Apakah Anda yakin? Laporan ini akan ditinjau oleh Pusat dan dapat menangguhkan pembayaran Vendor.")
            .setPositiveButton("YAKIN") { _, _ -> submitToFirebase() }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun submitToFirebase() {
        val userEmail = auth.currentUser?.email ?: return
        val notes = findViewById<EditText>(R.id.etNotes).text.toString()
        
        // Convert bitmap to base64
        val baos = ByteArrayOutputStream()
        capturedBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

        val report = hashMapOf(
            "reporterEmail" to userEmail,
            "category" to selectedCategory,
            "notes" to notes,
            "photoBase64" to imageBase64,
            "timestamp" to System.currentTimeMillis(),
            "status" to "PENDING_INVESTIGATION",
            "date" to SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        )

        findViewById<Button>(R.id.btnSubmitReport).isEnabled = false
        findViewById<Button>(R.id.btnSubmitReport).text = "MENGIRIM..."

        db.collection("anomali_reports").add(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Laporan Berhasil Dikirim!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengirim laporan", Toast.LENGTH_SHORT).show()
                findViewById<Button>(R.id.btnSubmitReport).isEnabled = true
                findViewById<Button>(R.id.btnSubmitReport).text = "KIRIM LAPORAN INVESTIGASI"
            }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

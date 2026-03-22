package com.why.mbgdapur

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SchoolAICheckActivity : AppCompatActivity() {

    private lateinit var btnShutter: MaterialButton
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var previewView: PreviewView

    private lateinit var layoutAnalysisResult1: View
    private lateinit var layoutAnalysisResult2: View
    private lateinit var includeResult1: View
    private lateinit var includeResult2: View

    private lateinit var ivThumb1: ImageView
    private lateinit var ivThumb2: ImageView
    private lateinit var btnSelectPhoto1: MaterialCardView
    private lateinit var btnSelectPhoto2: MaterialCardView
    private lateinit var tvLabel1: TextView
    private lateinit var tvLabel2: TextView
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var capturedBitmap1: Bitmap? = null
    private var capturedBitmap2: Bitmap? = null
    private var activeSlot: Int = 1
    private var lastAiJson: String? = null
    
    // Menggunakan API Key yang sama dengan AIVerificationActivity (yang sudah terbukti berhasil)
    private val apiKey = BuildConfig.GEMINI_API_KEY

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_ai_check)

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnShutter = findViewById(R.id.btnCaptureCheck)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreviewCheck)
        previewView = findViewById(R.id.previewViewAICheck)
        
        layoutAnalysisResult1 = findViewById(R.id.layoutAnalysisResultCheck1)
        layoutAnalysisResult2 = findViewById(R.id.layoutAnalysisResultCheck2)
        includeResult1 = findViewById(R.id.includeResultCheck1)
        includeResult2 = findViewById(R.id.includeResultCheck2)

        ivThumb1 = findViewById(R.id.ivThumb1Check)
        ivThumb2 = findViewById(R.id.ivThumb2Check)
        btnSelectPhoto1 = findViewById(R.id.btnSelectPhoto1Check)
        btnSelectPhoto2 = findViewById(R.id.btnSelectPhoto2Check)
        tvLabel1 = findViewById(R.id.tvLabel1Check)
        tvLabel2 = findViewById(R.id.tvLabel2Check)
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        
        btnShutter.setOnClickListener { handleShutterClick() }

        btnSelectPhoto1.setOnClickListener {
            activeSlot = 1
            updateSlotUI()
        }

        btnSelectPhoto2.setOnClickListener {
            activeSlot = 2
            updateSlotUI()
        }
        
        updateSlotUI()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateSlotUI() {
        // Menyesuaikan warna dengan resource yang tersedia di colors.xml
        btnSelectPhoto1.setStrokeColor(ContextCompat.getColorStateList(this, if (activeSlot == 1) R.color.mbg_green_dark else R.color.mbg_slate_200))
        btnSelectPhoto1.setCardBackgroundColor(Color.parseColor(if (activeSlot == 1) "#F0FDF4" else "#F8FAFC"))
        tvLabel1.setTextColor(ContextCompat.getColor(this, if (activeSlot == 1) R.color.mbg_green_dark else R.color.mbg_slate_400))
        if (capturedBitmap1 == null) ivThumb1.setColorFilter(ContextCompat.getColor(this, if (activeSlot == 1) R.color.mbg_green_dark else R.color.mbg_slate_400))

        btnSelectPhoto2.setStrokeColor(ContextCompat.getColorStateList(this, if (activeSlot == 2) R.color.mbg_green_dark else R.color.mbg_slate_200))
        btnSelectPhoto2.setCardBackgroundColor(Color.parseColor(if (activeSlot == 2) "#F0FDF4" else "#F8FAFC"))
        tvLabel2.setTextColor(ContextCompat.getColor(this, if (activeSlot == 2) R.color.mbg_green_dark else R.color.mbg_slate_400))
        if (capturedBitmap2 == null) ivThumb2.setColorFilter(ContextCompat.getColor(this, if (activeSlot == 2) R.color.mbg_green_dark else R.color.mbg_slate_400))

        val currentBitmap = if (activeSlot == 1) capturedBitmap1 else capturedBitmap2
        if (currentBitmap != null) {
            ivPhotoPreview.setImageBitmap(currentBitmap)
            ivPhotoPreview.visibility = View.VISIBLE
            previewView.visibility = View.GONE
        } else {
            ivPhotoPreview.visibility = View.GONE
            previewView.visibility = View.VISIBLE
        }
        updateButtonState()
    }

    private fun updateButtonState() {
        if (lastAiJson != null) {
            btnShutter.text = "TUTUP"
            btnShutter.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
            return
        }
        if (capturedBitmap1 != null && capturedBitmap2 != null) {
            btnShutter.text = "MULAI ANALISIS AI (2 FOTO)"
            btnShutter.setIconResource(android.R.drawable.ic_menu_set_as)
        } else {
            btnShutter.text = "AMBIL FOTO $activeSlot"
            btnShutter.setIconResource(android.R.drawable.ic_menu_camera)
        }
    }

    private fun handleShutterClick() {
        when {
            btnShutter.text.toString().contains("TUTUP") -> finish()
            btnShutter.text.toString().contains("ANALISIS") -> {
                btnShutter.isEnabled = false
                btnShutter.text = "MENGANALISIS..."
                analyzeWithGemini()
            }
            else -> takePhoto()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        btnShutter.isEnabled = false
        btnShutter.text = "PROSES..."
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                val optimized = resizeBitmap(bitmap, 512)
                runOnUiThread {
                    if (activeSlot == 1) {
                        capturedBitmap1 = optimized
                        ivThumb1.setImageBitmap(optimized)
                        ivThumb1.clearColorFilter()
                    } else {
                        capturedBitmap2 = optimized
                        ivThumb2.setImageBitmap(optimized)
                        ivThumb2.clearColorFilter()
                    }
                    ivPhotoPreview.setImageBitmap(optimized)
                    ivPhotoPreview.visibility = View.VISIBLE
                    previewView.visibility = View.GONE
                    btnShutter.isEnabled = true
                    updateButtonState()
                }
            }
            override fun onError(e: ImageCaptureException) {
                runOnUiThread { btnShutter.isEnabled = true; updateButtonState() }
            }
        })
    }

    private fun analyzeWithGemini() {
        val bitmap1 = capturedBitmap1 ?: return
        val bitmap2 = capturedBitmap2 ?: return

        val generativeModel = GenerativeModel(
            modelName = "gemini-3-flash-preview",
            apiKey = apiKey,
            generationConfig = generationConfig { temperature = 0.1f },
            requestOptions = RequestOptions(apiVersion = "v1beta")
        )

        val prompt = "Analisis kedua foto menu makanan ini secara terpisah. Foto 1 adalah Menu 1, Foto 2 adalah Menu 2. Berikan respon HANYA JSON dengan format: {\"menu1\": {\"status\":\"LULUS\" atau \"REVISI\", \"komposisi\":\"item yang ada\", \"nutrisi\":{\"kalori\":0,\"protein\":0,\"lemak\":0,\"karbo\":0}, \"alasan\":\"singkat\"}, \"menu2\": {...sama dengan menu 1...}}"

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(content { 
                    image(bitmap1)
                    image(bitmap2)
                    text(prompt) 
                })
                val textResponse = response.text ?: ""
                runOnUiThread {
                    try {
                        val start = textResponse.indexOf("{")
                        val end = textResponse.lastIndexOf("}")
                        if (start != -1 && end != -1) {
                            val json = JSONObject(textResponse.substring(start, end + 1))
                            lastAiJson = json.toString()
                            populateResults(json)
                        } else {
                            showErrorResult("Format respon tidak sesuai")
                        }
                    } catch (e: Exception) {
                        showErrorResult("Error Parsing: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showErrorResult(e.localizedMessage ?: "Unknown Error") }
            }
        }
    }

    private fun populateResults(json: JSONObject) {
        val menu1 = json.optJSONObject("menu1")
        val menu2 = json.optJSONObject("menu2")

        if (menu1 != null) {
            layoutAnalysisResult1.visibility = View.VISIBLE
            populateMenuResult(includeResult1, menu1, capturedBitmap1)
        }
        if (menu2 != null) {
            layoutAnalysisResult2.visibility = View.VISIBLE
            populateMenuResult(includeResult2, menu2, capturedBitmap2)
        }

        btnShutter.isEnabled = true
        updateButtonState()
    }

    private fun populateMenuResult(view: View, data: JSONObject, bitmap: Bitmap?) {
        val status = data.optString("status", "REVISI")
        val komposisi = data.optString("komposisi", "--")
        val nutrisi = data.optJSONObject("nutrisi")
        val alasan = data.optString("alasan", "--")

        val tvStatus = view.findViewById<TextView>(R.id.tvAiFinalStatus)
        val ivIcon = view.findViewById<ImageView>(R.id.ivAiStatusIcon)
        val layoutHeader = view.findViewById<View>(R.id.layoutAiStatusHeader)
        val ivFood = view.findViewById<ImageView>(R.id.ivAiFoodPhoto)
        val tvDetection = view.findViewById<TextView>(R.id.tvAiDetection)
        val tvReason = view.findViewById<TextView>(R.id.tvAiReason)
        
        val tvKalori = view.findViewById<TextView>(R.id.tvAiKalori)
        val tvProtein = view.findViewById<TextView>(R.id.tvAiProtein)
        val tvLemak = view.findViewById<TextView>(R.id.tvAiLemak)
        val tvKarbo = view.findViewById<TextView>(R.id.tvAiKarbo)

        tvDetection.text = komposisi
        tvReason.text = alasan
        tvKalori.text = "${nutrisi?.optInt("kalori") ?: 0} kcal"
        tvProtein.text = "${nutrisi?.optInt("protein") ?: 0}g"
        tvLemak.text = "${nutrisi?.optInt("lemak") ?: 0}g"
        tvKarbo.text = "${nutrisi?.optInt("karbo") ?: 0}g"
        
        if (bitmap != null) {
            ivFood.setImageBitmap(bitmap)
            ivFood.alpha = 1.0f
        }

        if (status.contains("LULUS", true) || status.contains("LAYAK", true)) {
            tvStatus.text = "STATUS: LULUS QC"
            tvStatus.setTextColor(Color.BLACK)
            ivIcon.setImageResource(android.R.drawable.checkbox_on_background)
            ivIcon.setColorFilter(Color.parseColor("#16A34A"))
            layoutHeader.setBackgroundColor(Color.parseColor("#F0FDF4"))
        } else {
            tvStatus.text = "STATUS: REVISI"
            tvStatus.setTextColor(Color.parseColor("#EF4444"))
            ivIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            ivIcon.setColorFilter(Color.parseColor("#EF4444"))
            layoutHeader.setBackgroundColor(Color.parseColor("#FEF2F2"))
        }
    }

    private fun showErrorResult(error: String) {
        layoutAnalysisResult1.visibility = View.VISIBLE
        val tvStatus = includeResult1.findViewById<TextView>(R.id.tvAiFinalStatus)
        val tvDetection = includeResult1.findViewById<TextView>(R.id.tvAiDetection)
        tvStatus.text = "STATUS: ERROR"
        tvDetection.text = error
        
        btnShutter.isEnabled = true
        btnShutter.text = "COBA LAGI"
        btnShutter.setOnClickListener { 
            lastAiJson = null
            capturedBitmap1 = null
            capturedBitmap2 = null
            activeSlot = 1
            updateSlotUI()
            layoutAnalysisResult1.visibility = View.GONE
            layoutAnalysisResult2.visibility = View.GONE
            btnShutter.setOnClickListener { handleShutterClick() }
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        if (ratio > 1) {
            width = maxSize
            height = (width / ratio).toInt()
        } else {
            height = maxSize
            width = (height * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

package com.why.mbgdapur

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MorningPrepActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var checkBoxes: List<CheckBox>
    private lateinit var btnSave: MaterialButton
    private lateinit var tvStatus: TextView
    
    private lateinit var ivReceiptPhoto: ImageView
    private lateinit var ivRawMaterialPhoto: ImageView
    private lateinit var etRawWeight: TextInputEditText
    private lateinit var etTotalCapital: TextInputEditText

    private var targetPorsi: Long = 0
    private var menuName: String? = null
    
    private var bitmapReceipt: Bitmap? = null
    private var bitmapRawMaterial: Bitmap? = null

    private val receiptPhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            bitmapReceipt = bitmap
            displaySelectedImage(ivReceiptPhoto, bitmap)
        }
    }

    private val rawMaterialPhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            bitmapRawMaterial = bitmap
            displaySelectedImage(ivRawMaterialPhoto, bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_morning_prep)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvStatus = findViewById(R.id.tvPrepStatus)
        btnSave = findViewById(R.id.btnSaveMorningPrep)
        
        ivReceiptPhoto = findViewById(R.id.ivReceiptPhoto)
        ivRawMaterialPhoto = findViewById(R.id.ivRawMaterialPhoto)
        etRawWeight = findViewById(R.id.etRawWeight)
        etTotalCapital = findViewById(R.id.etTotalCapital)
        
        checkBoxes = listOf(
            findViewById(R.id.cbHealth),
            findViewById(R.id.cbAttributes),
            findViewById(R.id.cbPersonalHygiene),
            findViewById(R.id.cbSterilization),
            findViewById(R.id.cbWorkspace),
            findViewById(R.id.cbQuantity),
            findViewById(R.id.cbQuality)
        )

        findViewById<View>(R.id.btnBackPrep).setOnClickListener { finish() }

        findViewById<MaterialCardView>(R.id.cardCaptureReceipt).setOnClickListener {
            if (btnSave.isEnabled) receiptPhotoLauncher.launch(null)
        }

        findViewById<MaterialCardView>(R.id.cardCaptureRawMaterial).setOnClickListener {
            if (btnSave.isEnabled) rawMaterialPhotoLauncher.launch(null)
        }

        loadDataAndStatus()

        btnSave.setOnClickListener {
            if (validateForm()) {
                processAndSaveData()
            }
        }
    }

    private fun displaySelectedImage(imageView: ImageView, bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        imageView.setPadding(0, 0, 0, 0)
        imageView.imageTintList = null 
        imageView.colorFilter = null
        imageView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun validateForm(): Boolean {
        if (bitmapReceipt == null || bitmapRawMaterial == null) {
            Toast.makeText(this, "Mohon lengkapi foto nota dan bahan!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etRawWeight.text.isNullOrEmpty()) {
            Toast.makeText(this, "Masukkan berat bahan baku!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etTotalCapital.text.isNullOrEmpty()) {
            Toast.makeText(this, "Masukkan total modal belanja!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!checkBoxes.all { it.isChecked }) {
            Toast.makeText(this, "Lengkapi semua checklist SOP!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 480
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        
        var newWidth = maxDim
        var newHeight = maxDim
        if (ratio > 1) {
            newHeight = (maxDim / ratio).toInt()
        } else {
            newWidth = (maxDim * ratio).toInt()
        }
        
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 40, baos)
        val byteArray = baos.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) { null }
    }

    private fun processAndSaveData() {
        btnSave.isEnabled = false
        btnSave.text = "MEMPROSES DATA..."

        val receiptBase64 = bitmapToBase64(bitmapReceipt!!)
        val rawBase64 = bitmapToBase64(bitmapRawMaterial!!)

        saveAllDataToFirestore(receiptBase64, rawBase64)
    }

    private fun saveAllDataToFirestore(receiptString: String, rawString: String) {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val weight = etRawWeight.text.toString()
        val capital = etTotalCapital.text.toString().toLongOrNull() ?: 0L
        
        val prepResults = checkBoxes.associate { resources.getResourceEntryName(it.id) to it.isChecked }

        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val vendorRef = documents.documents[0].reference

                val prepData = hashMapOf(
                    "receiptPhotoUrl" to receiptString, 
                    "rawMaterialPhotoUrl" to rawString,  
                    "rawMaterialWeight" to weight,
                    "totalCapital" to capital,
                    "morningPrepData" to prepResults,
                    "prepCompletedAt" to FieldValue.serverTimestamp(),
                    "statusPrep" to "COMPLETED",
                    "currentStep" to 3,
                    "statusProduksi" to "SEDANG DIMASAK"
                )

                vendorRef.collection("daily_schedules").document(today).set(prepData, SetOptions.merge()).addOnSuccessListener {
                    val rootUpdates = hashMapOf<String, Any>(
                        "currentStep" to 3,
                        "statusProduksi" to "SEDANG DIMASAK",
                        "rawMaterialWeight" to weight,
                        "totalCapital" to capital
                    )
                    vendorRef.update(rootUpdates).addOnSuccessListener {
                        Toast.makeText(this, "Audit Berhasil! Siap Memasak.", Toast.LENGTH_SHORT).show()
                        lockUI() 
                    }
                }
            }
        }
    }

    private fun loadDataAndStatus() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents[0]
                val stepInDb = doc.getLong("currentStep")?.toInt() ?: 2
                
                doc.reference.collection("daily_schedules").document(today).get().addOnSuccessListener { scheduleDoc ->
                    if (scheduleDoc.exists()) {
                        menuName = scheduleDoc.getString("menu")
                        targetPorsi = scheduleDoc.getLong("targetPorsi") ?: 0L

                        scheduleDoc.getString("receiptPhotoUrl")?.let { base64ToBitmap(it)?.let { bmp -> 
                            displaySelectedImage(ivReceiptPhoto, bmp); bitmapReceipt = bmp 
                        }}
                        scheduleDoc.getString("rawMaterialPhotoUrl")?.let { base64ToBitmap(it)?.let { bmp -> 
                            displaySelectedImage(ivRawMaterialPhoto, bmp); bitmapRawMaterial = bmp 
                        }}
                        
                        if (stepInDb > 2) {
                            etRawWeight.setText(scheduleDoc.getString("rawMaterialWeight") ?: "")
                            etTotalCapital.setText(scheduleDoc.getLong("totalCapital")?.toString() ?: "")
                            lockUI()
                        }
                    }
                }
            }
        }
    }

    private fun lockUI() {
        checkBoxes.forEach { it.isEnabled = false; it.isChecked = true }
        etRawWeight.isEnabled = false
        etTotalCapital.isEnabled = false
        findViewById<MaterialCardView>(R.id.cardCaptureReceipt).isClickable = false
        findViewById<MaterialCardView>(R.id.cardCaptureRawMaterial).isClickable = false
        
        btnSave.isEnabled = false
        btnSave.text = "DATA AUDIT TERKUNCI"
        btnSave.setTextColor(Color.WHITE)
        btnSave.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mbg_green_dark))
        btnSave.alpha = 0.8f

        tvStatus.text = "TERVERIFIKASI ✓"
    }
}

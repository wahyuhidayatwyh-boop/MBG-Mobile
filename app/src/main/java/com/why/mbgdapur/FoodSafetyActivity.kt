package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FoodSafetyActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var ivPhoto: ImageView
    private lateinit var etTemp: TextInputEditText
    private lateinit var tvWarning: TextView
    private lateinit var switchConfirm: SwitchMaterial
    private lateinit var btnSubmit: MaterialButton
    
    private var isPhotoTaken = false
    private var bitmapSample: Bitmap? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            bitmapSample = bitmap
            displaySelectedImage(bitmap)
            isPhotoTaken = true
            checkFormValidity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_food_safety)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        ivPhoto = findViewById(R.id.ivSamplePhoto)
        etTemp = findViewById(R.id.etChillerTemp)
        tvWarning = findViewById(R.id.tvTempWarning)
        switchConfirm = findViewById(R.id.switchConfirmSafety)
        btnSubmit = findViewById(R.id.btnSubmitSafety)

        findViewById<ImageView>(R.id.btnBackSafety).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnTakePhoto).setOnClickListener {
            takePhotoLauncher.launch(null)
        }

        switchConfirm.setOnCheckedChangeListener { _, _ -> checkFormValidity() }

        btnSubmit.setOnClickListener {
            if (validateTemp()) {
                saveSafetyData()
            }
        }

        loadCurrentStatus()
    }

    private fun displaySelectedImage(bitmap: Bitmap) {
        ivPhoto.setImageBitmap(bitmap)
        ivPhoto.setPadding(0, 0, 0, 0)
        ivPhoto.imageTintList = null 
        ivPhoto.colorFilter = null
        ivPhoto.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun validateTemp(): Boolean {
        val tempStr = etTemp.text.toString()
        if (tempStr.isEmpty()) {
            Toast.makeText(this, "Input suhu chiller!", Toast.LENGTH_SHORT).show()
            return false
        }
        val temp = tempStr.toDoubleOrNull() ?: 0.0
        if (temp > 5.0) {
            tvWarning.visibility = View.VISIBLE
            tvWarning.text = "PERINGATAN: Suhu > 5°C tidak standar! Pastikan Chiller dingin."
            return true 
        } else {
            tvWarning.visibility = View.GONE
        }
        return true
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val byteArray = baos.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveSafetyData() {
        btnSubmit.isEnabled = false
        btnSubmit.text = "MENYIMPAN..."

        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val temp = etTemp.text.toString()
        val photoBase64 = bitmapSample?.let { bitmapToBase64(it) } ?: ""

        db.collection("vendors").whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val vendorRef = documents.documents[0].reference
                    val scheduleRef = vendorRef.collection("daily_schedules").document(today)

                    // PERBAIKAN: Set currentStep ke 6 agar "Serah Terima Kurir" TERBUKA
                    val safetyData = mapOf(
                        "samplePhotoUrl" to photoBase64,
                        "chillerTemp" to temp,
                        "safetyConfirmed" to true,
                        "safetyVerifiedAt" to FieldValue.serverTimestamp(),
                        "currentStep" to 6,
                        "statusProduksi" to "SIAP DISTRIBUSI"
                    )

                    vendorRef.update(safetyData)
                    scheduleRef.update(safetyData).addOnSuccessListener {
                        Toast.makeText(this, "Prosedur Keamanan Pangan Selesai!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
    }

    private fun checkFormValidity() {
        btnSubmit.isEnabled = isPhotoTaken && switchConfirm.isChecked && etTemp.text?.isNotEmpty() == true
        btnSubmit.alpha = if (btnSubmit.isEnabled) 1.0f else 0.5f
    }

    private fun loadCurrentStatus() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val vendorDoc = documents.documents[0]
                    
                    vendorDoc.reference.collection("daily_schedules").document(today).get()
                        .addOnSuccessListener { scheduleDoc ->
                            if (scheduleDoc.exists()) {
                                val step = scheduleDoc.getLong("currentStep")?.toInt() ?: 0
                                val photoBase64 = scheduleDoc.getString("samplePhotoUrl")
                                photoBase64?.let { base64ToBitmap(it)?.let { bmp -> 
                                    displaySelectedImage(bmp)
                                    bitmapSample = bmp
                                    isPhotoTaken = true
                                }}
                                etTemp.setText(scheduleDoc.getString("chillerTemp") ?: "")
                                
                                // Jika langkah sudah mencapai 6 atau lebih, kunci UI
                                if (step >= 6) {
                                    lockUI()
                                }
                            }
                        }
                }
            }
    }

    private fun lockUI() {
        etTemp.isEnabled = false
        switchConfirm.isEnabled = false
        switchConfirm.isChecked = true
        findViewById<MaterialButton>(R.id.btnTakePhoto).visibility = View.GONE
        btnSubmit.isEnabled = false
        btnSubmit.text = "SAMPEL TERSIMPAN ✓"
        tvWarning.text = "Data tersimpan permanen sebagai bukti audit."
        tvWarning.visibility = View.VISIBLE
    }
}

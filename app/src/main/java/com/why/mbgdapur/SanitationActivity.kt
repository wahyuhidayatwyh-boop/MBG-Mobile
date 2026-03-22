package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SanitationActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etReturnCount: android.widget.EditText
    private lateinit var etReason: android.widget.EditText
    private lateinit var tilReturnReason: TextInputLayout
    private lateinit var btnSave: MaterialButton
    private lateinit var btnPhotoKitchen: MaterialButton
    private lateinit var ivPhotoKitchenPreview: ImageView
    private lateinit var checkBoxes: List<CheckBox>
    
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var isPhotoTaken = false
    private var photoBase64: String = ""
    private var targetBoxes = 0
    private var actualVendorId: String? = null
    private var currentStepFromDb = 0

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            isPhotoTaken = true
            photoBase64 = encodeBitmapToBase64(bitmap)
            ivPhotoKitchenPreview.setImageBitmap(bitmap)
            findViewById<View>(R.id.cardPhotoKitchenPreview).visibility = View.VISIBLE
            ivPhotoKitchenPreview.visibility = View.VISIBLE
            Toast.makeText(this, "Foto Dapur Berhasil Diambil!", Toast.LENGTH_SHORT).show()
            btnPhotoKitchen.text = "FOTO TERSIMPAN ✔"
            btnPhotoKitchen.setIconResource(android.R.drawable.checkbox_on_background)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sanitation)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvStatus = findViewById(R.id.tvSanitationStatus)
        etReturnCount = findViewById(R.id.etReturnCount)
        etReason = findViewById(R.id.etReturnReason)
        tilReturnReason = findViewById(R.id.tilReturnReason)
        btnSave = findViewById(R.id.btnSaveSanitation)
        btnPhotoKitchen = findViewById(R.id.btnPhotoKitchen)
        ivPhotoKitchenPreview = findViewById(R.id.ivPhotoKitchenPreview)

        checkBoxes = listOf(
            findViewById(R.id.cbCookingTools),
            findViewById(R.id.cbPrepTables),
            findViewById(R.id.cbFloorClean),
            findViewById(R.id.cbBoxesWashed),
            findViewById(R.id.cbBoxesStored),
            findViewById(R.id.cbWasteOut),
            findViewById(R.id.cbBinClean)
        )

        findViewById<ImageView>(R.id.btnBackSanitation).setOnClickListener { finish() }

        fetchVendorAndTarget()

        btnPhotoKitchen.setOnClickListener {
            try {
                takePhotoLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        etReturnCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val countValue = s.toString().toIntOrNull() ?: 0
                if (countValue < targetBoxes && s.toString().isNotEmpty()) {
                    tilReturnReason.visibility = View.VISIBLE
                } else {
                    tilReturnReason.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSave.setOnClickListener {
            saveToFirestore()
        }
    }

    private fun fetchVendorAndTarget() {
        val userEmail = auth.currentUser?.email ?: "dapur@mbg.com"
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorDoc = docs.documents[0]
                actualVendorId = vendorDoc.id
                
                vendorDoc.reference.collection("daily_schedules").document(today).get()
                    .addOnSuccessListener { scheduleDoc ->
                        if (scheduleDoc.exists()) {
                            targetBoxes = scheduleDoc.getLong("targetPorsi")?.toInt() ?: 0
                            currentStepFromDb = scheduleDoc.getLong("currentStep")?.toInt() ?: 0
                            
                            // Try loading photo from root document first (new format)
                            val photoUrl = scheduleDoc.getString("sanitasiPhotoUrl") ?: scheduleDoc.getString("sanitasiPhotoBase64")
                            if (!photoUrl.isNullOrEmpty()) {
                                photoBase64 = photoUrl
                                isPhotoTaken = true
                                val bitmap = decodeBase64(photoUrl)
                                if (bitmap != null) {
                                    ivPhotoKitchenPreview.setImageBitmap(bitmap)
                                    findViewById<View>(R.id.cardPhotoKitchenPreview).visibility = View.VISIBLE
                                    ivPhotoKitchenPreview.visibility = View.VISIBLE
                                    btnPhotoKitchen.text = "FOTO TERSIMPAN ✔"
                                }
                            }

                            scheduleDoc.reference.collection("sanitasi_info").document("automatic").get()
                                .addOnSuccessListener { sanDoc ->
                                    if (sanDoc.exists() && sanDoc.getString("status") == "SELESAI") {
                                        displayReadOnlyData(sanDoc.data)
                                    }
                                }
                        }
                    }
            }
        }
    }

    private fun displayReadOnlyData(data: Map<String, Any>?) {
        if (data == null) return

        tvStatus.text = "SELESAI & TERVERIFIKASI"
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStatusHeader)?.setCardBackgroundColor(
            ContextCompat.getColor(this, R.color.mbg_green_dark)
        )
        tvStatus.setTextColor(Color.WHITE)
        
        etReturnCount.setText(data["boxesReturned"].toString())
        etReturnCount.isEnabled = false
        
        val reason = data["returnReason"] as? String
        if (!reason.isNullOrEmpty()) {
            etReason.setText(reason)
            tilReturnReason.visibility = View.VISIBLE
        }
        etReason.isEnabled = false

        checkBoxes[0].isChecked = data["isCookingToolsClean"] as? Boolean ?: false
        checkBoxes[1].isChecked = data["isPrepTablesClean"] as? Boolean ?: false
        checkBoxes[2].isChecked = data["isFloorClean"] as? Boolean ?: false
        checkBoxes[3].isChecked = data["isBoxesWashed"] as? Boolean ?: false
        checkBoxes[4].isChecked = data["isBoxesStored"] as? Boolean ?: false
        checkBoxes[5].isChecked = data["isWasteOut"] as? Boolean ?: false
        checkBoxes[6].isChecked = data["isBinClean"] as? Boolean ?: false

        checkBoxes.forEach { it.isEnabled = false }

        // photoBase64 should already be loaded from fetchVendorAndTarget or from here
        val base64Photo = data["photoProofBase64"] as? String ?: photoBase64
        if (!base64Photo.isNullOrEmpty()) {
            val bitmap = decodeBase64(base64Photo)
            ivPhotoKitchenPreview.setImageBitmap(bitmap)
            findViewById<View>(R.id.cardPhotoKitchenPreview).visibility = View.VISIBLE
            ivPhotoKitchenPreview.visibility = View.VISIBLE
        }

        btnPhotoKitchen.visibility = View.GONE
        btnSave.visibility = View.GONE
    }

    private fun saveToFirestore() {
        val vId = actualVendorId ?: return
        if (!isPhotoTaken) {
            Toast.makeText(this, "Harap ambil foto bukti kebersihan dulu!", Toast.LENGTH_SHORT).show()
            return
        }

        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val userEmail = auth.currentUser?.email ?: "dapur@mbg.com"
        
        val isAllChecked = checkBoxes.all { it.isChecked }
        val boxesReturned = etReturnCount.text.toString().toIntOrNull() ?: 0

        val sanitationData = hashMapOf(
            "status" to if (isAllChecked) "SELESAI" else "DRAFT",
            "boxesReturned" to boxesReturned,
            "returnReason" to if (boxesReturned < targetBoxes) etReason.text.toString() else "",
            "photoProofBase64" to photoBase64,
            "isCookingToolsClean" to checkBoxes[0].isChecked,
            "isPrepTablesClean" to checkBoxes[1].isChecked,
            "isFloorClean" to checkBoxes[2].isChecked,
            "isBoxesWashed" to checkBoxes[3].isChecked,
            "isBoxesStored" to checkBoxes[4].isChecked,
            "isWasteOut" to checkBoxes[5].isChecked,
            "isBinClean" to checkBoxes[6].isChecked,
            "updatedAt" to FieldValue.serverTimestamp(),
            "submittedBy" to userEmail
        )

        btnSave.isEnabled = false
        btnSave.text = "MENYIMPAN..."

        val vendorRef = db.collection("vendors").document(vId)
        val scheduleRef = vendorRef.collection("daily_schedules").document(today)
        
        val batch = db.batch()
        batch.set(scheduleRef.collection("sanitasi_info").document("automatic"), sanitationData)
        
        // Simpan juga di level dokumen jadwal (menggunakan field baru sesuai request)
        batch.update(scheduleRef, "sanitasiPhotoUrl", photoBase64)
        batch.update(scheduleRef, "sanitasiPhotoBase64", photoBase64)

        if (isAllChecked && currentStepFromDb >= 8) {
            val dashboardUpdates = mapOf(
                "currentStep" to 9,
                "statusProduksi" to "LAPORAN AKHIR"
            )
            batch.update(vendorRef, dashboardUpdates)
            batch.update(scheduleRef, dashboardUpdates)
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Data Sanitasi Berhasil Tersimpan!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                btnSave.text = "SIMPAN PROGRESS"
                Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun decodeBase64(base64String: String): Bitmap? {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
}

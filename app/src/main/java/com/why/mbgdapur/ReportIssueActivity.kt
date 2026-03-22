package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportIssueActivity : AppCompatActivity() {

    private lateinit var ivPhoto: ImageView
    private lateinit var etDescription: EditText
    private lateinit var btnSubmit: MaterialButton
    
    private var isPhotoTaken = false
    private var selectedCategoryCard: MaterialCardView? = null
    private var photoBase64: String = ""

    private val firestore = FirebaseFirestore.getInstance()
    private val vendorId = "F5DGGLr93pySLzMe6std"

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            ivPhoto.setImageBitmap(bitmap)
            ivPhoto.setPadding(0, 0, 0, 0)
            ivPhoto.imageTintList = null 
            isPhotoTaken = true
            photoBase64 = encodeImageToBase64(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_issue)

        ivPhoto = findViewById(R.id.ivIssuePhoto)
        etDescription = findViewById(R.id.etIssueDescription)
        btnSubmit = findViewById(R.id.btnSubmitIssue)
        val btnBack = findViewById<ImageView>(R.id.btnBackIssue)
        val btnAttach = findViewById<MaterialCardView>(R.id.btnAttachPhoto)

        val cardEquipment = findViewById<MaterialCardView>(R.id.cardCatEquipment)
        val cardLogistics = findViewById<MaterialCardView>(R.id.cardCatLogistics)
        val cardPersonnel = findViewById<MaterialCardView>(R.id.cardCatPersonnel)
        val cardPower = findViewById<MaterialCardView>(R.id.cardCatPower)

        val categoryCards = listOf(cardEquipment, cardLogistics, cardPersonnel, cardPower)

        categoryCards.forEach { card ->
            card.setOnClickListener { selectCategory(card, categoryCards) }
        }

        btnBack.setOnClickListener { finish() }

        btnAttach.setOnClickListener {
            try {
                takePhotoLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera tidak dapat dibuka", Toast.LENGTH_SHORT).show()
            }
        }

        btnSubmit.setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun selectCategory(selected: MaterialCardView, allCards: List<MaterialCardView>) {
        allCards.forEach { it.isChecked = false }
        selected.isChecked = true
        selectedCategoryCard = selected
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun validateAndSubmit() {
        val description = etDescription.text.toString().trim()

        if (selectedCategoryCard == null) {
            Toast.makeText(this, "Pilih kategori kendala terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (description.isEmpty()) {
            Toast.makeText(this, "Harap isi deskripsi kendala", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isPhotoTaken) {
            Toast.makeText(this, "Harap lampirkan foto bukti", Toast.LENGTH_SHORT).show()
            return
        }

        showModernConfirmationDialog()
    }

    private fun showModernConfirmationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_report, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)

        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)

        btnCancel.setOnClickListener { alertDialog.dismiss() }
        btnConfirm.setOnClickListener {
            alertDialog.dismiss()
            saveReportToFirestore()
        }

        alertDialog.show()
    }

    private fun saveReportToFirestore() {
        btnSubmit.isEnabled = false
        btnSubmit.text = "SEDANG MENGIRIM..."

        val categoryName = when (selectedCategoryCard?.id) {
            R.id.cardCatEquipment -> "Alat Rusak"
            R.id.cardCatLogistics -> "Bahan Baku"
            R.id.cardCatPersonnel -> "Tim/SDM"
            R.id.cardCatPower -> "Utilitas"
            else -> "Lainnya"
        }

        val reportDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        val reportData = hashMapOf(
            "category" to categoryName,
            "description" to etDescription.text.toString().trim(),
            "image_data" to photoBase64,
            "report_date" to reportDate,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "PENDING",
            "priority" to "HIGH",
            "vendor_id" to vendorId,
            "reported_by" to "Admin Dapur MBG"
        )

        firestore.collection("vendors")
            .document(vendorId)
            .collection("report_vendor")
            .add(reportData)
            .addOnSuccessListener {
                Toast.makeText(this, "Laporan Berhasil Terkirim!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnSubmit.isEnabled = true
                btnSubmit.text = "KIRIM LAPORAN SEKARANG"
                Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

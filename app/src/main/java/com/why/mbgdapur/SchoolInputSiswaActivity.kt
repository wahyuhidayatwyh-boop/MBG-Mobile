package com.why.mbgdapur

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SchoolInputSiswaActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var totalSiswa = 0
    private var porsi13 = 0
    private var porsi46 = 0
    private var schoolLevel = "SD"
    private var schoolDocId: String? = null
    
    private var evidenceBase64: String? = null
    private var evidenceName: String? = null
    
    private lateinit var tvCount: TextView
    private lateinit var tvCount13: TextView
    private lateinit var tvCount46: TextView
    private lateinit var tvSchoolName: TextView
    private lateinit var containerGrades: LinearLayout

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { handleSelectedFile(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_input_siswa)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvCount = findViewById(R.id.tvStudentCount)
        tvCount13 = findViewById(R.id.tvCount13)
        tvCount46 = findViewById(R.id.tvCount46)
        tvSchoolName = findViewById(R.id.tvSchoolNameDisplay)
        containerGrades = findViewById(R.id.containerGradesInput)

        setupUI()
        loadSchoolInfoAndConfig()
    }

    private fun setupUI() {
        findViewById<View>(R.id.btnBackInputSiswa).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnPlus13).setOnClickListener { porsi13++; updateUI() }
        findViewById<MaterialButton>(R.id.btnMinus13).setOnClickListener { if (porsi13 > 0) porsi13--; updateUI() }

        findViewById<MaterialButton>(R.id.btnPlus46).setOnClickListener { porsi46++; updateUI() }
        findViewById<MaterialButton>(R.id.btnMinus46).setOnClickListener { if (porsi46 > 0) porsi46--; updateUI() }

        findViewById<MaterialButton>(R.id.btnPlusStudent).setOnClickListener { totalSiswa++; updateUI() }
        findViewById<MaterialButton>(R.id.btnMinusStudent).setOnClickListener { if (totalSiswa > 0) totalSiswa--; updateUI() }

        findViewById<MaterialCardView>(R.id.btnUploadEvidence).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" } // Fokuskan ke image agar bisa dikompres
            filePickerLauncher.launch(intent)
        }

        findViewById<MaterialButton>(R.id.btnSaveStudentCount).setOnClickListener {
            saveStudentData()
        }
    }

    private fun updateUI() {
        if (schoolLevel.uppercase(Locale.getDefault()) == "SD" || schoolLevel.uppercase(Locale.getDefault()) == "MI") {
            totalSiswa = porsi13 + porsi46
            tvCount13.text = porsi13.toString()
            tvCount46.text = porsi46.toString()
        }
        tvCount.text = totalSiswa.toString()
    }

    private fun loadSchoolInfoAndConfig() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        db.collection("schools").whereEqualTo("email", userEmail).limit(1).get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val doc = querySnapshot.documents[0]
                    schoolDocId = doc.id
                    tvSchoolName.text = doc.getString("name") ?: "Sekolah MBG"
                    schoolLevel = doc.getString("level") ?: "SD"
                    
                    if (schoolLevel.uppercase(Locale.getDefault()) == "SD" || schoolLevel.uppercase(Locale.getDefault()) == "MI") {
                        containerGrades.visibility = View.VISIBLE
                        porsi13 = doc.getLong("count_grade_1_3")?.toInt() ?: 0
                        porsi46 = doc.getLong("count_grade_4_6")?.toInt() ?: 0
                        findViewById<View>(R.id.btnPlusStudent).visibility = View.GONE
                        findViewById<View>(R.id.btnMinusStudent).visibility = View.GONE
                    } else {
                        containerGrades.visibility = View.GONE
                        totalSiswa = doc.getLong("lastStudentCount")?.toInt() ?: 0
                    }
                    updateUI()
                }
            }
    }

    private fun handleSelectedFile(uri: Uri) {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                evidenceName = cursor.getString(nameIndex)
            }

            // KOMPRESI GAMBAR: Penting agar tidak OutOfMemory di Firestore
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            
            // Resize jika terlalu besar (maks 1024px)
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val targetWidth = 800
            val targetHeight = (targetWidth / ratio).toInt()
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

            val baos = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos) // Kompres kualitas ke 60%
            val bytes = baos.toByteArray()
            
            if (bytes.size > 800000) { // Jika masih > 800KB, peringatkan
                Toast.makeText(this, "Ukuran file terlalu besar, gunakan foto lain.", Toast.LENGTH_LONG).show()
                return
            }

            evidenceBase64 = Base64.encodeToString(bytes, Base64.DEFAULT)
            findViewById<TextView>(R.id.tvEvidenceFileName).text = evidenceName
            Toast.makeText(this, "Bukti berhasil diproses (${bytes.size / 1024} KB)", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memproses file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveStudentData() {
        if (schoolDocId == null) return
        if (evidenceBase64 == null) {
            Toast.makeText(this, "Mohon unggah bukti data siswa!", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSave = findViewById<MaterialButton>(R.id.btnSaveStudentCount)
        btnSave.isEnabled = false
        btnSave.text = "MENYIMPAN..."

        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        var c13 = porsi13.toLong()
        var c46 = porsi46.toLong()
        
        if (schoolLevel.uppercase() !in listOf("SD", "MI")) {
            if (schoolLevel.uppercase() in listOf("PAUD", "TK", "RA")) {
                c13 = totalSiswa.toLong(); c46 = 0
            } else {
                c13 = 0; c46 = totalSiswa.toLong()
            }
        }

        val data = hashMapOf(
            "jumlahSiswa" to totalSiswa,
            "count_grade_1_3" to c13,
            "count_grade_4_6" to c46,
            "level" to schoolLevel,
            "evidenceText" to evidenceBase64,
            "timestamp" to System.currentTimeMillis(),
            "date" to today,
            "schoolEmail" to userEmail
        )

        val batch = db.batch()
        val schoolRef = db.collection("schools").document(schoolDocId!!)
        batch.set(schoolRef.collection("student_input_history").document(), data)
        batch.set(schoolRef, hashMapOf(
            "lastStudentCount" to totalSiswa,
            "count_grade_1_3" to c13,
            "count_grade_4_6" to c46,
            "lastUpdate" to System.currentTimeMillis()
        ), SetOptions.merge())

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Data Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            btnSave.isEnabled = true
            btnSave.text = "SIMPAN DATA & AUDIT"
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

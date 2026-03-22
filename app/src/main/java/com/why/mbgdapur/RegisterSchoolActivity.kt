package com.why.mbgdapur

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.ActivityRegisterSchoolBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class RegisterSchoolActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterSchoolBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    private var photoSuratAktif: String = ""
    private var photoStempel: String = ""
    private var photoFotoSekolah: String = ""
    private var photoSK: String = ""

    private var currentUploadType = ""

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val base64 = encodeImage(it)
                updatePhotoStatus(currentUploadType, base64)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val base64 = if (bitmap != null) encodeImage(bitmap) else Base64.encodeToString(bytes, Base64.DEFAULT)
                    updatePhotoStatus(currentUploadType, base64)
                }
                inputStream?.close()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memproses file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterSchoolBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupSpinners()
        binding.btnToolbarBack.setOnClickListener { finish() }
        binding.btnGetLocation.setOnClickListener {
            latitude = -7.450000
            longitude = 109.250000
            binding.btnGetLocation.text = "LOKASI TERKUNCI ($latitude, $longitude)"
        }

        binding.btnUploadSuratAktif.setOnClickListener { showModernUploadDialog("SURAT_AKTIF") }
        binding.btnUploadStempel.setOnClickListener { showModernUploadDialog("STEMPEL") }
        binding.btnUploadFotoSekolah.setOnClickListener { showModernUploadDialog("FOTO_SEKOLAH") }
        binding.btnUploadSK.setOnClickListener { showModernUploadDialog("SK") }

        binding.btnRegister.setOnClickListener { registerSchool() }
    }

    private fun setupSpinners() {
        val levels = arrayOf("SD", "MI", "SMP", "MTS", "SMA", "SMK", "MA", "PAUD", "TK", "RA")
        binding.spinnerLevel.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, levels))
        binding.spinnerStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, arrayOf("NEGERI", "SWASTA")))
    }

    private fun showModernUploadDialog(type: String) {
        currentUploadType = type
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_upload_source, null)
        
        view.findViewById<View>(R.id.btnSourceCamera).setOnClickListener {
            cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btnSourceGallery).setOnClickListener {
            galleryLauncher.launch("*/*")
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun updatePhotoStatus(type: String, base64: String) {
        val checkIcon = R.drawable.ic_check_green
        when (type) {
            "SURAT_AKTIF" -> { photoSuratAktif = base64; binding.ivCheckSuratAktif.setImageResource(checkIcon); binding.ivCheckSuratAktif.imageTintList = null }
            "STEMPEL" -> { photoStempel = base64; binding.ivCheckStempel.setImageResource(checkIcon); binding.ivCheckStempel.imageTintList = null }
            "FOTO_SEKOLAH" -> { photoFotoSekolah = base64; binding.ivCheckFotoSekolah.setImageResource(checkIcon); binding.ivCheckFotoSekolah.imageTintList = null }
            "SK" -> { photoSK = base64; binding.ivCheckSK.setImageResource(checkIcon); binding.ivCheckSK.imageTintList = null }
        }
    }

    private fun registerSchool() {
        val name = binding.etSchoolName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || photoSuratAktif.isEmpty() || photoFotoSekolah.isEmpty()) {
            Toast.makeText(this, "Lengkapi data & berkas utama!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) saveSchoolData(auth.currentUser?.uid ?: "", email, name)
            else {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSchoolData(uid: String, email: String, name: String) {
        val g13 = binding.etGrade13.text.toString().toIntOrNull() ?: 0
        val g46 = binding.etGrade46.text.toString().toIntOrNull() ?: 0
        val gSenior = binding.etGradeSenior.text.toString().toIntOrNull() ?: 0
        
        val schoolData = hashMapOf(
            "schoolId" to "SCH" + System.currentTimeMillis().toString().takeLast(4),
            "name" to name,
            "level" to binding.spinnerLevel.text.toString().uppercase(),
            "address" to binding.etAddress.text.toString().trim(),
            "email" to email,
            "nip" to binding.etNPSN.text.toString().trim(),
            "phone" to binding.etPicPhone.text.toString().trim(),
            "latitude" to latitude,
            "longitude" to longitude,
            "count_grade_1_3" to g13,
            "count_grade_4_6" to g46,
            "lastStudentCount" to (g13 + g46 + gSenior),
            "lastUpdate" to System.currentTimeMillis(),
            "npsn" to binding.etNPSN.text.toString().trim(),
            "status" to binding.spinnerStatus.text.toString(),
            "location" to binding.etAddress.text.toString().trim(),
            "p13" to g13.toLong(),
            "p46" to g46.toLong(),
            "pSMP" to gSenior.toLong(),
            "schoolStart" to binding.etSchoolStart.text.toString().trim(),
            "breakTime" to binding.etBreakTime.text.toString().trim(),
            "activeDays" to binding.etActiveDays.text.toString().trim(),
            "dropOffPoint" to binding.etDropOffPoint.text.toString().trim(),
            "picName" to binding.etPicName.text.toString().trim(),
            "picPhone" to binding.etPicPhone.text.toString().trim(),
            "picPosition" to binding.etPicPosition.text.toString().trim(),
            "isApproved" to false,
            "photoSuratAktif" to photoSuratAktif,
            "photoStempel" to photoStempel,
            "photoFotoSekolah" to photoFotoSekolah,
            "photoSK" to photoSK,
            "assignedDriverId" to "",
            "assignedVendorEmail" to "",
            "createdAt" to System.currentTimeMillis()
        )

        val batch = db.batch()
        batch.set(db.collection("users").document(uid), hashMapOf("uid" to uid, "email" to email, "role" to "school", "isApproved" to false))
        batch.set(db.collection("schools").document(email), schoolData)
        batch.commit().addOnSuccessListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun encodeImage(bm: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 30, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }
}

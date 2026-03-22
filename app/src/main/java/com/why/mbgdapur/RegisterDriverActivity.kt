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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.ActivityRegisterDriverBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class RegisterDriverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterDriverBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var photoSIM: String = ""
    private var photoSTNK: String = ""
    private var photoVehicle: String = ""
    private var photoSKCK: String = ""

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
        binding = ActivityRegisterDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupSpinners()
        binding.btnToolbarBack.setOnClickListener { finish() }

        binding.btnUploadSIM.setOnClickListener { showModernUploadDialog("SIM") }
        binding.btnUploadSTNK.setOnClickListener { showModernUploadDialog("STNK") }
        binding.btnUploadFotoKendaraan.setOnClickListener { showModernUploadDialog("VEHICLE") }
        binding.btnUploadSKCK.setOnClickListener { showModernUploadDialog("SKCK") }

        binding.btnRegister.setOnClickListener { registerDriver() }
    }

    private fun setupSpinners() {
        val types = arrayOf("Motor", "Mobil (Blindvan)", "Mobil (Pickup)", "Mobil (Box)", "Lainnya")
        binding.spinnerVehicleType.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types))
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
            galleryLauncher.launch("image/*")
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun updatePhotoStatus(type: String, base64: String) {
        val checkIcon = R.drawable.ic_check_green
        when (type) {
            "SIM" -> { photoSIM = base64; binding.ivCheckSIM.setImageResource(checkIcon); binding.ivCheckSIM.imageTintList = null }
            "STNK" -> { photoSTNK = base64; binding.ivCheckSTNK.setImageResource(checkIcon); binding.ivCheckSTNK.imageTintList = null }
            "VEHICLE" -> { photoVehicle = base64; binding.ivCheckFotoKendaraan.setImageResource(checkIcon); binding.ivCheckFotoKendaraan.imageTintList = null }
            "SKCK" -> { photoSKCK = base64; binding.ivCheckSKCK.setImageResource(checkIcon); binding.ivCheckSKCK.imageTintList = null }
        }
    }

    private fun registerDriver() {
        val name = binding.etDriverName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val nik = binding.etNIK.text.toString().trim()
        var phone = binding.etDriverPhone.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || nik.isEmpty() || phone.isEmpty() || 
            photoSIM.isEmpty() || photoSTNK.isEmpty() || photoVehicle.isEmpty()) {
            Toast.makeText(this, "Lengkapi data & berkas utama!", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Memastikan format nomor HP adalah 62...
        if (phone.startsWith("0")) {
            phone = "62" + phone.substring(1)
        } else if (!phone.startsWith("62")) {
            phone = "62" + phone
        }

        binding.progressBar.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) saveDriverData(auth.currentUser?.uid ?: "", email, name, phone)
            else {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveDriverData(uid: String, email: String, name: String, phone: String) {
        val driverData = hashMapOf(
            "driverId" to "DRV" + System.currentTimeMillis().toString().takeLast(4),
            "name" to name,
            "nik" to binding.etNIK.text.toString().trim(),
            "phone" to phone,
            "address" to binding.etDriverAddress.text.toString().trim(),
            "email" to email,
            "vehicleType" to binding.spinnerVehicleType.text.toString(),
            "vehiclePlate" to binding.etPlateNumber.text.toString().trim(),
            "carryingCapacity" to binding.etCapacity.text.toString().trim(),
            "coverageArea" to binding.etCoverageArea.text.toString().trim(),
            "workingHours" to binding.etWorkingHours.text.toString().trim(),
            "isGpsConsent" to binding.switchGpsConsent.isChecked,
            "isApproved" to false,
            "photoSIM" to photoSIM,
            "photoSTNK" to photoSTNK,
            "photoVehicle" to photoVehicle,
            "photoSKCK" to photoSKCK,
            "rating" to "0.0",
            "averageRating" to "0.0",
            "slaScore" to "0%",
            "statusDelivery" to "WAITING",
            "statusHandover" to "WAITING",
            "statusShift" to "WAITING",
            "currentIssueType" to "NONE",
            "assignedVendorEmail" to "",
            "createdAt" to System.currentTimeMillis(),
            "lastUpdate" to System.currentTimeMillis()
        )

        val batch = db.batch()
        batch.set(db.collection("users").document(uid), hashMapOf("uid" to uid, "email" to email, "role" to "driver", "isApproved" to false))
        batch.set(db.collection("drivers").document(email), driverData)
        batch.commit().addOnSuccessListener {
            auth.signOut()
            Toast.makeText(this, "Pendaftaran berhasil, tunggu persetujuan admin.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }.addOnFailureListener {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Gagal menyimpan data: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encodeImage(bm: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 30, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }
}

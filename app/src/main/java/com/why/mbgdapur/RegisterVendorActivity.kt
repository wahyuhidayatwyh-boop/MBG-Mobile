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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.ActivityRegisterVendorBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class RegisterVendorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterVendorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    
    private var photoHalal: String = ""
    private var photoPIRT: String = ""
    private var photoHygiene: String = ""
    private var photoIzinOps: String = ""
    private var photoDapur: String = ""

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
        binding = ActivityRegisterVendorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnToolbarBack.setOnClickListener { finish() }
        binding.btnGetLocation.setOnClickListener {
            latitude = -7.450000
            longitude = 109.250000
            binding.btnGetLocation.text = "LOKASI TERKUNCI ($latitude, $longitude)"
        }

        binding.btnUploadHalal.setOnClickListener { showModernUploadDialog("HALAL") }
        binding.btnUploadPIRT.setOnClickListener { showModernUploadDialog("PIRT") }
        binding.btnUploadHygiene.setOnClickListener { showModernUploadDialog("HYGIENE") }
        binding.btnUploadIzinOps.setOnClickListener { showModernUploadDialog("IZIN_OPS") }
        binding.btnUploadFotoDapur.setOnClickListener { showModernUploadDialog("DAPUR") }

        binding.btnRegister.setOnClickListener { registerVendor() }
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
            "HALAL" -> { photoHalal = base64; binding.ivCheckHalal.setImageResource(checkIcon); binding.ivCheckHalal.imageTintList = null }
            "PIRT" -> { photoPIRT = base64; binding.ivCheckPIRT.setImageResource(checkIcon); binding.ivCheckPIRT.imageTintList = null }
            "HYGIENE" -> { photoHygiene = base64; binding.ivCheckHygiene.setImageResource(checkIcon); binding.ivCheckHygiene.imageTintList = null }
            "IZIN_OPS" -> { photoIzinOps = base64; binding.ivCheckIzinOps.setImageResource(checkIcon); binding.ivCheckIzinOps.imageTintList = null }
            "DAPUR" -> { photoDapur = base64; binding.ivCheckFotoDapur.setImageResource(checkIcon); binding.ivCheckFotoDapur.imageTintList = null }
        }
    }

    private fun registerVendor() {
        val name = binding.etVendorName.text.toString().trim()
        val owner = binding.etOwnerName.text.toString().trim()
        val nib = binding.etNIB.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()

        if (name.isEmpty() || owner.isEmpty() || nib.isEmpty() || email.isEmpty() || password.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Lengkapi identitas utama!", Toast.LENGTH_SHORT).show()
            return
        }

        if (photoPIRT.isEmpty() || photoIzinOps.isEmpty() || photoDapur.isEmpty()) {
            Toast.makeText(this, "PIRT, Izin Ops, & Foto Dapur Wajib!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!binding.cbGizi.isChecked || !binding.cbSLA.isChecked || !binding.cbAudit.isChecked) {
            Toast.makeText(this, "Setujui seluruh komitmen!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                saveVendorData(auth.currentUser?.uid ?: "", email, name, owner, nib)
            } else {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveVendorData(uid: String, email: String, name: String, owner: String, nib: String) {
        // Generate Vendor ID otomatis
        val generatedVendorId = "VND-${System.currentTimeMillis().toString().takeLast(5)}"
        
        val vendorData = hashMapOf<String, Any>(
            "vendorId" to generatedVendorId,
            "name" to name,
            "ownerName" to owner,
            "nib" to nib,
            "npwp" to binding.etNPWP.text.toString().trim(),
            "address" to binding.etAddress.text.toString().trim(),
            "location" to binding.etAddress.text.toString().trim(),
            "email" to email,
            "latitude" to latitude,
            "longitude" to longitude,
            "targetPorsi" to (binding.etTargetPorsi.text.toString().toIntOrNull() ?: 0),
            "totalCapital" to (binding.etTotalCapital.text.toString().toLongOrNull() ?: 0L),
            
            // Database consistency (Sesuai Screenshot)
            "statusProduksi" to "MENUNGGU VERIFIKASI",
            "averageRating" to "0.0", // String sesuai screenshot
            "complianceScore" to "0%",
            "cookedCount" to 0,
            "currentStep" to 0,
            "aiVerified" to false,
            "safetyVerified" to false,
            "isManualSubmit" to false,
            "chillerTemp" to "",
            "safetyTemp" to "",
            "menuPhotoBase64" to "",
            "rawMaterialWeight" to "",
            "safetyConfirmed" to false,
            "samplePhotoUrl" to photoDapur,
            "isApproved" to false,
            "lastUpdate" to System.currentTimeMillis(),
            
            // B. PERIZINAN & SERTIFIKASI (Flat structure)
            "hasHalal" to binding.switchHalal.isChecked,
            "photoHalal" to photoHalal,
            "photoPIRT" to photoPIRT,
            "photoHygiene" to photoHygiene,
            "photoIzinOps" to photoIzinOps,
            "photoDapur" to photoDapur,
            
            // C. KAPASITAS & OPERASIONAL
            "employeeCount" to (binding.etEmployeeCount.text.toString().toIntOrNull() ?: 0),
            "operatingHours" to binding.etOperatingHours.text.toString().trim(),
            "menuTypes" to binding.etMenuTypes.text.toString().trim(),
            
            // D. COMPLIANCE AWAL
            "setujuGizi" to binding.cbGizi.isChecked,
            "setujuSLA" to binding.cbSLA.isChecked,
            "setujuAudit" to binding.cbAudit.isChecked
        )

        val batch = db.batch()
        batch.set(db.collection("users").document(uid), hashMapOf(
            "uid" to uid, 
            "email" to email, 
            "role" to "vendor", 
            "isApproved" to false
        ))
        batch.set(db.collection("vendors").document(email), vendorData)
        
        batch.commit().addOnSuccessListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }.addOnFailureListener { e ->
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Gagal simpan database: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encodeImage(bm: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 30, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }
}

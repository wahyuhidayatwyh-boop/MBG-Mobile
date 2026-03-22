package com.why.mbgdapur

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverHelpBinding
import java.io.ByteArrayOutputStream

class DriverHelpActivity : AppCompatActivity() {

    private lateinit var binding: DriverHelpBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var selectedPhotoBase64: String? = null
    private var isFormVisible = false

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                binding.ivPreview.setImageBitmap(it)
                binding.ivPreview.visibility = View.VISIBLE
                binding.tvPhotoPlaceholder.visibility = View.GONE
                selectedPhotoBase64 = encodeImage(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupSpinner()
        setupActions()
        setupNavigation()
        listenToCurrentIssue()
    }

    private fun setupSpinner() {
        val items = arrayOf("Jalan Macet Total", "Kecelakaan", "Ban Bocor", "Pengalihan Rute", "Lainnya")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        binding.spinnerIssueType.setAdapter(adapter)
    }

    private fun listenToCurrentIssue() {
        val email = auth.currentUser?.email ?: return
        db.collection("drivers").document(email).addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                val issueType = snapshot.getString("currentIssueType") ?: "NONE"
                val issueMsg = snapshot.getString("currentIssueMsg") ?: ""
                val issuePhoto = snapshot.getString("currentIssuePhoto")

                if (issueType != "NONE") {
                    binding.cardActiveIssue.visibility = View.VISIBLE
                    binding.tvCurrentIssueMsg.text = "STATUS TERAKHIR: [$issueType]\n$issueMsg"
                    
                    if (!issuePhoto.isNullOrEmpty()) {
                        binding.ivCurrentIssuePhoto.visibility = View.VISIBLE
                        try {
                            val decodedString = Base64.decode(issuePhoto, Base64.DEFAULT)
                            val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                            binding.ivCurrentIssuePhoto.setImageBitmap(decodedByte)
                        } catch (e: Exception) {}
                    } else {
                        binding.ivCurrentIssuePhoto.visibility = View.GONE
                    }
                    // layoutMainButtons TIDAK LAGI DI-GONE agar bisa multi-laporan
                } else {
                    binding.cardActiveIssue.visibility = View.GONE
                }
            }
        }
    }

    private fun setupActions() {
        binding.btnIssueMogok.setOnClickListener {
            reportIssue("MOGOK", "Kendaraan Mogok / Masalah Mesin (Lapor Dapur)", null)
        }

        binding.btnIssueSekolah.setOnClickListener {
            isFormVisible = !isFormVisible
            binding.layoutSekolahForm.visibility = if (isFormVisible) View.VISIBLE else View.GONE
            binding.ivArrowSekolah.animate().rotation(if (isFormVisible) 180f else 0f).start()
        }

        binding.btnCapturePhoto.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePhotoLauncher.launch(takePictureIntent)
        }

        binding.btnSubmitSekolah.setOnClickListener {
            val type = binding.spinnerIssueType.text.toString()
            val desc = binding.etIssueDescription.text.toString()

            if (type == "Pilih Kategori Kendala") {
                Toast.makeText(this, "Pilih kategori kendala!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (desc.isEmpty()) {
                Toast.makeText(this, "Tuliskan keterangan kendala!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            reportIssue(type, desc, selectedPhotoBase64)
            resetForms() // Langsung reset agar bisa lapor lagi
        }

        binding.btnResolveIssue.setOnClickListener {
            resolveIssue()
        }
    }

    private fun reportIssue(type: String, msg: String, photo: String?) {
        val email = auth.currentUser?.email ?: return
        
        // Simpan setiap laporan sebagai dokumen baru di riwayat
        val reportData = hashMapOf(
            "category" to type,
            "description" to msg,
            "photoBase64" to photo,
            "timestamp" to FieldValue.serverTimestamp(),
            "status" to "AKTIF"
        )

        db.collection("drivers").document(email)
            .collection("report_driver")
            .add(reportData)
            .addOnSuccessListener {
                // Update status terkini di dokumen utama (Sekolah akan melihat laporan TERBARU)
                val updates = mapOf(
                    "currentIssueType" to type,
                    "currentIssueMsg" to msg,
                    "currentIssuePhoto" to photo,
                    "vendorActionStatus" to "Laporan diterima..."
                )
                db.collection("drivers").document(email).update(updates)
                
                Toast.makeText(this, "Laporan $type Berhasil Dikirim!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resolveIssue() {
        val email = auth.currentUser?.email ?: return
        val updates = mapOf(
            "currentIssueType" to "NONE",
            "currentIssueMsg" to "",
            "currentIssuePhoto" to null,
            "vendorActionStatus" to ""
        )
        db.collection("drivers").document(email).update(updates).addOnSuccessListener {
            Toast.makeText(this, "Semua kendala dinyatakan selesai!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetForms() {
        binding.spinnerIssueType.setText("Pilih Kategori Kendala", false)
        binding.etIssueDescription.setText("")
        binding.ivPreview.visibility = View.GONE
        binding.tvPhotoPlaceholder.visibility = View.VISIBLE
        selectedPhotoBase64 = null
        isFormVisible = false
        binding.layoutSekolahForm.visibility = View.GONE
        binding.ivArrowSekolah.rotation = 0f
    }

    private fun encodeImage(bm: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        val b = baos.toByteArray()
        return Base64.encodeToString(b, Base64.DEFAULT)
    }

    private fun setupNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.menu_help
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_dashboard -> {
                    startActivity(Intent(this, DriverDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.menu_help -> true
                R.id.menu_profile -> {
                    startActivity(Intent(this, DriverProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}

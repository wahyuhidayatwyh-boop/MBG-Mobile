package com.why.mbgdapur

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverProfileBinding
import java.io.ByteArrayOutputStream
import java.io.InputStream

class DriverProfileActivity : AppCompatActivity() {

    private lateinit var binding: DriverProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data?.data
            imageUri?.let { uri ->
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                        }
                    } else {
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    val correctedBitmap = rotateImageIfRequired(bitmap, uri)
                    val resizedBitmap = resizeBitmap(correctedBitmap, 600)
                    val base64String = bitmapToBase64(resizedBitmap)
                    updateProfilePhoto(base64String)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupBottomNavigation()
        loadDriverData()
        loadVehicleData()
        setupLogout()
        
        binding.cardHistory.setOnClickListener {
            startActivity(Intent(this, DriverHistoryActivity::class.java))
        }

        binding.cardVehicleInfo.setOnClickListener {
            startActivity(Intent(this, DriverVehicleDetailActivity::class.java))
        }

        binding.btnEditPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }
        
        binding.ivProfileImage.setOnClickListener {
            binding.btnEditPhoto.performClick()
        }
    }

    private fun rotateImageIfRequired(img: Bitmap, selectedImage: Uri): Bitmap {
        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(selectedImage)
            val ei = if (inputStream != null) {
                ExifInterface(inputStream)
            } else {
                return img
            }
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
                else -> img
            }
        } catch (e: Exception) {
            return img
        } finally {
            inputStream?.close()
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        if (rotatedImg != img) {
            img.recycle()
        }
        return rotatedImg
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun loadDriverData() {
        val email = auth.currentUser?.email ?: return
        db.collection("drivers").document(email)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    binding.tvDriverNameProfile.text = doc.getString("name") ?: "Mitra Driver"
                    binding.tvDriverIdProfile.text = "Mitra Kurir • ${doc.getString("driverId") ?: "ID"}"
                    val photoBase64 = doc.getString("profilephoto")
                    if (!photoBase64.isNullOrEmpty()) {
                        val bitmap = base64ToBitmap(photoBase64)
                        binding.ivProfileImage.setImageBitmap(bitmap)
                    }
                }
            }
    }

    private fun loadVehicleData() {
        val email = auth.currentUser?.email ?: return
        db.collection("drivers").document(email).collection("vehicle").document("info")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val plate = doc.getString("plateNumber") ?: "Belum Set"
                    val type = doc.getString("vehicleType") ?: "Kendaran"
                    binding.tvVehicleSummaryProfile.text = "$type ($plate)"
                } else {
                    binding.tvVehicleSummaryProfile.text = "Data kendaraan belum diisi"
                }
            }
    }

    private fun updateProfilePhoto(base64String: String) {
        val email = auth.currentUser?.email ?: return
        db.collection("drivers").document(email)
            .update("profilephoto", base64String)
            .addOnSuccessListener {
                Toast.makeText(this, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memperbarui foto", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
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

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirmLogout)
            val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelLogout)

            btnConfirm.setOnClickListener {
                dialog.dismiss()
                auth.signOut()
                // Update to global MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            btnCancel.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.menu_profile
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_dashboard -> {
                    startActivity(Intent(this, DriverDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.menu_help -> {
                    startActivity(Intent(this, DriverHelpActivity::class.java))
                    finish()
                    true
                }
                R.id.menu_profile -> true
                else -> false
            }
        }
    }
}

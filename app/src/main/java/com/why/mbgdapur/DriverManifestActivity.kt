package com.why.mbgdapur

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.why.mbgdapur.databinding.DriverManifestBinding
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DriverManifestActivity : AppCompatActivity() {

    private lateinit var binding: DriverManifestBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private var isPhotoTaken = false
    private var dX = 0f
    private var vendorEmail: String? = null
    private var totalBoksGlobal: Long = 0
    
    private var driverListener: ListenerRegistration? = null
    private var vendorListener: ListenerRegistration? = null
    private var scheduleListener: ListenerRegistration? = null
    private var destinationsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverManifestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadManifestData()
        setupPhotoCapture()
        setupSwipeInteraction()
        
        binding.layoutSwipeContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.mbg_green_dark))
        binding.swipeThumb.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun loadManifestData() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        driverListener = db.collection("drivers").document(userEmail).addSnapshotListener { driverDoc, _ ->
            if (driverDoc != null && driverDoc.exists()) {
                vendorEmail = driverDoc.getString("assignedVendorEmail")
                val vehicleInfo = driverDoc.getString("vehicle") ?: "Mobil Box"
                binding.tvVehicle.text = vehicleInfo
                
                vendorEmail?.let { email ->
                    vendorListener = db.collection("vendors").whereEqualTo("email", email).addSnapshotListener { vendorDocs, _ ->
                        if (vendorDocs != null && !vendorDocs.isEmpty) {
                            val vendorRef = vendorDocs.documents[0].reference
                            val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                            
                            scheduleListener = scheduleRef.addSnapshotListener { scheduleDoc, _ ->
                                if (scheduleDoc != null && scheduleDoc.exists()) {
                                    destinationsListener = scheduleRef.collection("destinations").addSnapshotListener { destinations, _ ->
                                        var totalKeseluruhan = 0L
                                        destinations?.forEach { dest ->
                                            val porsiRaw = dest.get("targetPorsi")
                                            totalKeseluruhan += when (porsiRaw) {
                                                is Number -> porsiRaw.toLong()
                                                is String -> porsiRaw.toLongOrNull() ?: 0L
                                                else -> 0L
                                            }
                                        }
                                        
                                        totalBoksGlobal = if (totalKeseluruhan > 0) totalKeseluruhan else scheduleDoc.getLong("targetPorsi") ?: 0
                                        binding.tvTotalManifest.text = "$totalBoksGlobal Boks"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        binding.tvTime.text = "$currentTime WIB"
    }

    private fun setupPhotoCapture() {
        val takePictureLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap
                binding.ivPhotoPreview.setImageBitmap(imageBitmap)
                binding.ivPhotoPreview.visibility = View.VISIBLE
                binding.layoutCaptureInstruction.visibility = View.GONE
                binding.cardPhotoSuccess.visibility = View.VISIBLE
                binding.cardCapturePhoto.strokeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
                
                isPhotoTaken = true
                unlockSwipeUI()
            }
        }

        binding.cardCapturePhoto.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                takePictureLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera tidak dapat dibuka", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unlockSwipeUI() {
        binding.layoutSwipeContainer.alpha = 1.0f
        binding.tvSwipeHint.text = "GESER UNTUK BERANGKAT"
        binding.tvSwipeHint.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeInteraction() {
        val thumb = binding.swipeThumb
        val container = binding.layoutSwipeContainer

        thumb.setOnTouchListener { v, event ->
            if (!isPhotoTaken) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(this, "Ambil foto bukti muat dulu!", Toast.LENGTH_SHORT).show()
                }
                return@setOnTouchListener false
            }

            container.post {
                val maxTranslation = container.width.toFloat() - v.width.toFloat() - 20f

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> dX = v.x - event.rawX
                    MotionEvent.ACTION_MOVE -> {
                        var newX = event.rawX + dX
                        if (newX < 10f) newX = 10f
                        if (newX > maxTranslation) newX = maxTranslation
                        v.x = newX
                    }
                    MotionEvent.ACTION_UP -> {
                        if (v.x > maxTranslation * 0.8) {
                            processAndSaveManifest()
                        } else {
                            v.animate().x(10f).setDuration(200).start()
                        }
                    }
                }
            }
            true
        }
    }

    private fun processAndSaveManifest() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        val drawable = binding.ivPhotoPreview.drawable as? BitmapDrawable
        if (drawable == null) {
            Toast.makeText(this, "Foto tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bitmap = drawable.bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos) 
        val byteArray = baos.toByteArray()
        val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)

        stopAllListeners()

        val batch = db.batch()
        val driverRef = db.collection("drivers").document(userEmail)
        
        val manifestLogRef = driverRef.collection("delivery_logs").document("${today}_MANIFEST")
        val manifestLogData = hashMapOf<String, Any>(
            "buktiMuatBase64" to base64Image,
            "waktuMuat" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(), // TAMBAHKAN INI
            "totalMuatan" to totalBoksGlobal,
            "vendorActionStatus" to "SIAP BERANGKAT"
        )
        batch.set(manifestLogRef, manifestLogData, SetOptions.merge())

        val globalUpdate = hashMapOf<String, Any>(
            "statusHandover" to "COMPLETED",
            "statusDelivery" to "READY",
            "currentIssueMsg" to "",
            "currentIssueType" to "NONE"
        )
        batch.update(driverRef, globalUpdate)

        Toast.makeText(this, "Menyimpan Manifest...", Toast.LENGTH_SHORT).show()

        batch.commit().addOnSuccessListener {
            onSwipeSuccess()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAllListeners() {
        driverListener?.remove()
        vendorListener?.remove()
        scheduleListener?.remove()
        destinationsListener?.remove()
    }

    private fun onSwipeSuccess() {
        val intent = Intent(this, DriverDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        stopAllListeners()
        super.onDestroy()
    }
}

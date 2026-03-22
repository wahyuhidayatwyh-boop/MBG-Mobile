package com.why.mbgdapur

import android.content.Intent
import android.content.res.ColorStateList
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.*

class CourierHandoverActivity : AppCompatActivity() {

    private lateinit var tvCourierName: TextView
    private lateinit var tvVehiclePlate: TextView
    private lateinit var ivCourierAvatar: ImageView
    private lateinit var btnSubmit: MaterialButton
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var cameraContainer: MaterialCardView

    private lateinit var tvDestinations: TextView
    private lateinit var tvTotalLoad: TextView
    private lateinit var tvDeadline: TextView
    private lateinit var etFinalCount: TextInputEditText

    private var driverEmail: String? = null
    private val schoolEmails = mutableListOf<String>()

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == 101) {
            val email = result.data?.getStringExtra("SCANNED_EMAIL")
            if (email != null) {
                this.driverEmail = email
                fetchDriverData(email)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_courier_handover)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvCourierName = findViewById(R.id.tvCourierName)
        tvVehiclePlate = findViewById(R.id.tvVehiclePlate)
        ivCourierAvatar = findViewById(R.id.ivCourierAvatar)
        btnSubmit = findViewById(R.id.btnSubmitHandover)
        cameraContainer = findViewById(R.id.btnScanCourier)
        
        tvDestinations = findViewById(R.id.tvDestinations)
        tvTotalLoad = findViewById(R.id.tvTotalLoad)
        tvDeadline = findViewById(R.id.tvDeadline)
        etFinalCount = findViewById(R.id.etFinalCount)
        
        findViewById<ImageView>(R.id.btnBackHandover).setOnClickListener { finish() }

        fetchScheduleData()

        cameraContainer.setOnClickListener {
            val intent = Intent(this, VendorWadahScanActivity::class.java)
            intent.putExtra("SCAN_MODE", "COURIER_VERIFICATION")
            scanLauncher.launch(intent)
        }

        btnSubmit.setOnClickListener {
            if (driverEmail == null) {
                Toast.makeText(this, "Mohon scan QR Kurir terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                saveHandoverData()
            }
        }
    }

    private fun fetchScheduleData() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val vendorRef = documents.documents[0].reference
                    val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                    
                    scheduleRef.get().addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            tvDeadline.text = doc.getString("deliveryDeadline") ?: "--:--"
                            val rootTarget = doc.getLong("targetPorsi") ?: 0L
                            tvTotalLoad.text = "$rootTarget Boks"
                            
                            // LOGIKA FIX: Cek apakah serah terima sudah dilakukan
                            val savedDriver = doc.getString("driverEmail")
                            if (!savedDriver.isNullOrEmpty()) {
                                this.driverEmail = savedDriver
                                fetchDriverData(savedDriver)
                                
                                val savedCount = doc.getLong("handoverCount")
                                if (savedCount != null) {
                                    etFinalCount.setText(savedCount.toString())
                                } else {
                                    etFinalCount.setText(rootTarget.toString())
                                }
                                
                                // Kunci UI agar tidak bisa scan/submit ulang
                                btnSubmit.isEnabled = false
                                btnSubmit.text = "SERAH TERIMA SELESAI"
                                btnSubmit.alpha = 0.6f
                                cameraContainer.isEnabled = false
                                cameraContainer.alpha = 0.5f
                                etFinalCount.isEnabled = false
                            } else {
                                etFinalCount.setText(rootTarget.toString())
                            }

                            scheduleRef.collection("destinations").get().addOnSuccessListener { destinations ->
                                val schoolNames = mutableListOf<String>()
                                schoolEmails.clear()
                                destinations?.forEach { dest ->
                                    dest.getString("schoolName")?.let { schoolNames.add(it) }
                                    schoolEmails.add(dest.id) 
                                }
                                if (schoolNames.isNotEmpty()) tvDestinations.text = schoolNames.joinToString(" & ")
                            }
                        }
                    }
                }
            }
    }

    private fun fetchDriverData(email: String) {
        db.collection("drivers").document(email).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: "Kurir Terverifikasi"
                    val vehicle = doc.getString("vehicle") ?: "Unit Logistik MBG"
                    val photoBase64 = doc.getString("profilephoto")
                    
                    tvCourierName.text = name.uppercase()
                    tvVehiclePlate.text = vehicle

                    if (!photoBase64.isNullOrEmpty()) {
                        val bitmap = base64ToBitmap(photoBase64)
                        ivCourierAvatar.setImageBitmap(bitmap)
                        ivCourierAvatar.setPadding(0, 0, 0, 0)
                        ivCourierAvatar.backgroundTintList = null
                    } else {
                        ivCourierAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                        ivCourierAvatar.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                        ivCourierAvatar.setPadding(10, 10, 10, 10)
                    }
                }
            }
    }

    private fun saveHandoverData() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val finalCount = etFinalCount.text.toString().toIntOrNull() ?: 0

        val batch = db.batch()
        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorRef = docs.documents[0].reference

                val handoverData = hashMapOf(
                    "handoverCount" to finalCount,
                    "driverEmail" to driverEmail,
                    "handoverTime" to com.google.firebase.Timestamp.now(),
                    "statusProduksi" to "DALAM PENGIRIMAN",
                    "currentStep" to 7
                )
                
                batch.set(vendorRef.collection("daily_schedules").document(today), handoverData, SetOptions.merge())
                batch.update(vendorRef, mapOf(
                    "statusProduksi" to "DALAM PENGIRIMAN",
                    "currentStep" to 7
                ))

                val dEmail = driverEmail ?: return@addOnSuccessListener
                schoolEmails.forEachIndexed { index, sEmail ->
                    val logRef = db.collection("drivers").document(dEmail).collection("delivery_logs").document("${today}_$sEmail")
                    batch.set(logRef, hashMapOf("status" to "WAITING", "updatedAt" to FieldValue.serverTimestamp(), "stopKe" to (index + 1)), SetOptions.merge())
                }
                
                batch.update(db.collection("drivers").document(dEmail), mapOf("statusHandover" to "VERIFIED", "statusDelivery" to "WAITING", "lastUpdate" to FieldValue.serverTimestamp()))

                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "Serah Terima Berhasil!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

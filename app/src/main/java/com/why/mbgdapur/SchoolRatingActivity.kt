package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.*

class SchoolRatingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var ratingBarVendor: RatingBar
    private lateinit var etReviewVendor: TextInputEditText
    private lateinit var ratingBarDriver: RatingBar
    private lateinit var etReviewDriver: TextInputEditText
    private lateinit var btnSubmit: MaterialButton
    
    private lateinit var cardVendor: MaterialCardView
    private lateinit var cardDriver: MaterialCardView

    private lateinit var ivVendorLogo: ImageView
    private lateinit var ivDriverPhoto: ImageView

    private var vendorDocId: String? = null
    private var currentDriverId: String? = null
    private var historyId: String? = null

    private var isAlreadyRated = false
    private var savedVendorRating = 0f
    private var savedVendorReview = ""
    private var savedDriverRating = 0f
    private var savedDriverReview = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_rating)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        ratingBarVendor = findViewById(R.id.ratingBarVendor)
        etReviewVendor = findViewById(R.id.etReviewVendor)
        ratingBarDriver = findViewById(R.id.ratingBarDriver)
        etReviewDriver = findViewById(R.id.etReviewDriver)
        btnSubmit = findViewById(R.id.btnSubmitAllRatings)
        
        cardVendor = findViewById(R.id.cardVendorRating)
        cardDriver = findViewById(R.id.cardDriverRating)

        ivVendorLogo = findViewById(R.id.ivVendorLogo)
        ivDriverPhoto = findViewById(R.id.ivDriverPhotoRating)

        // Ambil ID History (Tanggal), jika tidak ada gunakan tanggal hari ini
        historyId = intent.getStringExtra("HISTORY_ID") ?: SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        vendorDocId = intent.getStringExtra("VENDOR_ID")

        findViewById<View>(R.id.btnBackRating).setOnClickListener { finish() }

        checkIfAlreadyRated()

        btnSubmit.setOnClickListener { submitAllRatings() }
    }

    private fun checkIfAlreadyRated() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        val hId = historyId ?: return
        
        // Buat ID dokumen tetap: Tanggal_EmailSekolah
        val customDocId = "${hId}_${userEmail.replace(".", "_")}"

        isAlreadyRated = false
        savedVendorRating = 0f
        savedVendorReview = ""
        savedDriverRating = 0f
        savedDriverReview = ""

        // Cek langsung berdasarkan ID Dokumen (Tanpa Query agar lebih akurat)
        db.collection("vendor_ratings").document(customDocId).get(Source.SERVER).addOnSuccessListener { vDoc ->
            if (vDoc.exists()) {
                isAlreadyRated = true
                savedVendorRating = vDoc.getDouble("rating")?.toFloat() ?: 0f
                savedVendorReview = vDoc.getString("review") ?: ""

                db.collection("driver_ratings").document(customDocId).get(Source.SERVER).addOnSuccessListener { dDoc ->
                    if (dDoc.exists()) {
                        savedDriverRating = dDoc.getDouble("rating")?.toFloat() ?: 0f
                        savedDriverReview = dDoc.getString("review") ?: ""
                    }
                    loadBasicInfo()
                }.addOnFailureListener { loadBasicInfo() }
            } else {
                isAlreadyRated = false
                loadBasicInfo()
            }
        }.addOnFailureListener { 
            isAlreadyRated = false
            loadBasicInfo() 
        }
    }

    private fun loadBasicInfo() {
        val dateId = historyId ?: return

        vendorDocId?.let { vId ->
            db.collection("vendors").document(vId).get().addOnSuccessListener { vDoc ->
                if (vDoc.exists()) {
                    val vName = vDoc.getString("name") ?: "Nama Dapur"
                    findViewById<TextView>(R.id.tvTargetVendorName).text = if (isAlreadyRated) "$vName (Sudah Dinilai)" else vName
                    ivVendorLogo.setImageResource(R.drawable.logo_mbg)

                    vDoc.reference.collection("history").document(dateId).get().addOnSuccessListener { hDoc ->
                        if (hDoc.exists() && hDoc.contains("driverEmail")) {
                            currentDriverId = hDoc.getString("driverEmail")
                            fetchDriverData(currentDriverId)
                        } else {
                            vDoc.reference.collection("daily_schedules").document(dateId).get().addOnSuccessListener { sDoc ->
                                currentDriverId = sDoc.getString("driverEmail")
                                fetchDriverData(currentDriverId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fetchDriverData(driverId: String?) {
        if (driverId.isNullOrEmpty()) {
            cardDriver.visibility = View.GONE
            if (isAlreadyRated) applyReadOnlyMode()
            else resetToInputMode()
            return
        }

        cardDriver.visibility = View.VISIBLE
        db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
            if (dDoc.exists()) {
                findViewById<TextView>(R.id.tvTargetDriverName).text = dDoc.getString("name") ?: driverId
                val photo = dDoc.getString("profilephoto")
                if (!photo.isNullOrEmpty()) {
                    ivDriverPhoto.setImageBitmap(base64ToBitmap(photo))
                    ivDriverPhoto.imageTintList = null
                }
            } else {
                db.collection("drivers").whereEqualTo("email", driverId).get().addOnSuccessListener { dDocs ->
                    if (!dDocs.isEmpty) {
                        val doc = dDocs.documents[0]
                        findViewById<TextView>(R.id.tvTargetDriverName).text = doc.getString("name") ?: driverId
                        val photo = doc.getString("profilephoto")
                        if (!photo.isNullOrEmpty()) {
                            ivDriverPhoto.setImageBitmap(base64ToBitmap(photo))
                            ivDriverPhoto.imageTintList = null
                        }
                    } else {
                        findViewById<TextView>(R.id.tvTargetDriverName).text = driverId
                    }
                }
            }
            
            if (isAlreadyRated) applyReadOnlyMode()
            else resetToInputMode()
        }
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val cleanBase64 = if (base64Str.contains(",")) base64Str.split(",")[1] else base64Str
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) { null }
    }

    private fun applyReadOnlyMode() {
        ratingBarVendor.rating = savedVendorRating
        etReviewVendor.setText(savedVendorReview)
        ratingBarDriver.rating = savedDriverRating
        etReviewDriver.setText(savedDriverReview)

        ratingBarVendor.setIsIndicator(true)
        etReviewVendor.isEnabled = false
        etReviewVendor.isFocusable = false

        ratingBarDriver.setIsIndicator(true)
        etReviewDriver.isEnabled = false
        etReviewDriver.isFocusable = false

        btnSubmit.visibility = View.GONE
    }

    private fun resetToInputMode() {
        ratingBarVendor.setIsIndicator(false)
        etReviewVendor.isEnabled = true
        etReviewVendor.isFocusableInTouchMode = true
        
        ratingBarDriver.setIsIndicator(false)
        etReviewDriver.isEnabled = true
        etReviewDriver.isFocusableInTouchMode = true

        btnSubmit.visibility = View.VISIBLE
    }

    private fun submitAllRatings() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: ""
        val timestamp = System.currentTimeMillis()
        val hId = historyId ?: return
        
        // ID Dokumen: Tanggal_Email (Email titik diganti underscore)
        val customDocId = "${hId}_${userEmail.replace(".", "_")}"

        if (ratingBarVendor.rating == 0f) {
            Toast.makeText(this, "Harap beri rating kualitas menu", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Simpan/Update Penilaian Vendor
        if (vendorDocId != null) {
            val vendorData = mapOf(
                "targetId" to vendorDocId!!,
                "fromSchool" to userEmail,
                "rating" to ratingBarVendor.rating.toDouble(),
                "review" to etReviewVendor.text.toString(),
                "timestamp" to timestamp,
                "historyId" to hId
            )
            db.collection("vendor_ratings").document(customDocId).set(vendorData).addOnSuccessListener {
                updateAverageRating("vendors", vendorDocId!!, "vendor_ratings")
            }
        }

        // 2. Simpan/Update Penilaian Driver
        if (currentDriverId != null && ratingBarDriver.rating > 0) {
            val driverData = mapOf(
                "fromSchool" to userEmail,
                "rating" to ratingBarDriver.rating.toDouble(),
                "review" to etReviewDriver.text.toString(),
                "targetId" to currentDriverId!!,
                "timestamp" to timestamp,
                "historyId" to hId
            )
            db.collection("driver_ratings").document(customDocId).set(driverData).addOnSuccessListener {
                updateAverageRating("drivers", currentDriverId!!, "driver_ratings")
            }
        }

        Toast.makeText(this, "Penilaian berhasil disimpan!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateAverageRating(targetCollection: String, targetId: String, ratingsCollection: String) {
        db.collection(ratingsCollection)
            .whereEqualTo("targetId", targetId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    var totalRating = 0.0
                    val count = querySnapshot.size()
                    for (doc in querySnapshot) {
                        totalRating += doc.getDouble("rating") ?: 0.0
                    }
                    val average = totalRating / count
                    
                    // Update ke dokumen utama (Vendor atau Driver)
                    db.collection(targetCollection).document(targetId)
                        .update("averageRating", String.format(Locale.US, "%.1f", average))
                }
            }
    }
}

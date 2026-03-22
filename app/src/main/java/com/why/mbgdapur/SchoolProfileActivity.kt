package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SchoolProfileActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_profile)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupNavigation()
        loadSchoolProfile()

        findViewById<View>(R.id.btnLogoutSchool).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Konfirmasi Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Keluar") { _, _ ->
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun setupNavigation() {
        // Set menu Akun sebagai terpilih (agar ikon berubah menjadi _klik)
        findViewById<View>(R.id.navAkun).isSelected = true

        findViewById<View>(R.id.navBeranda).setOnClickListener {
            startActivity(Intent(this, SchoolDashboardActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navMenu).setOnClickListener {
            startActivity(Intent(this, SchoolScheduleListActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navHistory).setOnClickListener {
            startActivity(Intent(this, SchoolHistoryActivity::class.java))
            finish()
        }
    }

    private fun loadSchoolProfile() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return

        db.collection("schools").whereEqualTo("email", userEmail).limit(1).get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents[0]
                
                findViewById<TextView>(R.id.tvProfileSchoolNameHeader).text = doc.getString("name") ?: "Sekolah MBG"
                findViewById<TextView>(R.id.tvProfileAddress).text = doc.getString("address") ?: "Alamat belum diatur"
                findViewById<TextView>(R.id.tvProfileLevel).text = doc.getString("level") ?: "--"
                findViewById<TextView>(R.id.tvProfileEmail).text = doc.getString("email") ?: "--"
                
                findViewById<TextView>(R.id.tvProfileLat).text = doc.get("latitude")?.toString() ?: "0.0"
                findViewById<TextView>(R.id.tvProfileLng).text = doc.get("longitude")?.toString() ?: "0.0"
                
                val lastCount = doc.getLong("lastStudentCount") ?: 0L
                val countA = doc.getLong("count_grade_1_3") ?: 0L
                val countB = doc.getLong("count_grade_4_6") ?: 0L
                
                findViewById<TextView>(R.id.tvProfileTotalSiswa).text = "$lastCount Anak"
                findViewById<TextView>(R.id.tvProfileCountA).text = "$countA Anak"
                findViewById<TextView>(R.id.tvProfileCountB).text = "$countB Anak"

                val vendorEmail = doc.getString("assignedVendorEmail")
                if (vendorEmail != null) {
                    fetchVendorName(vendorEmail)
                }

                val driverId = doc.getString("assignedDriverId")
                if (driverId != null) {
                    fetchDriverName(driverId)
                }
            }
        }
    }

    private fun fetchVendorName(email: String) {
        db.collection("vendors").whereEqualTo("email", email).limit(1).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val name = docs.documents[0].getString("name") ?: email
                findViewById<TextView>(R.id.tvProfileVendor).text = "Vendor: $name"
            } else {
                findViewById<TextView>(R.id.tvProfileVendor).text = "Vendor: $email"
            }
        }
    }

    private fun fetchDriverName(driverId: String) {
        db.collection("drivers").document(driverId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: driverId
                findViewById<TextView>(R.id.tvProfileDriver).text = "Kurir: $name"
            } else {
                findViewById<TextView>(R.id.tvProfileDriver).text = "Kurir: $driverId"
            }
        }
    }
}

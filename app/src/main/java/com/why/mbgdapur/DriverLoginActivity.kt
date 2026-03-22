package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.why.mbgdapur.databinding.DriverLoginBinding

class DriverLoginActivity : AppCompatActivity() {

    private lateinit var binding: DriverLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Jika sudah login sebelumnya, langsung ke dashboard (Resume Mode)
        if (auth.currentUser != null) {
            startDriverDashboard()
        }

        setupLoginAction()
    }

    private fun setupLoginAction() {
        binding.btnLoginDriver.setOnClickListener {
            val driverIdInput = binding.etDriverId.text.toString().trim()
            val password = binding.etDriverPassword.text.toString().trim()

            if (driverIdInput.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "ID dan Password wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.pbLoginDriver.visibility = View.VISIBLE
            binding.btnLoginDriver.isEnabled = false

            db.collection("drivers")
                .whereEqualTo("driverId", driverIdInput)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val email = documents.documents[0].getString("email")
                        if (email != null) {
                            performFirebaseLogin(email, password)
                        } else {
                            handleError("Data email driver tidak ditemukan")
                        }
                    } else {
                        handleError("ID Driver tidak ditemukan")
                    }
                }
                .addOnFailureListener { e ->
                    handleError("Kesalahan Koneksi: ${e.message}")
                }
        }
    }

    private fun performFirebaseLogin(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // RESET STATUS DATABASE SAAT LOGIN BARU
                    resetDriverStatusAndStart(email)
                } else {
                    handleError("Kata Sandi Salah!")
                }
            }
    }

    private fun resetDriverStatusAndStart(email: String) {
        // Membersihkan status lama agar driver mulai dari Barcode Dapur
        val resetData = hashMapOf<String, Any>(
            "statusHandover" to "WAITING",
            "statusDelivery" to "WAITING",
            "currentNavigatingSchool" to "",
            "lastUpdate" to FieldValue.serverTimestamp()
        )
        
        db.collection("drivers").document(email).update(resetData)
            .addOnCompleteListener {
                startDriverDashboard()
            }
    }

    private fun handleError(msg: String) {
        binding.pbLoginDriver.visibility = View.GONE
        binding.btnLoginDriver.isEnabled = true
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun startDriverDashboard() {
        val intent = Intent(this, DriverDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

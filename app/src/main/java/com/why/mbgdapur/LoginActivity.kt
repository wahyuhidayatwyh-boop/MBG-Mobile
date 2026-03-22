package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.why.mbgdapur.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            checkUserAccessAndNavigate(auth.currentUser?.uid ?: "")
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan Password wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBarLogin.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        checkUserAccessAndNavigate(auth.currentUser?.uid ?: "")
                    } else {
                        binding.progressBarLogin.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this, "Login Gagal: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun checkUserAccessAndNavigate(uid: String) {
        binding.progressBarLogin.visibility = View.VISIBLE
        
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role") ?: ""
                    val email = document.getString("email") ?: ""
                    
                    // 1. Cek approval di koleksi "users"
                    val isApprovedInUsers = checkApprovedStatus(document)

                    // Pengecualian Pemerintah/Admin
                    if (role.lowercase() == "pemerintah" || role.lowercase() == "admin") {
                        proceedLogin(role, email)
                        return@addOnSuccessListener
                    }

                    if (isApprovedInUsers) {
                        proceedLogin(role, email)
                    } else {
                        // 2. Fallback: Cek ke koleksi spesifik (schools/vendors/drivers) jika di users masih false
                        val collectionName = when(role.lowercase()) {
                            "school" -> "schools"
                            "vendor" -> "vendors"
                            "driver" -> "drivers"
                            else -> null
                        }

                        if (collectionName != null) {
                            db.collection(collectionName).document(email).get()
                                .addOnSuccessListener { roleDoc ->
                                    if (roleDoc.exists() && checkApprovedStatus(roleDoc)) {
                                        proceedLogin(role, email)
                                    } else {
                                        rejectLogin()
                                    }
                                }
                                .addOnFailureListener { rejectLogin() }
                        } else {
                            rejectLogin()
                        }
                    }
                } else {
                    rejectLogin("Data pengguna tidak ditemukan")
                }
            }
            .addOnFailureListener { e ->
                binding.progressBarLogin.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                Toast.makeText(this, "Kesalahan sistem: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkApprovedStatus(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        val raw = doc.get("isApproved") ?: doc.get("approved") ?: false
        return when (raw) {
            is Boolean -> raw
            is String -> raw.lowercase() == "true"
            else -> false
        }
    }

    private fun proceedLogin(role: String, email: String) {
        if (role.lowercase() == "driver") {
            resetDriverStatusAndNavigate(email)
        } else {
            binding.progressBarLogin.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            navigateToDashboard(role)
        }
    }

    private fun rejectLogin(message: String = "Akun belum di-acc oleh Pemerintah") {
        binding.progressBarLogin.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        auth.signOut()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun resetDriverStatusAndNavigate(email: String) {
        val resetData = hashMapOf<String, Any>(
            "statusHandover" to "WAITING",
            "statusDelivery" to "WAITING",
            "lastUpdate" to FieldValue.serverTimestamp()
        )
        db.collection("drivers").document(email).update(resetData)
            .addOnCompleteListener {
                binding.progressBarLogin.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                navigateToDashboard("driver")
            }
    }

    private fun navigateToDashboard(role: String) {
        val intent = when (role.lowercase()) {
            "school" -> Intent(this, SchoolDashboardActivity::class.java)
            "vendor" -> Intent(this, DashboardActivity::class.java)
            "driver" -> Intent(this, DriverDashboardActivity::class.java)
            "pengawas", "pemerintah" -> Intent(this, DashboardActivity::class.java)
            else -> {
                Toast.makeText(this, "Role tidak dikenali: $role", Toast.LENGTH_SHORT).show()
                return
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

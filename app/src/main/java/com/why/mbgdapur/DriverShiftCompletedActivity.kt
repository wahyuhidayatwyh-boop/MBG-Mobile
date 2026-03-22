package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverShiftCompletedBinding
import java.text.SimpleDateFormat
import java.util.*

class DriverShiftCompletedActivity : AppCompatActivity() {

    private lateinit var binding: DriverShiftCompletedBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverShiftCompletedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        loadSummaryData()

        binding.btnFinishAll.setOnClickListener {
            resetDriverStatusAndFinish()
        }
    }

    private fun loadSummaryData() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        db.collection("drivers").document(userEmail)
            .collection("delivery_logs")
            .get()
            .addOnSuccessListener { logs ->
                var totalRetur = 0L
                logs.forEach { doc ->
                    if (doc.id.startsWith(today) && doc.getString("returStatus") == "DONE") {
                        totalRetur += doc.getLong("returBoxCount") ?: 0L
                    }
                }
                binding.tvTotalReturDone.text = "$totalRetur Boks"
            }
    }

    private fun resetDriverStatusAndFinish() {
        val userEmail = auth.currentUser?.email ?: return
        
        // Reset status di Firestore agar dashboard kembali ke kondisi default (kosong/awal)
        db.collection("drivers").document(userEmail).update(mapOf(
            "statusHandover" to "WAITING",
            "statusDelivery" to "WAITING",
            "currentNavigatingSchool" to "",
            "lastUpdate" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            val intent = Intent(this, DriverDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal mereset status, mengalihkan ke Dashboard...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DriverDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}

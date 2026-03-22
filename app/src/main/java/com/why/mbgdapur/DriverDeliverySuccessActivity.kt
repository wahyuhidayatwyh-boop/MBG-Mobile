package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverDeliverySuccessBinding
import java.text.SimpleDateFormat
import java.util.*

class DriverDeliverySuccessActivity : AppCompatActivity() {

    private lateinit var binding: DriverDeliverySuccessBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverDeliverySuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadDeliverySummary()

        binding.btnBackToDashboard.setOnClickListener {
            val intent = Intent(this, DriverDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtra("SHIFT_DONE", true) 
            startActivity(intent)
            finish()
        }
    }

    private fun loadDeliverySummary() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        val driverRef = db.collection("drivers").document(userEmail)
        
        // 1. Ambil Total Muatan dari MANIFEST
        driverRef.collection("delivery_logs").document("${today}_MANIFEST").get()
            .addOnSuccessListener { manifestDoc ->
                if (manifestDoc.exists()) {
                    val total = manifestDoc.getLong("totalMuatan") ?: 0
                    binding.tvSummaryBoxes.text = "$total Boks Berhasil Didrop"
                }
            }

        // 2. Ambil Ringkasan Sekolah yang Selesai
        driverRef.collection("delivery_logs")
            .whereEqualTo("status", "DONE")
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Filter manual untuk tanggal hari ini karena ID dokumen mengandung tanggal
                val todayDocs = querySnapshot.documents.filter { it.id.startsWith(today) }
                
                val count = todayDocs.size
                binding.tvSummarySchools.text = "$count Sekolah Terselesaikan"

                // 3. Ambil Waktu Selesai Terakhir
                if (todayDocs.isNotEmpty()) {
                    // Cari yang waktuSelesai-nya paling baru
                    val lastDone = todayDocs.maxByOrNull { it.getTimestamp("waktuSelesai")?.toDate()?.time ?: 0L }
                    val timestamp = lastDone?.getTimestamp("waktuSelesai")
                    
                    timestamp?.let {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        binding.tvCompletionTime.text = "${sdf.format(it.toDate())} WIB"
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat ringkasan", Toast.LENGTH_SHORT).show()
            }
    }
}

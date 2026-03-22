package com.why.mbgdapur

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class SchoolInputSisaActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var sisaCount = 0
    private var maxPorsi = 0
    private var selectedReason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_input_sisa)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupUI()
        loadMaxPorsi()
    }

    private fun setupUI() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val tvSisaCount = findViewById<TextView>(R.id.tvSisaCount)
        
        // 1. Counter Logic
        findViewById<MaterialButton>(R.id.btnMinus).setOnClickListener {
            if (sisaCount > 0) {
                sisaCount--
                tvSisaCount.text = sisaCount.toString()
            }
        }

        findViewById<MaterialButton>(R.id.btnPlus).setOnClickListener {
            if (sisaCount < maxPorsi) {
                sisaCount++
                tvSisaCount.text = sisaCount.toString()
            } else {
                Toast.makeText(this, "Tidak bisa melebihi target porsi ($maxPorsi)", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Reason Dropdown
        val reasons = arrayOf(
            "Siswa Sakit Mendadak (Hari-H)",
            "Siswa Izin / Pulang Lebih Awal (Hari-H)",
            "Siswa Alpa / Tanpa Keterangan"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, reasons)
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerReason)
        spinner.setAdapter(adapter)
        spinner.setOnItemClickListener { _, _, position, _ ->
            selectedReason = reasons[position]
        }

        // 5. Submit Button
        findViewById<Button>(R.id.btnSaveSisa).setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun loadMaxPorsi() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("schools").document(userEmail).get().addOnSuccessListener { schoolDoc ->
            val vendorEmail = schoolDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
            
            db.collection("vendors").whereEqualTo("email", vendorEmail).get().addOnSuccessListener { vendorDocs ->
                if (!vendorDocs.isEmpty) {
                    val vendorDoc = vendorDocs.documents[0]
                    vendorDoc.reference.collection("daily_schedules").document(today)
                        .collection("destinations").document(userEmail).get().addOnSuccessListener { destDoc ->
                            if (destDoc.exists()) {
                                maxPorsi = (destDoc.getLong("targetPorsi") ?: 0).toInt()
                                findViewById<TextView>(R.id.tvMaxPorsiInfo).text = "Maksimal: $maxPorsi Porsi"
                            }
                        }
                }
            }
        }
    }

    private fun validateAndSubmit() {
        if (sisaCount == 0) {
            Toast.makeText(this, "Jumlah sisa porsi harus lebih dari 0", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedReason == null) {
            Toast.makeText(this, "Pilih alasan ketidakhadiran!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val rgDistribution = findViewById<RadioGroup>(R.id.rgDistribution)
        val selectedRbId = rgDistribution.checkedRadioButtonId
        if (selectedRbId == -1) {
            Toast.makeText(this, "Pilih alokasi penyaluran makanan!", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedDistribution = findViewById<RadioButton>(selectedRbId).text.toString()
        val notes = findViewById<EditText>(R.id.etSisaNotes).text.toString()

        val userEmail = auth.currentUser?.email ?: return
        val timestamp = System.currentTimeMillis()

        db.collection("schools").document(userEmail).get().addOnSuccessListener { schoolDoc ->
            val vendorEmail = schoolDoc.getString("assignedVendorEmail") ?: ""

            val sisaReport = hashMapOf(
                "jumlahSisa" to sisaCount,
                "alasan" to selectedReason,
                "distribusi" to selectedDistribution,
                "catatan" to notes,
                "timestamp" to timestamp,
                "date" to SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()),
                "assignedVendorEmail" to vendorEmail
            )

            findViewById<Button>(R.id.btnSaveSisa).isEnabled = false
            findViewById<Button>(R.id.btnSaveSisa).text = "MENYIMPAN..."

            // Simpan ke sub-koleksi informasi_sisa
            db.collection("schools").document(userEmail)
                .collection("informasi_sisa")
                .add(sisaReport)
                .addOnSuccessListener {
                    Toast.makeText(this, "Laporan Sisa Berhasil Disimpan!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal menyimpan laporan", Toast.LENGTH_SHORT).show()
                    findViewById<Button>(R.id.btnSaveSisa).isEnabled = true
                    findViewById<Button>(R.id.btnSaveSisa).text = "SIMPAN LAPORAN SISA"
                }
        }
    }
}

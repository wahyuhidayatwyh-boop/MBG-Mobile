package com.why.mbgdapur

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class SchoolAllergyActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var etAllergyNotes: EditText
    private lateinit var tvLastUpdate: TextView
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_allergy)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Fix: Use btnBackAllergy instead of toolbarAllergy as it's not in the layout
        findViewById<MaterialCardView>(R.id.btnBackAllergy).setOnClickListener {
            finish()
        }

        etAllergyNotes = findViewById(R.id.etAllergyNotes)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        btnSave = findViewById(R.id.btnSaveAllergy)

        loadCurrentAllergyData()

        btnSave.setOnClickListener {
            saveAllergyData()
        }
    }

    private fun loadCurrentAllergyData() {
        val userEmail = auth.currentUser?.email ?: return
        // Ambil data terbaru dari sub-koleksi informasi_alergi
        db.collection("schools").document(userEmail)
            .collection("informasi_alergi")
            .orderBy("allergyLastUpdate", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val doc = docs.documents[0]
                    val notes = doc.getString("allergyNotes") ?: ""
                    etAllergyNotes.setText(notes)
                    
                    val lastUpdate = doc.getLong("allergyLastUpdate")
                    if (lastUpdate != null) {
                        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        tvLastUpdate.text = sdf.format(Date(lastUpdate))
                    }
                }
            }
    }

    private fun saveAllergyData() {
        val userEmail = auth.currentUser?.email ?: return
        val notes = etAllergyNotes.text.toString().trim()
        val timestamp = System.currentTimeMillis()

        btnSave.isEnabled = false
        btnSave.text = "MENYIMPAN..."

        // Ambil assignedVendorEmail dari profil sekolah untuk disertakan di dokumen alergi
        db.collection("schools").document(userEmail).get().addOnSuccessListener { schoolDoc ->
            val vendorEmail = schoolDoc.getString("assignedVendorEmail") ?: ""

            val allergyData = hashMapOf(
                "allergyNotes" to notes,
                "allergyLastUpdate" to timestamp,
                "assignedVendorEmail" to vendorEmail
            )

            // Simpan sebagai dokumen baru di sub-koleksi informasi_alergi
            db.collection("schools").document(userEmail)
                .collection("informasi_alergi")
                .add(allergyData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Data alergi berhasil disimpan", Toast.LENGTH_SHORT).show()
                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    tvLastUpdate.text = sdf.format(Date(timestamp))
                    btnSave.isEnabled = true
                    btnSave.text = "SIMPAN DATA ALERGI"
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                    btnSave.text = "SIMPAN DATA ALERGI"
                }
        }
    }
}

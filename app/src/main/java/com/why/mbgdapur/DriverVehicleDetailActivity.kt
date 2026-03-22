package com.why.mbgdapur

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverVehicleDetailBinding

class DriverVehicleDetailActivity : AppCompatActivity() {

    private lateinit var binding: DriverVehicleDetailBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverVehicleDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupVehicleTypeSpinner()
        loadVehicleData()

        binding.btnBackVehicle.setOnClickListener { finish() }

        binding.btnUpdateVehicle.setOnClickListener {
            updateVehicleData()
        }
    }

    private fun setupVehicleTypeSpinner() {
        val types = arrayOf("Mobil Box (Thermal)", "Mobil Box (Standar)", "Motor Box", "Mobil Pickup")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        binding.actvVehicleType.setAdapter(adapter)
    }

    private fun loadVehicleData() {
        val email = auth.currentUser?.email ?: return
        
        db.collection("drivers").document(email).collection("vehicle").document("info")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val plate = doc.getString("plateNumber") ?: ""
                    val type = doc.getString("vehicleType") ?: ""

                    binding.tvDisplayPlate.text = plate
                    binding.tvDisplayType.text = type
                    
                    binding.etPlateNumber.setText(plate)
                    binding.actvVehicleType.setText(type, false)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data kendaraan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateVehicleData() {
        val email = auth.currentUser?.email ?: return
        val plate = binding.etPlateNumber.text.toString().uppercase().trim()
        val type = binding.actvVehicleType.text.toString()

        if (plate.isEmpty()) {
            Toast.makeText(this, "Nomor pelat tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val vehicleData = hashMapOf(
            "plateNumber" to plate,
            "vehicleType" to type,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("drivers").document(email).collection("vehicle").document("info")
            .set(vehicleData)
            .addOnSuccessListener {
                binding.tvDisplayPlate.text = plate
                binding.tvDisplayType.text = type
                Toast.makeText(this, "Data kendaraan berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memperbarui data", Toast.LENGTH_SHORT).show()
            }
    }
}

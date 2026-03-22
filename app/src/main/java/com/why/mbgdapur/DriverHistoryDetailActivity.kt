package com.why.mbgdapur

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverHistoryDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class DriverHistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: DriverHistoryDetailBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.btnBackDetail.setOnClickListener { finish() }

        val logId = intent.getStringExtra("LOG_ID") ?: return
        val logType = intent.getStringExtra("LOG_TYPE")
        loadDetail(logId, logType)
    }

    private fun loadDetail(logId: String, logType: String?) {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("drivers").document(userEmail).collection("delivery_logs").document(logId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    binding.tvTitleHeader.text = "DETAIL $logType"

                    when (logType) {
                        "MANIFEST" -> {
                            binding.layoutManifestInfo.visibility = View.VISIBLE
                            binding.tvSchoolNameDetail.text = "OPERASI MANIFEST"
                            
                            val ts = doc.getTimestamp("waktuMuat") ?: doc.getTimestamp("updatedAt")
                            binding.tvDateDetail.text = ts?.toDate()?.let { 
                                SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale.getDefault()).format(it)
                            } ?: today

                            val total = doc.getLong("totalMuatan") ?: 0
                            binding.tvTotalMuatanDetail.text = "Total Muatan: $total Boks"

                            doc.getString("buktiMuatBase64")?.let { base64 ->
                                binding.ivManifestPhotoFull.setImageBitmap(decodeBase64(base64))
                            }
                        }
                        "DELIVERY" -> {
                            binding.layoutDeliveryInfo.visibility = View.VISIBLE
                            val schoolEmail = logId.substringAfter("_")
                            fetchSchoolName(userEmail, today, schoolEmail)

                            val ts = doc.getTimestamp("waktuSelesai") ?: doc.getTimestamp("updatedAt")
                            binding.tvDateDetail.text = ts?.toDate()?.let { 
                                SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale.getDefault()).format(it)
                            } ?: today

                            doc.getString("pod_foto")?.let { base64 ->
                                binding.ivPodPhoto.setImageBitmap(decodeBase64(base64))
                            }
                            doc.getString("pod_signature")?.let { base64 ->
                                binding.ivSignature.setImageBitmap(decodeBase64(base64))
                            }
                        }
                        "RETUR" -> {
                            binding.layoutReturInfo.visibility = View.VISIBLE
                            val schoolEmail = logId.substringAfter("_")
                            fetchSchoolName(userEmail, today, schoolEmail)

                            val ts = doc.getTimestamp("returWaktu") ?: doc.getTimestamp("updatedAt")
                            binding.tvDateDetail.text = ts?.toDate()?.let { 
                                SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale.getDefault()).format(it)
                            } ?: today

                            val boks = doc.getLong("returBoxCount") ?: 0
                            binding.tvReturBoxCount.text = "Total Boks Kotor: $boks"

                            // Cek field returFoto atau retur_foto (untuk fleksibilitas data lama/baru)
                            val fotoBase64 = doc.getString("returFoto") ?: doc.getString("retur_foto")
                            fotoBase64?.let { base64 ->
                                binding.ivReturPhoto.setImageBitmap(decodeBase64(base64))
                            }
                            
                            val signatureBase64 = doc.getString("returSignature") ?: doc.getString("retur_signature")
                            signatureBase64?.let { base64 ->
                                binding.ivReturSignature.setImageBitmap(decodeBase64(base64))
                            }
                        }
                    }
                }
            }
    }

    private fun fetchSchoolName(driverEmail: String, date: String, schoolEmail: String) {
        db.collection("drivers").document(driverEmail).get().addOnSuccessListener { driverDoc ->
            val vEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
            db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendors ->
                if (!vendors.isEmpty) {
                    vendors.documents[0].reference.collection("daily_schedules").document(date)
                        .collection("destinations").document(schoolEmail).get().addOnSuccessListener { dest ->
                            binding.tvSchoolNameDetail.text = dest.getString("schoolName") ?: schoolEmail.substringBefore("@").uppercase()
                        }
                } else {
                    binding.tvSchoolNameDetail.text = schoolEmail.substringBefore("@").uppercase()
                }
            }
        }
    }

    private fun decodeBase64(base64String: String): android.graphics.Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

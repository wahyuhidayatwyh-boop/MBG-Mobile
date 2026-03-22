package com.why.mbgdapur

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Source

class SchoolHistoryDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var tvMenuName: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTotalPorsi: TextView
    
    private lateinit var layoutGiziA: View
    private lateinit var ivGiziA: ImageView
    private lateinit var tvPorsiA: TextView
    
    private lateinit var layoutGiziB: View
    private lateinit var ivGiziB: ImageView
    private lateinit var tvPorsiB: TextView
    
    private lateinit var btnGoToRating: MaterialCardView
    private lateinit var tvRatingTitle: TextView
    private lateinit var tvRatingSubtitle: TextView
    
    private var currentVendorId: String? = null
    private var historyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_history_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvMenuName = findViewById(R.id.tvDetailMenuName)
        tvDate = findViewById(R.id.tvDetailDate)
        tvTotalPorsi = findViewById(R.id.tvDetailTotalPorsi)
        
        layoutGiziA = findViewById(R.id.layoutDetailGiziA)
        ivGiziA = findViewById(R.id.ivDetailGiziA)
        tvPorsiA = findViewById(R.id.tvDetailPorsiA)
        
        layoutGiziB = findViewById(R.id.layoutDetailGiziB)
        ivGiziB = findViewById(R.id.ivDetailGiziB)
        tvPorsiB = findViewById(R.id.tvDetailPorsiB)
        
        btnGoToRating = findViewById(R.id.btnGoToRating)
        tvRatingTitle = findViewById(R.id.tvRatingTitle)
        tvRatingSubtitle = findViewById(R.id.tvRatingSubtitle)

        historyId = intent.getStringExtra("HISTORY_ID")
        currentVendorId = intent.getStringExtra("VENDOR_ID")

        findViewById<View>(R.id.btnBackDetail).setOnClickListener { finish() }
        
        btnGoToRating.setOnClickListener {
            val intent = Intent(this, SchoolRatingActivity::class.java)
            intent.putExtra("HISTORY_ID", historyId)
            intent.putExtra("VENDOR_ID", currentVendorId)
            startActivity(intent)
        }

        if (historyId != null && currentVendorId != null) {
            loadDetailData()
            checkRatingStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        checkRatingStatus()
    }

    private fun checkRatingStatus() {
        val userEmail = auth.currentUser?.email?.lowercase()?.trim() ?: return
        val hId = historyId ?: return
        
        val customDocId = "${hId}_${userEmail.replace(".", "_")}"

        db.collection("vendor_ratings").document(customDocId).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    tvRatingTitle.text = "LIHAT PENILAIAN"
                    tvRatingSubtitle.text = "Pesanan ini sudah Anda nilai"
                } else {
                    tvRatingTitle.text = "BERI PENILAIAN"
                    tvRatingSubtitle.text = "Masukan Anda membantu kami"
                }
            }
    }

    private fun loadDetailData() {
        val userEmail = auth.currentUser?.email?.lowercase()?.trim() ?: return
        val vendorId = currentVendorId ?: return
        val hId = historyId ?: return
        
        val vendorRef = db.collection("vendors").document(vendorId)
        
        // Coba cari di daily_schedules dulu (untuk data hari ini yang baru saja DONE)
        vendorRef.collection("daily_schedules").document(hId).get(Source.SERVER).addOnSuccessListener { scheduleDoc ->
            if (scheduleDoc.exists()) {
                processMainDocument(scheduleDoc, userEmail)
            } else {
                // Jika tidak ada di schedule, cari di history
                vendorRef.collection("history").document(hId).get(Source.SERVER).addOnSuccessListener { historyDoc ->
                    if (historyDoc.exists()) {
                        processMainDocument(historyDoc, userEmail)
                    } else {
                        tvMenuName.text = "Data tidak ditemukan"
                    }
                }
            }
        }.addOnFailureListener {
            // Fallback ke history jika gagal akses schedule
            vendorRef.collection("history").document(hId).get(Source.SERVER).addOnSuccessListener { historyDoc ->
                if (historyDoc.exists()) processMainDocument(historyDoc, userEmail)
            }
        }
    }

    private fun processMainDocument(doc: com.google.firebase.firestore.DocumentSnapshot, userEmail: String) {
        val menuConfig = doc.get("menuConfig") as? Map<*, *>
        val menuA = (menuConfig?.get("CAT_13K") as? Map<*, *>)?.get("menuName")?.toString()
        val menuB = (menuConfig?.get("CAT_15K") as? Map<*, *>)?.get("menuName")?.toString()
        
        var menuUmum = doc.getString("nama_menu") ?: doc.getString("menu") ?: "Menu MBG"
        if (menuUmum.contains(",")) menuUmum = menuUmum.replace(",", " & ")

        doc.reference.collection("destinations").document(userEmail).get(Source.SERVER).addOnSuccessListener { destDoc ->
            if (destDoc.exists()) {
                val p13 = destDoc.getLong("p13") ?: 0L
                val p46 = destDoc.getLong("p46") ?: 0L
                val pSMP = destDoc.getLong("pSMP") ?: 0L
                val pSMA = destDoc.getLong("pSMA") ?: 0L
                val p3B = destDoc.getLong("p3B") ?: 0L
                
                val totalGiziB = p46 + pSMP + pSMA + p3B
                val totalPorsi = destDoc.getLong("targetPorsi") ?: (p13 + totalGiziB)
                
                tvTotalPorsi.text = "$totalPorsi Porsi"
                tvDate.text = historyId 

                tvMenuName.text = when {
                    p13 > 0 && totalGiziB > 0 -> menuUmum 
                    p13 > 0 -> menuA ?: menuUmum         
                    totalGiziB > 0 -> menuB ?: menuUmum
                    else -> menuUmum
                }

                val fallbackMainPhoto = doc.getString("menuPhotoUrl") ?: 
                                      doc.getString("menuPhotoBase64") ?: 
                                      doc.getString("foto_menu")

                if (p13 > 0) {
                    layoutGiziA.visibility = View.VISIBLE
                    tvPorsiA.text = "Jumlah: $p13 Porsi"
                    findViewById<TextView>(R.id.tvLabelDetailGiziA)?.text = "Rincian Gizi A: ${menuA ?: ""}"
                    fetchPhotoWithFallback(doc.reference, "photo_CAT_13K", ivGiziA, fallbackMainPhoto)
                } else {
                    layoutGiziA.visibility = View.GONE
                }

                if (totalGiziB > 0) {
                    layoutGiziB.visibility = View.VISIBLE
                    tvPorsiB.text = "Jumlah: $totalGiziB Porsi"
                    findViewById<TextView>(R.id.tvLabelDetailGiziB)?.text = "Rincian Gizi B: ${menuB ?: ""}"
                    fetchPhotoWithFallback(doc.reference, "photo_CAT_15K", ivGiziB, fallbackMainPhoto)
                } else {
                    layoutGiziB.visibility = View.GONE
                }
            }
        }
    }

    private fun fetchPhotoWithFallback(parentRef: DocumentReference, docName: String, imageView: ImageView, globalFallback: String?) {
        parentRef.collection("media").document(docName).get(Source.SERVER).addOnSuccessListener { mDoc ->
            val data = mDoc.getString("data") ?: mDoc.getString("imageUrl")
            if (data != null) {
                displayPhoto(data, imageView)
            } else if (globalFallback != null) {
                displayPhoto(globalFallback, imageView)
            }
        }
    }

    private fun displayPhoto(source: String, iv: ImageView) {
        if (source.startsWith("http")) {
            Glide.with(this)
                .load(source)
                .placeholder(R.drawable.logo_mbg)
                .error(R.drawable.logo_mbg)
                .into(iv)
        } else {
            try {
                val cleanBase64 = if (source.contains(",")) source.split(",")[1] else source
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            } catch (e: Exception) {
                iv.setImageResource(R.drawable.logo_mbg)
            }
        }
    }
}

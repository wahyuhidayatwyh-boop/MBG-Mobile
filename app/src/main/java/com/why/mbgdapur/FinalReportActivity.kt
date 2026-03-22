package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FinalReportActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var totalTargetPorsi: Long = 0
    private var totalModal: Long = 0
    private var actualVendorId: String? = null
    
    // Evidence Data Cache
    private var menuName: String? = null
    private var menuPhoto: String? = null
    private var menuConfigMap: Map<String, Any>? = null
    private var photoNota: String? = null
    private var photoBahan: String? = null
    private var photoAi: String? = null
    private var photoAi2: String? = null
    private var photoSafety: String? = null
    private var photoSanitasi: String? = null
    private var rawWeight: String? = null
    private var boxWeight: String? = null
    private var aiResult: String? = null
    private var safetyTemp: String? = null

    // Audit State
    private var totalPendapatan: Long = 0
    private val categoryDataList = mutableListOf<CategoryAuditData>()

    data class CategoryAuditData(
        val name: String,
        val porsi: Long,
        val harga: Int,
        var pendapatan: Long = 0,
        var modal: Long = 0,
        var margin: Double = 0.0,
        var status: String = ""
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "name" to name,
            "porsi" to porsi,
            "harga" to harga,
            "pendapatan" to pendapatan,
            "modal" to modal,
            "margin" to margin,
            "status" to status
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_final_report)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        findViewById<ImageView>(R.id.btnBackReport)?.setOnClickListener { finish() }

        val btnSubmit = findViewById<MaterialButton>(R.id.btnLockAndSubmit)
        btnSubmit?.setOnClickListener {
            if (totalModal <= 0) {
                Toast.makeText(this, "Total Modal Belanja belum diinput di Persiapan Pagi!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (totalTargetPorsi <= 0) {
                Toast.makeText(this, "Total Porsi tidak boleh nol!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showMinimalistConfirmationDialog()
        }

        loadReportData()
    }

    private fun loadReportData() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorDoc = docs.documents[0]
                actualVendorId = vendorDoc.id
                val vendorRef = vendorDoc.reference
                val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                
                scheduleRef.get().addOnSuccessListener { scheduleDoc ->
                    if (scheduleDoc.exists()) {
                        totalModal = scheduleDoc.getLong("totalCapital") ?: 0L
                        menuName = scheduleDoc.getString("menu")
                        menuPhoto = scheduleDoc.getString("menuPhotoBase64")
                        menuConfigMap = scheduleDoc.get("menuConfig") as? Map<String, Any>
                        
                        val currentStep = scheduleDoc.getLong("currentStep")?.toInt() ?: 0
                        
                        // Load Evidence
                        photoNota = scheduleDoc.getString("receiptPhotoUrl")
                        photoBahan = scheduleDoc.getString("rawMaterialPhotoUrl")
                        rawWeight = scheduleDoc.getString("rawMaterialWeight") ?: "--"
                        photoAi = scheduleDoc.getString("aiPhotoBase64")
                        photoAi2 = scheduleDoc.getString("aiPhoto2Base64")
                        boxWeight = scheduleDoc.getString("boxWeight") ?: "--"
                        aiResult = scheduleDoc.getString("aiResult") ?: "Lulus Verifikasi"
                        photoSafety = scheduleDoc.getString("samplePhotoUrl")
                        photoSanitasi = scheduleDoc.getString("sanitasiPhotoUrl") ?: scheduleDoc.getString("sanitasiPhotoBase64")
                        safetyTemp = scheduleDoc.getString("chillerTemp") ?: "--"

                        loadDynamicAuditData(scheduleRef, scheduleDoc)
                        updateComplianceUI()
                        if (currentStep >= 10) lockUIForReadOnly()
                    }
                }
            }
        }
    }

    private fun loadDynamicAuditData(scheduleRef: com.google.firebase.firestore.DocumentReference, scheduleDoc: com.google.firebase.firestore.DocumentSnapshot) {
        scheduleRef.collection("destinations").get().addOnSuccessListener { destinations ->
            val schoolLevelsA = mutableSetOf<String>()
            val schoolLevelsB = mutableSetOf<String>()
            
            destinations.forEach { d ->
                val level = d.getString("level") ?: "SD"
                val p13 = d.getLong("p13") ?: 0L
                val pOthers = (d.getLong("p46") ?: 0L) + (d.getLong("pSMP") ?: 0L) + 
                              (d.getLong("pSMA") ?: 0L) + (d.getLong("p3B") ?: 0L)
                
                if (p13 > 0) schoolLevelsA.add(level)
                if (pOthers > 0) schoolLevelsB.add(level)
            }

            categoryDataList.clear()
            val menuConfig = scheduleDoc.get("menuConfig") as? Map<String, Any>
            
            val configA = menuConfig?.get("CAT_13K") as? Map<String, Any>
            val porsiA = configA?.get("totalPorsi") as? Long ?: 0L
            if (porsiA > 0) {
                categoryDataList.add(CategoryAuditData(generateGiziLabel("CAT_13K", schoolLevelsA), porsiA, 13000))
            }

            val configB = menuConfig?.get("CAT_15K") as? Map<String, Any>
            val porsiB = configB?.get("totalPorsi") as? Long ?: 0L
            if (porsiB > 0) {
                categoryDataList.add(CategoryAuditData(generateGiziLabel("CAT_15K", schoolLevelsB), porsiB, 15000))
            }

            calculateAuditFinal()
        }
    }

    private fun generateGiziLabel(catId: String, levels: Set<String>): String {
        return if (catId == "CAT_13K") {
            val hasSD = levels.any { it == "SD" || it == "MI" }
            val hasKecil = levels.any { it == "PAUD" || it == "TK" || it == "RA" }
            when {
                hasSD && hasKecil -> "Gizi A (PAUD-Kelas 3)"
                hasKecil -> "Gizi A (PAUD/TK/RA)"
                else -> "Gizi A (SD Kelas 1-3)"
            }
        } else {
            val hasSD = levels.any { it == "SD" || it == "MI" }
            val hasSMP = levels.any { it == "SMP" || it == "MTS" }
            val hasSMA = levels.any { it == "SMA" || it == "SMK" || it == "MA" }
            val parts = mutableListOf<String>()
            if (hasSD) parts.add("Kelas 4-6")
            if (hasSMP) parts.add("SMP")
            if (hasSMA) parts.add("SMA")
            val range = if (parts.isEmpty()) "Standar" else parts.joinToString("/")
            "Gizi B ($range)"
        }
    }

    private fun calculateAuditFinal() {
        totalTargetPorsi = categoryDataList.sumOf { it.porsi }
        totalPendapatan = 0
        
        for (item in categoryDataList) {
            item.pendapatan = item.porsi * item.harga
            totalPendapatan += item.pendapatan
        }

        // Hybrid Modal Distribution System (Logical Baseline Weight)
        // 1. Calculate weighted proportions
        var totalWeightedProp = 0.0
        val itemDetails = mutableListOf<Pair<Double, Double>>() // propDasar, weight

        for (item in categoryDataList) {
            val propDasar = if (totalPendapatan > 0) item.pendapatan.toDouble() / totalPendapatan.toDouble() else 0.0
            val weight = when (item.harga) {
                13000 -> 1.0  // Baseline weight
                15000 -> 1.1  // 10% higher weight
                else -> 1.0
            }
            itemDetails.add(propDasar to weight)
            totalWeightedProp += (propDasar * weight)
        }

        // 2. Distribute Modal
        var allocatedModal = 0L
        for (i in categoryDataList.indices) {
            val item = categoryDataList[i]
            val (propDasar, weight) = itemDetails[i]
            
            if (i == categoryDataList.size - 1) {
                item.modal = totalModal - allocatedModal
            } else {
                val hybridFactor = if (totalWeightedProp > 0) (propDasar * weight) / totalWeightedProp else 0.0
                item.modal = (totalModal * hybridFactor).toLong()
                allocatedModal += item.modal
            }
            
            item.margin = if (item.pendapatan > 0) (item.pendapatan - item.modal).toDouble() / item.pendapatan * 100.0 else 0.0
            item.status = determineAuditStatus(item.margin)
        }

        updateAuditUI()
    }

    private fun determineAuditStatus(margin: Double): String {
        return when {
            margin < 5.0 -> "PERLU EVALUASI"
            margin < 10.0 -> "MARGIN TIPIS"
            margin < 15.0 -> "IDEAL"
            margin <= 20.0 -> "WAJAR"
            else -> "PERLU AUDIT"
        }
    }

    private fun updateAuditUI() {
        val localeID = Locale("id", "ID")
        val format = NumberFormat.getCurrencyInstance(localeID).apply { maximumFractionDigits = 0 }
        val numberFormat = NumberFormat.getNumberInstance(localeID)
        
        findViewById<TextView>(R.id.tvTotalRevenue).text = format.format(totalPendapatan)
        findViewById<TextView>(R.id.tvTotalCapital).text = format.format(totalModal)
        
        val netProfit = totalPendapatan - totalModal
        val totalMargin = if (totalPendapatan > 0) netProfit.toDouble() / totalPendapatan * 100.0 else 0.0
        
        findViewById<TextView>(R.id.tvTotalMargin).text = String.format(localeID, "%.1f%%", totalMargin)
        findViewById<TextView>(R.id.tvNetProfit).text = format.format(netProfit)
        
        val totalStatus = determineAuditStatus(totalMargin)
        findViewById<TextView>(R.id.tvAuditStatus).text = when(totalStatus) {
            "PERLU EVALUASI" -> "EVALUASI"
            "MARGIN TIPIS" -> "TIPIS"
            else -> totalStatus
        }
        val statusCard = findViewById<MaterialCardView>(R.id.cardAuditStatus)
        val insightTv = findViewById<TextView>(R.id.tvAiInsight)
        
        when (totalStatus) {
            "IDEAL" -> {
                statusCard.setCardBackgroundColor(Color.parseColor("#16A34A"))
                insightTv.text = "Margin sangat ideal and sehat"
            }
            "WAJAR" -> {
                statusCard.setCardBackgroundColor(Color.parseColor("#22C55E"))
                insightTv.text = "Margin dalam batas wajar (Stabil)"
            }
            "MARGIN TIPIS" -> {
                statusCard.setCardBackgroundColor(Color.parseColor("#F59E0B")) 
                insightTv.text = "Margin tipis, perlu efisiensi biaya"
            }
            "PERLU EVALUASI" -> {
                statusCard.setCardBackgroundColor(Color.parseColor("#F59E0B")) // KUNING
                insightTv.text = "Margin rendah, biaya operasional terlalu tinggi"
            }
            else -> { // PERLU AUDIT
                statusCard.setCardBackgroundColor(Color.parseColor("#EF4444")) // MERAH
                insightTv.text = "Margin sangat tinggi, potensi mark-up terdeteksi"
            }
        }

        val container = findViewById<LinearLayout>(R.id.containerCategoryAnalysis)
        container.removeAllViews()
        
        for (item in categoryDataList) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(12), 0, dpToPx(12))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
                text = item.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTextColor(Color.parseColor("#1E293B")); setPadding(dpToPx(8), 0, 0, 0)
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f)
                text = item.porsi.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#64748B"))
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
                text = numberFormat.format(item.harga)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#64748B"))
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
                text = numberFormat.format(item.pendapatan)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, android.graphics.Typeface.BOLD)
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
                text = numberFormat.format(item.modal)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#1E293B"))
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
                text = String.format(localeID, "%.1f%%", item.margin)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(when(item.status){ 
                    "IDEAL", "WAJAR" -> Color.parseColor("#16A34A")
                    "PERLU EVALUASI", "MARGIN TIPIS" -> Color.parseColor("#D97706")
                    else -> Color.parseColor("#EF4444") 
                })
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
                text = when(item.status) { 
                    "PERLU EVALUASI" -> "EVAL"
                    "MARGIN TIPIS" -> "TIPIS"
                    "IDEAL" -> "IDEAL"
                    "WAJAR" -> "WAJAR"
                    else -> "AUDIT" 
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(when(item.status){ 
                        "IDEAL", "WAJAR" -> Color.parseColor("#16A34A")
                        "PERLU EVALUASI", "MARGIN TIPIS" -> Color.parseColor("#F59E0B")
                        else -> Color.parseColor("#EF4444")
                    })
                }
            })

            container.addView(row)
            container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dpToPx(1)); setBackgroundColor(Color.parseColor("#F1F5F9")) })
        }
    }

    private fun updateComplianceUI() {
        if (!photoNota.isNullOrEmpty()) {
            findViewById<ImageView>(R.id.ivCheckPrep).visibility = View.VISIBLE
            base64ToBitmap(photoNota!!)?.let { findViewById<ImageView>(R.id.ivPrepThumb).apply { setImageBitmap(it); alpha = 1.0f } }
        }
        if (!photoBahan.isNullOrEmpty()) {
            findViewById<ImageView>(R.id.ivCheckRaw).visibility = View.VISIBLE
            base64ToBitmap(photoBahan!!)?.let { findViewById<ImageView>(R.id.ivRawThumb).apply { setImageBitmap(it); alpha = 1.0f } }
        }
        findViewById<TextView>(R.id.tvRawWeight).text = String.format("%s KG", rawWeight)
        
        // Handle Detailed AI Result
        populateDetailedAiResult()

        if (!photoSafety.isNullOrEmpty()) {
            findViewById<ImageView>(R.id.ivCheckSafety).visibility = View.VISIBLE
            base64ToBitmap(photoSafety!!)?.let { findViewById<ImageView>(R.id.ivSafetyThumb).apply { setImageBitmap(it); alpha = 1.0f } }
        }
        if (!photoSanitasi.isNullOrEmpty()) {
            findViewById<ImageView>(R.id.ivCheckSanitasi).visibility = View.VISIBLE
            base64ToBitmap(photoSanitasi!!)?.let { findViewById<ImageView>(R.id.ivSanitasiThumb).apply { setImageBitmap(it); alpha = 1.0f } }
        }
        findViewById<TextView>(R.id.tvSafetyTemp).text = String.format("%s °C", safetyTemp)
    }

    private fun populateDetailedAiResult() {
        val container1 = findViewById<View>(R.id.layoutAiResult1)
        val container2 = findViewById<View>(R.id.layoutAiResult2)
        
        container1?.visibility = View.GONE
        container2?.visibility = View.GONE
        findViewById<View>(R.id.cardSmallAiResult)?.visibility = View.VISIBLE

        if (aiResult.isNullOrEmpty() || !aiResult!!.startsWith("{")) {
            findViewById<TextView>(R.id.tvAiResult).text = aiResult ?: "Belum Verifikasi"
            return
        }

        try {
            val json = JSONObject(aiResult!!)
            
            if (json.has("menu1")) {
                findViewById<View>(R.id.cardSmallAiResult)?.visibility = View.GONE
                fillAiResultView(container1, json.getJSONObject("menu1"), photoAi)
                
                if (json.has("menu2")) {
                    fillAiResultView(container2, json.getJSONObject("menu2"), photoAi2)
                }
            } else {
                findViewById<View>(R.id.cardSmallAiResult)?.visibility = View.GONE
                fillAiResultView(container1, json, photoAi)
            }
        } catch (e: Exception) {
            findViewById<View>(R.id.cardSmallAiResult)?.visibility = View.VISIBLE
        }
    }

    private fun fillAiResultView(view: View?, data: JSONObject, photoBase64: String?) {
        if (view == null) return
        view.visibility = View.VISIBLE
        
        val status = data.optString("status", "REVISI")
        val komposisi = data.optString("komposisi", "--")
        val nutrisi = data.optJSONObject("nutrisi")
        val alasan = data.optString("alasan", "--")

        view.findViewById<TextView>(R.id.tvAiDetection).text = komposisi
        view.findViewById<TextView>(R.id.tvAiReason).text = alasan
        view.findViewById<TextView>(R.id.tvAiKalori).text = "${nutrisi?.optInt("kalori") ?: 0} kcal"
        view.findViewById<TextView>(R.id.tvAiProtein).text = "${nutrisi?.optInt("protein") ?: 0}g"
        view.findViewById<TextView>(R.id.tvAiLemak).text = "${nutrisi?.optInt("lemak") ?: 0}g"
        view.findViewById<TextView>(R.id.tvAiKarbo).text = "${nutrisi?.optInt("karbo") ?: 0}g"
        
        val statusTv = view.findViewById<TextView>(R.id.tvAiFinalStatus)
        val iconIv = view.findViewById<ImageView>(R.id.ivAiStatusIcon)
        val header = view.findViewById<View>(R.id.layoutAiStatusHeader)

        if (status.contains("LULUS", true)) {
            statusTv.text = "STATUS AI: LULUS QC"
            statusTv.setTextColor(Color.parseColor("#1E293B"))
            iconIv.setImageResource(android.R.drawable.checkbox_on_background)
            iconIv.setColorFilter(Color.parseColor("#16A34A"))
            header.setBackgroundColor(Color.parseColor("#F0FDF4"))
        } else {
            statusTv.text = "STATUS AI: REVISI"
            statusTv.setTextColor(Color.parseColor("#EF4444"))
            iconIv.setImageResource(android.R.drawable.ic_dialog_alert)
            iconIv.setColorFilter(Color.parseColor("#EF4444"))
            header.setBackgroundColor(Color.parseColor("#FEF2F2"))
        }

        val ivAiFood = view.findViewById<ImageView>(R.id.ivAiFoodPhoto)
        if (ivAiFood != null && !photoBase64.isNullOrEmpty()) {
            base64ToBitmap(photoBase64)?.let { 
                ivAiFood.setImageBitmap(it)
                ivAiFood.alpha = 1.0f 
            }
        }
    }

    private fun showMinimalistConfirmationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeleteTitle)
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirmDelete)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelDelete)

        tvTitle.text = "KIRIM LAPORAN AKHIR"
        tvMsg.text = "Pastikan semua data audit sudah benar. Laporan yang sudah dikirim akan dikunci dan tidak dapat diubah."
        btnConfirm.text = "KIRIM SEKARANG"
        btnConfirm.icon = null

        btnConfirm.setOnClickListener {
            submitFinalReport()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun submitFinalReport() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        val wastageCount = findViewById<EditText>(R.id.etWastageCount).text.toString().toLongOrNull() ?: 0L
        val wastageReason = findViewById<EditText>(R.id.etWastageReason).text.toString()

        val reportMap = hashMapOf(
            "vendorEmail" to userEmail,
            "submitted_by" to userEmail,
            "tanggal" to today,
            "waktu_submit" to com.google.firebase.Timestamp.now(),
            "total_pendapatan" to totalPendapatan,
            "totalRevenue" to totalPendapatan,
            "total_modal" to totalModal,
            "totalCapital" to totalModal,
            "total_porsi" to totalTargetPorsi,
            "porsi_target" to totalTargetPorsi,
            "totalTargetPorsi" to totalTargetPorsi,
            "porsi_realisasi" to totalTargetPorsi, 
            "porsiRealisasi" to totalTargetPorsi,
            "nama_menu" to (menuName ?: "Menu MBG"),
            "menu" to (menuName ?: "Menu MBG"),
            "foto_menu" to (menuPhoto ?: ""),
            "menuConfig" to menuConfigMap,
            "categories" to categoryDataList.map { it.toMap() },
            "porsi_rusak" to wastageCount,
            "wastageCount" to wastageCount,
            "alasan_utama" to wastageReason,
            "wastageReason" to wastageReason,
            "foto_nota" to (photoNota ?: ""),
            "foto_bahan" to (photoBahan ?: ""),
            "berat_bahan_baku" to (rawWeight ?: ""),
            "foto_ai" to (photoAi ?: ""),
            "foto_ai_2" to (photoAi2 ?: ""),
            "berat_boks" to (boxWeight ?: ""),
            "hasil_ai" to (aiResult ?: ""),
            "foto_safety" to (photoSafety ?: ""),
            "foto_sanitasi" to (photoSanitasi ?: ""),
            "sanitasiPhotoUrl" to (photoSanitasi ?: ""),
            "suhu_chiller" to (safetyTemp ?: ""),
            "transaction_id" to "MBG-$today-${System.currentTimeMillis().toString().takeLast(4)}"
        )

        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorRef = docs.documents[0].reference
                updateVendorCompliance(vendorRef, wastageCount)
                
                val historyDocRef = vendorRef.collection("history").document(today)
                historyDocRef.set(reportMap).addOnSuccessListener {
                    vendorRef.collection("daily_schedules").document(today).collection("destinations").get()
                        .addOnSuccessListener { destinations ->
                            for (doc in destinations) {
                                historyDocRef.collection("destinations").document(doc.id).set(doc.data)
                            }
                        }

                    vendorRef.collection("daily_schedules").document(today).update(
                        "currentStep", 10,
                        "statusProduksi", "SHIFT BERAKHIR"
                    ).addOnSuccessListener {
                        Toast.makeText(this, "Laporan berhasil dikirim!", Toast.LENGTH_SHORT).show()
                        lockUIForReadOnly()
                    }
                }
            }
        }
    }

    private fun updateVendorCompliance(vendorRef: com.google.firebase.firestore.DocumentReference, wastageCount: Long) {
        db.runTransaction { transaction ->
            val snapshot = transaction.get(vendorRef)
            val totalReports = (snapshot.getLong("totalReports") ?: 0L) + 1
            
            // Laporan dianggap patuh (perfect) jika tidak ada porsi rusak
            val isPerfect = if (wastageCount == 0L) 1 else 0
            val perfectReports = (snapshot.getLong("perfectReports") ?: 0L) + isPerfect
            
            val newCompliance = (perfectReports.toDouble() / totalReports.toDouble()) * 100
            
            transaction.update(vendorRef, mapOf(
                "totalReports" to totalReports,
                "perfectReports" to perfectReports,
                "complianceScore" to String.format(Locale.US, "%.0f%%", newCompliance)
            ))
        }.addOnSuccessListener {
            Log.d("Compliance", "Kepatuhan berhasil diperbarui")
        }.addOnFailureListener { e -> 
            Log.e("Compliance", "Gagal perbarui kepatuhan: ${e.message}") 
        }
    }

    private fun lockUIForReadOnly() {
        findViewById<MaterialButton>(R.id.btnLockAndSubmit).visibility = View.GONE
        findViewById<EditText>(R.id.etWastageCount).isEnabled = false
        findViewById<EditText>(R.id.etWastageReason).isEnabled = false
        
        findViewById<TextView>(R.id.tvTransactionId).text = "STATUS: LAPORAN TERKUNCI"
        findViewById<TextView>(R.id.tvTransactionId).setTextColor(Color.parseColor("#10B981"))
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}

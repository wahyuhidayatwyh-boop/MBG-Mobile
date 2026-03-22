package com.why.mbgdapur

import android.app.Dialog
import android.content.ContentValues
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.OutputStream
import java.text.NumberFormat
import java.util.*

class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val reportId = intent.getStringExtra("REPORT_ID") ?: ""
        findViewById<ImageView>(R.id.btnBackHistoryDetail).setOnClickListener { finish() }

        if (reportId.isNotEmpty()) {
            loadDetailData(reportId)
        }

        findViewById<ExtendedFloatingActionButton>(R.id.btnExportPdf).setOnClickListener {
            exportToPdf(reportId)
        }
    }

    private fun exportToPdf(reportId: String) {
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollViewDetail) ?: return
        val totalHeight = scrollView.getChildAt(0).height
        val totalWidth = scrollView.getChildAt(0).width

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        scrollView.draw(canvas)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(totalWidth, totalHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val fileName = "Audit_MBG_${reportId.replace("-", "")}.pdf"

        try {
            val outputStream: OutputStream?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                outputStream = uri?.let { contentResolver.openOutputStream(it) }
            } else {
                val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                outputStream = java.io.FileOutputStream(file)
            }

            outputStream?.use {
                pdfDocument.writeTo(it)
                Toast.makeText(this, "PDF Berhasil Disimpan di folder Downloads", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal export PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun loadDetailData(reportId: String) {
        val userEmail = auth.currentUser?.email ?: return
        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorDoc = docs.documents[0]
                val vendorId = vendorDoc.id
                
                db.collection("vendors").document(vendorId)
                    .collection("history").document(reportId).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val report = doc.toObject(HistoryReport::class.java)
                            if (report != null) {
                                displayData(report, reportId)
                                loadDestinations(vendorId, reportId, "history")
                                loadCategoryPhotos(vendorId, reportId, "history")
                                return@addOnSuccessListener
                            }
                        }
                        
                        db.collection("vendors").document(vendorId)
                            .collection("daily_schedules").document(reportId).get()
                            .addOnSuccessListener { scheduleDoc ->
                                if (scheduleDoc.exists()) {
                                    val report = scheduleDoc.toObject(HistoryReport::class.java)
                                    if (report != null) {
                                        displayData(report, reportId)
                                        loadDestinations(vendorId, reportId, "daily_schedules")
                                        loadCategoryPhotos(vendorId, reportId, "daily_schedules")
                                    }
                                } else {
                                    Toast.makeText(this, "Data laporan tidak ditemukan", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
            }
        }
    }

    private fun loadCategoryPhotos(vendorId: String, reportId: String, collectionName: String) {
        val layoutCategoryPhotos = findViewById<LinearLayout>(R.id.layoutCategoryPhotos) ?: return
        val layoutPhoto13K = findViewById<LinearLayout>(R.id.layoutPhoto13K) ?: return
        val ivPhoto13K = findViewById<ImageView>(R.id.ivPhoto13K) ?: return
        val layoutPhoto15K = findViewById<LinearLayout>(R.id.layoutPhoto15K) ?: return
        val ivPhoto15K = findViewById<ImageView>(R.id.ivPhoto15K) ?: return
        val ivMainPhoto = findViewById<ImageView>(R.id.ivDetailMenuPhoto)

        fun fetchPhoto(coll: String, docName: String, targetLayout: LinearLayout, targetIv: ImageView) {
            db.collection("vendors").document(vendorId)
                .collection(coll).document(reportId)
                .collection("media").document(docName).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val data = doc.getString("data") ?: doc.getString("imageUrl")
                        if (!data.isNullOrEmpty()) {
                            layoutCategoryPhotos.visibility = View.VISIBLE
                            targetLayout.visibility = View.VISIBLE
                            setupClickableImage(targetIv, data)
                            
                            if (ivMainPhoto != null && (ivMainPhoto.alpha < 0.5f || ivMainPhoto.tag == "placeholder")) {
                                setupClickableImage(ivMainPhoto, data)
                                ivMainPhoto.tag = "loaded"
                            }
                        }
                    } else if (coll == "history") {
                        fetchPhoto("daily_schedules", docName, targetLayout, targetIv)
                    }
                }
        }

        fetchPhoto(collectionName, "photo_CAT_13K", layoutPhoto13K, ivPhoto13K)
        fetchPhoto(collectionName, "photo_CAT_15K", layoutPhoto15K, ivPhoto15K)
    }

    private fun loadDestinations(vendorId: String, reportId: String, collectionName: String) {
        val container = findViewById<LinearLayout>(R.id.containerDetailSchools)
        val tvTotalLabel = findViewById<TextView>(R.id.tvDetailTotalPorsiLabel)
        
        db.collection("vendors").document(vendorId)
            .collection(collectionName).document(reportId)
            .collection("destinations").get().addOnSuccessListener { destinations ->
                container.removeAllViews()
                var totalPorsi = 0L
                for (dest in destinations) {
                    val schoolName = dest.getString("schoolName") ?: "Sekolah"
                    val porsi = dest.getLong("targetPorsi") ?: dest.getLong("p13")?.plus(dest.getLong("p46") ?: 0L) ?: 0L
                    totalPorsi += porsi
                    
                    val itemView = LayoutInflater.from(this).inflate(R.layout.item_dialog_school_porsi, container, false)
                    itemView.findViewById<TextView>(R.id.tvDialogSchoolName).text = schoolName
                    itemView.findViewById<TextView>(R.id.tvDialogSchoolPorsi).text = "$porsi Porsi"
                    container.addView(itemView)
                }
                tvTotalLabel.text = "$totalPorsi Porsi"
            }
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

    private fun displayData(report: HistoryReport, date: String) {
        val localeID = Locale("id", "ID")
        val format = NumberFormat.getCurrencyInstance(localeID).apply { maximumFractionDigits = 0 }
        
        findViewById<TextView>(R.id.tvDetailDate).text = date
        
        val revenue = report.resolvedRevenue()
        val capital = report.resolvedCapital()
        
        findViewById<TextView>(R.id.tvDetailTotalRevenue).text = format.format(revenue)
        findViewById<TextView>(R.id.tvDetailTotalCapital).text = format.format(capital)
        
        val netProfit = revenue - capital
        val margin = if (revenue > 0) netProfit.toDouble() / revenue * 100.0 else 0.0
        
        findViewById<TextView>(R.id.tvDetailTotalMargin).text = String.format(localeID, "%.1f%%", margin)
        findViewById<TextView>(R.id.tvDetailNetProfit).text = format.format(netProfit)
        
        val statusText = determineAuditStatus(margin)
        findViewById<TextView>(R.id.tvDetailAuditStatus).text = when(statusText) {
            "PERLU EVALUASI" -> "EVALUASI"
            "MARGIN TIPIS" -> "TIPIS"
            else -> statusText
        }
        val statusCard = findViewById<MaterialCardView>(R.id.cardDetailAuditStatus)
        val insightTv = findViewById<TextView>(R.id.tvDetailAiInsight)
        
        when (statusText) {
            "IDEAL" -> {
                statusCard.setCardBackgroundColor(Color.parseColor("#16A34A"))
                insightTv.text = "Margin sangat ideal dan sehat"
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
                statusCard.setCardBackgroundColor(Color.parseColor("#F59E0B"))
                insightTv.text = "Margin rendah, biaya operasional terlalu tinggi"
            }
            else -> {
                statusCard.setCardBackgroundColor(Color.parseColor("#EF4444"))
                insightTv.text = "Margin sangat tinggi, potensi mark-up terdeteksi"
            }
        }

        val categoryContainer = findViewById<LinearLayout>(R.id.containerCategoryAnalysisDetail)
        categoryContainer.removeAllViews()
        report.categories?.forEach { itemMap ->
            val row = createAnalysisRow(itemMap, localeID)
            categoryContainer.addView(row)
            categoryContainer.addView(View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(-1, dpToPx(1))
                setBackgroundColor(Color.parseColor("#F1F5F9")) 
            })
        }

        val menuNameStr = report.resolvedMenuName()
        findViewById<TextView>(R.id.tvDetailMenuName).text = if (menuNameStr.isEmpty()) "Menu Tidak Tercatat" else menuNameStr
        findViewById<TextView>(R.id.tvDetailTargetPorsi).text = "Target Total: ${report.resolvedTargetPorsi()} Porsi"
        
        val ivMain = findViewById<ImageView>(R.id.ivDetailMenuPhoto)
        val mainPhoto = report.resolvedMenuPhoto()
        if (mainPhoto.isNotEmpty()) {
            setupClickableImage(ivMain, mainPhoto)
            ivMain?.tag = "loaded"
        } else {
            setupClickableImage(ivMain, null)
            ivMain?.tag = "placeholder"
        }

        findViewById<TextView>(R.id.tvDetailWeightRaw).text = "Berat: ${report.resolvedBeratBahan()} KG"
        setupClickableImage(findViewById(R.id.ivDetailPhotoNota), report.resolvedFotoNota())
        setupClickableImage(findViewById(R.id.ivDetailPhotoBahan), report.resolvedFotoBahan())

        // Handle Detailed AI Result in History (Passing both photos)
        populateHistoryAiResult(report.resolvedHasilAi(), report.resolvedFotoAi(), report.resolvedFotoAi2())

        findViewById<TextView>(R.id.tvDetailSafetyTemp).text = "Suhu: ${report.resolvedSuhuChiller()} °C"
        setupClickableImage(findViewById(R.id.ivDetailPhotoSafety), report.resolvedFotoSafety())
        setupClickableImage(findViewById(R.id.ivDetailPhotoSanitasi), report.resolvedFotoSanitasi())

        findViewById<TextView>(R.id.tvDetailWastage).text = "${report.resolvedWastageCount()} Porsi"
        val wastageReasonStr = report.resolvedWastageReason()
        findViewById<TextView>(R.id.tvDetailWastageReason).text = if (wastageReasonStr.isEmpty()) "--" else wastageReasonStr
        
        findViewById<TextView>(R.id.tvDetailLogId).text = "ID: ${report.transaction_id}"
    }

    private fun populateHistoryAiResult(aiRes: String, photoAi: String, photoAi2: String) {
        val container1 = findViewById<View>(R.id.layoutDetailAiResult1)
        val container2 = findViewById<View>(R.id.layoutDetailAiResult2)
        
        container1?.visibility = View.GONE
        container2?.visibility = View.GONE
        findViewById<View>(R.id.cardDetailSmallAi)?.visibility = View.VISIBLE

        if (aiRes.isEmpty() || !aiRes.startsWith("{")) {
            findViewById<TextView>(R.id.tvDetailAiScore).text = "Status: $aiRes"
            setupClickableImage(findViewById(R.id.ivDetailPhotoAi), photoAi)
            return
        }

        try {
            val json = JSONObject(aiRes)
            if (json.has("menu1")) {
                findViewById<View>(R.id.cardDetailSmallAi)?.visibility = View.GONE
                fillAiResultView(container1, json.getJSONObject("menu1"), photoAi)
                if (json.has("menu2")) {
                    fillAiResultView(container2, json.getJSONObject("menu2"), photoAi2)
                }
            } else {
                findViewById<View>(R.id.cardDetailSmallAi)?.visibility = View.GONE
                fillAiResultView(container1, json, photoAi)
            }
        } catch (e: Exception) {
            findViewById<View>(R.id.cardDetailSmallAi)?.visibility = View.VISIBLE
        }
    }

    private fun fillAiResultView(view: View?, data: JSONObject, photoSource: String?) {
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

        setupClickableImage(view.findViewById(R.id.ivAiFoodPhoto), photoSource)
    }

    private fun createAnalysisRow(item: Map<String, Any>, locale: Locale): View {
        val numberFormat = NumberFormat.getNumberInstance(locale)
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val name = item["name"] as? String ?: "--"
        val porsi = (item["porsi"] as? Number)?.toLong() ?: 0L
        val harga = (item["harga"] as? Number)?.toInt() ?: 0
        val pendapatan = (item["pendapatan"] as? Number)?.toLong() ?: 0L
        val modal = (item["modal"] as? Number)?.toLong() ?: 0L
        val marginVal = (item["margin"] as? Number)?.toDouble() ?: 0.0
        
        val status = determineAuditStatus(marginVal)

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
            text = name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(Color.parseColor("#1E293B")); setPadding(dpToPx(8), 0, 0, 0)
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f)
            text = porsi.toString(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#64748B"))
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
            text = numberFormat.format(harga); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#64748B"))
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
            text = numberFormat.format(pendapatan); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
            text = numberFormat.format(modal); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.parseColor("#1E293B"))
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
            text = String.format(locale, "%.1f%%", marginVal); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(when(status){ 
                "IDEAL", "WAJAR" -> Color.parseColor("#16A34A")
                "PERLU EVALUASI", "MARGIN TIPIS" -> Color.parseColor("#D97706")
                else -> Color.parseColor("#EF4444") 
            })
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
            text = when(status) { 
                "PERLU EVALUASI" -> "EVAL"
                "MARGIN TIPIS" -> "TIPIS"
                "IDEAL" -> "IDEAL"
                "WAJAR" -> "WAJAR"
                else -> "AUDIT" 
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f); textAlignment = View.TEXT_ALIGNMENT_CENTER; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dpToPx(2), 0, dpToPx(2))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(4).toFloat()
                setColor(when(status){ 
                    "IDEAL", "WAJAR" -> Color.parseColor("#16A34A")
                    "PERLU EVALUASI", "MARGIN TIPIS" -> Color.parseColor("#F59E0B")
                    else -> Color.parseColor("#EF4444")
                })
            }
        })

        return row
    }

    private fun setupClickableImage(imageView: ImageView?, imageSource: String?) {
        if (imageView == null) return
        
        if (imageSource.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.logo_mbg)
            imageView.alpha = 0.1f
            return
        }

        if (imageSource.startsWith("http")) {
            Glide.with(this)
                .load(imageSource)
                .placeholder(R.drawable.logo_mbg)
                .into(imageView)
            imageView.alpha = 1.0f
            imageView.setOnClickListener { showImageUrlPreview(imageSource) }
        } else {
            val bitmap = base64ToBitmap(imageSource)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.alpha = 1.0f
                imageView.setOnClickListener { showImagePreview(bitmap) }
            } else {
                imageView.setImageResource(R.drawable.logo_mbg)
                imageView.alpha = 0.1f
            }
        }
    }

    private fun showImagePreview(bitmap: Bitmap) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        imageView.adjustViewBounds = true
        imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setContentView(imageView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showImageUrlPreview(url: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val imageView = ImageView(this)
        Glide.with(this).load(url).into(imageView)
        imageView.adjustViewBounds = true
        imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setContentView(imageView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val cleanBase64 = if (base64Str.contains(",")) base64Str.split(",")[1] else base64Str
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) { null }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}

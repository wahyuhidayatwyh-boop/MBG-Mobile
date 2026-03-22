package com.why.mbgdapur

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.DriverHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class DriverHistoryActivity : AppCompatActivity() {

    private lateinit var binding: DriverHistoryBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    data class LogItem(val doc: DocumentSnapshot, val type: String, val timestamp: Date)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupActions()
        loadStatisticsAndHistory()
    }

    private fun setupActions() {
        binding.btnBackHistory.setOnClickListener { finish() }
    }

    private fun loadStatisticsAndHistory() {
        val userEmail = auth.currentUser?.email ?: return
        val calendar = Calendar.getInstance()
        val todayStart = getStartOfDay(calendar.time)
        
        // Batas 7 hari yang lalu untuk filter arsip
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val archiveThreshold = getStartOfDay(calendar.time)

        // Batas minggu ini (Senin jam 00:00)
        calendar.time = Date()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val weekStart = getStartOfDay(calendar.time)
        
        // Batas bulan ini (Tanggal 1 jam 00:00)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = getStartOfDay(calendar.time)

        db.collection("drivers").document(userEmail).collection("delivery_logs")
            .get() 
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) {
                    binding.tvEmptyHistory.visibility = View.VISIBLE
                    binding.tvStatDay.text = "0"
                    binding.tvStatWeek.text = "0"
                    binding.tvStatMonth.text = "0"
                    return@addOnSuccessListener
                }
                
                binding.containerHistory.removeAllViews()
                val allItems = mutableListOf<LogItem>()
                val monthlyStats = mutableMapOf<String, Int>()
                
                var countDay = 0
                var countWeek = 0
                var countMonth = 0

                snapshots.documents.forEach { doc ->
                    val status = doc.getString("status") ?: ""
                    val returStatus = doc.getString("returStatus") ?: ""
                    val isManifest = doc.contains("buktiMuatBase64")

                    // Ambil waktu untuk sorting dan filtering
                    val deliveryTs = doc.getTimestamp("waktuSelesai")?.toDate() ?: doc.getTimestamp("updatedAt")?.toDate() ?: Date()
                    val manifestTs = doc.getTimestamp("waktuMuat")?.toDate() ?: doc.getTimestamp("updatedAt")?.toDate() ?: Date()
                    val returTs = doc.getTimestamp("returWaktu")?.toDate() ?: doc.getTimestamp("updatedAt")?.toDate() ?: Date()

                    // 1. MANIFEST
                    if (isManifest && manifestTs.after(archiveThreshold)) {
                        allItems.add(LogItem(doc, "MANIFEST", manifestTs))
                    }
                    
                    // 2. DELIVERY
                    if (status == "DONE") {
                        if (deliveryTs.after(archiveThreshold)) {
                            allItems.add(LogItem(doc, "DELIVERY", deliveryTs))
                        }
                        
                        // Statistik tetap dihitung untuk bulan ini meskipun sudah diarsipkan dari list
                        if (deliveryTs.after(todayStart)) countDay++
                        if (deliveryTs.after(weekStart)) countWeek++
                        if (deliveryTs.after(monthStart)) countMonth++

                        // Statistik untuk Chart (Hanya Delivery)
                        val mKey = SimpleDateFormat("MMM", Locale.getDefault()).format(deliveryTs)
                        monthlyStats[mKey] = (monthlyStats[mKey] ?: 0) + 1
                    }

                    // 3. RETUR
                    if ((returStatus == "DONE" || doc.contains("returBoxCount")) && returTs.after(archiveThreshold)) {
                        allItems.add(LogItem(doc, "RETUR", returTs))
                    }
                }

                // Urutkan semua aktivitas berdasarkan waktu terbaru di memori
                allItems.sortByDescending { it.timestamp }

                binding.tvStatDay.text = countDay.toString()
                binding.tvStatWeek.text = countWeek.toString()
                binding.tvStatMonth.text = countMonth.toString()
                
                if (allItems.isEmpty()) {
                    binding.tvEmptyHistory.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyHistory.visibility = View.GONE
                    allItems.forEach { addHistoryCard(it) }
                }

                drawModernChart(monthlyStats)
            }
    }

    private fun addHistoryCard(item: LogItem) {
        val doc = item.doc
        val inflater = LayoutInflater.from(this)
        val cardView = inflater.inflate(R.layout.item_route_card, binding.containerHistory, false)
        
        val tvName = cardView.findViewById<TextView>(R.id.tvSchoolNameRoute)
        val tvDetail = cardView.findViewById<TextView>(R.id.tvRouteDetail)
        val ivIcon = cardView.findViewById<ImageView>(R.id.ivRouteStatus)

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(item.timestamp)
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(item.timestamp)

        when (item.type) {
            "MANIFEST" -> {
                tvName.text = "MANIFEST MUATAN"
                val total = doc.getLong("totalMuatan") ?: 0
                tvDetail.text = "Selesai Muat • $total Boks • $timeStr"
                ivIcon.setImageResource(android.R.drawable.ic_dialog_map)
                ivIcon.backgroundTintList = ContextCompat.getColorStateList(this, R.color.mbg_green_light)
                ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.mbg_green_dark))
            }
            "RETUR" -> {
                val rawId = doc.id.substringAfter("_")
                val schoolName = if (rawId.contains("@")) rawId.substringBefore("@").uppercase() else "SEKOLAH"
                tvName.text = "PENJEMPUTAN: $schoolName"
                val boks = doc.getLong("returBoxCount") ?: 0
                tvDetail.text = "Boks Kosong: $boks • $dateStr • $timeStr"
                ivIcon.setImageResource(android.R.drawable.ic_menu_revert)
                ivIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EFF6FF"))
                ivIcon.setColorFilter(Color.parseColor("#3B82F6"))
            }
            "DELIVERY" -> {
                val rawId = doc.id.substringAfter("_")
                val schoolName = if (rawId.contains("@")) rawId.substringBefore("@").uppercase() else "SEKOLAH"
                tvName.text = "PENGANTARAN: $schoolName"
                tvDetail.text = "Berhasil Terkirim • $dateStr • $timeStr"
                ivIcon.setImageResource(android.R.drawable.ic_input_add)
                ivIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0FDF4"))
                ivIcon.setColorFilter(Color.parseColor("#16A34A"))
            }
        }

        cardView.setOnClickListener {
            val intent = Intent(this, DriverHistoryDetailActivity::class.java)
            intent.putExtra("LOG_ID", doc.id)
            intent.putExtra("LOG_TYPE", item.type)
            startActivity(intent)
        }

        binding.containerHistory.addView(cardView)
    }

    private fun drawModernChart(stats: Map<String, Int>) {
        binding.layoutChartContainer.removeAllViews()
        val maxVal = stats.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val cal = Calendar.getInstance()
        val monthsToShow = mutableListOf<String>()
        for (i in 0..4) {
            monthsToShow.add(SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        monthsToShow.reverse()

        monthsToShow.forEach { m ->
            val count = stats[m] ?: 0
            val barHeightPercent = (count.toFloat() / maxVal * 0.75f).coerceAtLeast(0.05f)
            val column = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; orientation = LinearLayout.VERTICAL
            }
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(12), 0).apply { weight = barHeightPercent; bottomMargin = dpToPx(8) }
                val isCurrentMonth = m == SimpleDateFormat("MMM", Locale.getDefault()).format(Date())
                setBackgroundColor(if (isCurrentMonth) Color.parseColor("#FBC02D") else Color.parseColor("#003366"))
                outlineProvider = android.view.ViewOutlineProvider.BOUNDS; clipToOutline = true
            }
            val label = TextView(this).apply {
                text = m; setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(Color.parseColor("#94A3B8")); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); gravity = Gravity.CENTER
            }
            column.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f - barHeightPercent) })
            column.addView(bar); column.addView(label)
            binding.layoutChartContainer.addView(column)
        }
    }

    private fun getStartOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}

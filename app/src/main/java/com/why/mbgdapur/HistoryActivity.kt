package com.why.mbgdapur

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: HistoryAdapter
    private val historyReports = mutableListOf<HistoryReport>()
    
    private lateinit var btnDelete: ImageView
    private lateinit var tvSelectAll: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvMainStatIncome: TextView
    private lateinit var tvMainStatOrders: TextView
    private var bottomNav: BottomNavigationView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        btnDelete = findViewById(R.id.btnDeleteSelection)
        tvSelectAll = findViewById(R.id.tvSelectAll)
        chipGroup = findViewById(R.id.chipGroupHistoryFilter)
        tvMainStatIncome = findViewById(R.id.tvMainStatIncome)
        tvMainStatOrders = findViewById(R.id.tvMainStatOrders)
        bottomNav = findViewById(R.id.bottomNavVendor)

        tvSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            updateStatistics()
        }

        setupRecyclerView()
        setupBottomNav()
        loadHistoryData()
    }

    override fun onResume() {
        super.onResume()
        bottomNav?.selectedItemId = R.id.nav_history
    }

    private fun setupBottomNav() {
        bottomNav?.selectedItemId = R.id.nav_history
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_schedule -> {
                    startActivity(Intent(this, ScheduleListActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, VendorReportsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> true
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvHistoryList)
        rv.layoutManager = LinearLayoutManager(this)
        
        adapter = HistoryAdapter(
            historyReports,
            onSelectionChanged = { selectedCount ->
                btnDelete.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
                tvSelectAll.visibility = if (adapter.isSelectionModeActive()) View.VISIBLE else View.GONE
            },
            onItemClick = { report ->
                val intent = Intent(this, HistoryDetailActivity::class.java)
                intent.putExtra("REPORT_ID", report.id)
                startActivity(intent)
            }
        )
        rv.adapter = adapter
    }

    private fun showDeleteConfirmation() {
        val count = adapter.getSelectedIds().size
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.tvDeleteTitle).text = "Hapus Riwayat"
        dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text = "Apakah Anda yakin ingin menghapus $count item riwayat terpilih? Tindakan ini tidak dapat dibatalkan."

        dialogView.findViewById<MaterialButton>(R.id.btnConfirmDelete).setOnClickListener {
            deleteSelectedItems()
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteSelectedItems() {
        val userEmail = auth.currentUser?.email ?: return
        val selectedIds = adapter.getSelectedIds()
        val countToDelete = selectedIds.size
        
        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorId = docs.documents[0].id
                val batch = db.batch()
                
                selectedIds.forEach { id ->
                    val ref = db.collection("vendors").document(vendorId)
                        .collection("history").document(id)
                    batch.delete(ref)
                }
                
                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "$countToDelete item berhasil dihapus", Toast.LENGTH_SHORT).show()
                    adapter.setSelectionMode(false)
                    loadHistoryData()
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadHistoryData() {
        val userEmail = auth.currentUser?.email ?: return
        
        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorId = docs.documents[0].id
                
                db.collection("vendors").document(vendorId)
                    .collection("history")
                    .orderBy("waktu_submit", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { historyDocs ->
                        historyReports.clear()
                        for (doc in historyDocs) {
                            val report = doc.toObject(HistoryReport::class.java).copy(id = doc.id)
                            historyReports.add(report)
                        }
                        
                        adapter.updateData(historyReports)
                        updateStatistics()
                        
                        findViewById<LinearLayout>(R.id.layoutEmptyHistory).visibility = 
                            if (historyReports.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    private fun updateStatistics() {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val todayStr = sdf.format(Date())
        
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStartTime = cal.timeInMillis
        
        val weekCal = Calendar.getInstance()
        weekCal.set(Calendar.HOUR_OF_DAY, 0)
        weekCal.set(Calendar.MINUTE, 0)
        weekCal.set(Calendar.SECOND, 0)
        weekCal.set(Calendar.MILLISECOND, 0)
        weekCal.add(Calendar.DAY_OF_YEAR, -6)
        val weekStartTime = weekCal.timeInMillis

        var currentIncome = 0L
        var currentOrders = 0

        val selectedChipId = chipGroup.checkedChipId

        historyReports.forEach { report ->
            val reportTime = try {
                sdf.parse(report.id)?.time ?: report.waktu_submit?.toDate()?.time ?: 0L
            } catch (e: Exception) {
                report.waktu_submit?.toDate()?.time ?: 0L
            }
            
            val reportDateStr = if (report.id.contains("-")) report.id else sdf.format(Date(reportTime))

            when (selectedChipId) {
                R.id.chipHistoryDay -> {
                    if (reportDateStr == todayStr || reportTime >= dayStartTime) {
                        currentIncome += report.resolvedRevenue()
                        currentOrders++
                    }
                }
                R.id.chipHistoryWeek -> {
                    if (reportTime >= weekStartTime) {
                        currentIncome += report.resolvedRevenue()
                        currentOrders++
                    }
                }
                R.id.chipHistoryMonth -> {
                    currentIncome += report.resolvedRevenue()
                    currentOrders++
                }
            }
        }

        tvMainStatIncome.text = format.format(currentIncome)
        tvMainStatOrders.text = "$currentOrders Selesai"
        
        updateProductionTrend()
    }

    private fun updateProductionTrend() {
        val container = findViewById<LinearLayout>(R.id.layoutChartContainer)
        container.removeAllViews()

        val sdfFull = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val sdfShort = SimpleDateFormat("dd/MM", Locale.getDefault())

        val last7Days = mutableListOf<Triple<String, String, Long>>()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val fullDate = sdfFull.format(cal.time)
            val shortDate = sdfShort.format(cal.time)
            
            val dailyPorsi = historyReports.filter { 
                val rTime = it.waktu_submit?.toDate()?.time ?: 0L
                val rDate = if (it.id.contains("-")) it.id else sdfFull.format(Date(rTime))
                rDate == fullDate
            }.sumOf { it.resolvedRealisasiPorsi() }
            
            last7Days.add(Triple(fullDate, shortDate, dailyPorsi))
        }

        val maxPorsi = last7Days.maxOfOrNull { it.third } ?: 0L
        val chartMaxHeightDp = 100f

        last7Days.forEachIndexed { index, (_, shortDate, porsi) ->
            val barLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            val bar = View(this).apply {
                val heightPx = if (maxPorsi > 0) {
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 
                        (porsi.toFloat() / maxPorsi * chartMaxHeightDp).coerceAtLeast(4f), 
                        resources.displayMetrics
                    ).toInt()
                } else {
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
                }
                
                val widthPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics
                ).toInt()

                layoutParams = LinearLayout.LayoutParams(widthPx, heightPx).apply {
                    bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                }
                
                background = ContextCompat.getDrawable(this@HistoryActivity, R.drawable.bg_status_completed)
                
                if (porsi == 0L) {
                    alpha = 0.1f
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                } else {
                    alpha = 1.0f
                    val colorRes = if (index == 6) R.color.mbg_green_primary else R.color.mbg_green_dark
                    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@HistoryActivity, colorRes))
                }
            }

            val tvDate = TextView(this).apply {
                text = shortDate
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#94A3B8"))
                typeface = Typeface.DEFAULT_BOLD
            }

            barLayout.addView(bar)
            barLayout.addView(tvDate)
            container.addView(barLayout)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter.isSelectionModeActive()) {
            adapter.setSelectionMode(false)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

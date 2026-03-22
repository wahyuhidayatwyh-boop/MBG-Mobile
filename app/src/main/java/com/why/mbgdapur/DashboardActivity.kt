package com.why.mbgdapur

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentStep: Int = 2 
    private var bottomNav: BottomNavigationView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        bottomNav = findViewById(R.id.bottomNavVendor)

        loadVendorDataAndTodaySchedule()
        setupLogout()
        setupBottomNav()
        setupBackConfirmation()
        listenToEmergencyNotifications()
        
        findViewById<MaterialCardView>(R.id.menuPanic)?.setOnClickListener {
            startActivity(Intent(this, ReportIssueActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnViewOrderDetails)?.setOnClickListener {
            startActivity(Intent(this, OrderDetailActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNav?.selectedItemId = R.id.nav_home
    }

    private fun listenToEmergencyNotifications() {
        val user = auth.currentUser ?: return
        val vendorEmail = user.email ?: return
        
        db.collection("drivers")
            .whereEqualTo("assignedVendorEmail", vendorEmail)
            .addSnapshotListener { snapshots, e ->
                if (e != null || isFinishing) return@addSnapshotListener
                
                var emergencyFound = false
                snapshots?.forEach { doc ->
                    val issueType = doc.getString("currentIssueType") ?: "NONE"
                    if (issueType != "NONE") emergencyFound = true
                }
                
                if (emergencyFound) {
                    bottomNav?.getOrCreateBadge(R.id.nav_reports)?.apply {
                        backgroundColor = Color.RED
                        isVisible = true
                    }
                } else {
                    bottomNav?.removeBadge(R.id.nav_reports)
                }
            }
    }

    private fun setupBackConfirmation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showCustomConfirmationDialog(
                    title = "Konfirmasi Keluar",
                    message = "Apakah Anda yakin ingin menutup aplikasi?",
                    confirmText = "YA, KELUAR",
                    iconRes = android.R.drawable.ic_lock_power_off
                ) {
                    finishAffinity()
                }
            }
        })
    }

    private fun showCustomConfirmationDialog(
        title: String,
        message: String,
        confirmText: String,
        iconRes: Int = android.R.drawable.ic_dialog_info,
        onConfirm: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        dialogView.findViewById<TextView>(R.id.tvExitTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvExitMessage).text = message
        dialogView.findViewById<ImageView>(R.id.ivExitIcon)?.setImageResource(iconRes)
        
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirmExit)
        btnConfirm.text = confirmText
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btnCancelExit).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun loadVendorDataAndTodaySchedule() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        db.collection("vendors").whereEqualTo("email", userEmail)
            .addSnapshotListener { documents, _ ->
                if (documents != null && !documents.isEmpty) {
                    val doc = documents.documents[0]
                    findViewById<TextView>(R.id.tvVendorNameDashboard)?.text = doc.getString("name") ?: "DAPUR MBG"
                    findViewById<TextView>(R.id.tvVendorLocation)?.text = doc.getString("location") ?: "Lokasi"
                    
                    val ratingRaw = doc.get("averageRating")?.toString() ?: "0.0"
                    val rating = ratingRaw.toDoubleOrNull() ?: 0.0
                    
                    val complianceRaw = doc.get("complianceScore")?.toString() ?: "100"
                    val compliance = complianceRaw.replace("%", "").trim()
                    
                    findViewById<TextView>(R.id.tvVendorRating)?.text = String.format("%.1f", rating)
                    findViewById<TextView>(R.id.tvIntegrityScore)?.text = "$compliance%"
                    
                    val scheduleRef = doc.reference.collection("daily_schedules").document(today)
                    scheduleRef.addSnapshotListener { scheduleDoc, _ ->
                        val tvTargetCount = findViewById<TextView>(R.id.tvTargetCount)
                        val tvTargetMenu = findViewById<TextView>(R.id.tvTargetMenu)
                        val tvStatus = findViewById<TextView>(R.id.tvStatusProduksi)
                        val cardStatus = findViewById<MaterialCardView>(R.id.cardStatusProduksi)
                        
                        if (scheduleDoc != null && scheduleDoc.exists()) {
                            currentStep = scheduleDoc.getLong("currentStep")?.toInt() ?: 2
                            
                            if (currentStep >= 10) {
                                tvTargetCount?.text = "✓"
                                tvTargetMenu?.text = "Semua Selesai"
                                tvStatus?.text = "SHIFT BERAKHIR"
                                tvStatus?.setTextColor(Color.WHITE)
                                cardStatus?.setCardBackgroundColor(Color.parseColor("#F59E0B"))
                            } else {
                                val menuRaw = scheduleDoc.getString("menu") ?: ""
                                val displayMenu = formatMenuDisplay(menuRaw)
                                tvTargetMenu?.text = displayMenu

                                scheduleRef.collection("destinations").addSnapshotListener { destinations, _ ->
                                    var totalKeseluruhan = 0L
                                    destinations?.forEach { dest ->
                                        val porsiRaw = dest.get("targetPorsi")
                                        totalKeseluruhan += when (porsiRaw) {
                                            is Number -> porsiRaw.toLong()
                                            is String -> porsiRaw.toLongOrNull() ?: 0L
                                            else -> 0L
                                        }
                                    }
                                    val displayTotal = if (totalKeseluruhan > 0) totalKeseluruhan else {
                                        scheduleDoc.getLong("targetPorsi") ?: 0L
                                    }
                                    tvTargetCount?.text = displayTotal.toString()
                                }
                                tvStatus?.text = scheduleDoc.getString("statusProduksi") ?: "SIAP PRODUKSI"
                                tvStatus?.setTextColor(Color.WHITE)
                                cardStatus?.setCardBackgroundColor(Color.parseColor("#F59E0B"))
                            }
                        } else {
                            tvTargetCount?.text = "0"
                            tvTargetMenu?.text = "Tidak Ada Jadwal"
                            tvStatus?.text = "MENUNGGU JADWAL"
                            tvStatus?.setTextColor(Color.WHITE)
                            cardStatus?.setCardBackgroundColor(Color.parseColor("#F59E0B"))
                            currentStep = 0
                        }
                        updateMenuAccessibility()
                    }
                }
            }
    }

    private fun formatMenuDisplay(menu: String): String {
        if (menu.isEmpty()) return "Menu Belum Diatur"
        val items = menu.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            items.isEmpty() -> "Menu Belum Diatur"
            items.size == 1 -> items[0]
            items.size == 2 -> "${items[0]} & ${items[1]}"
            else -> "${items[0]} & ${items.size - 1} Menu Lainnya"
        }
    }

    private fun updateMenuAccessibility() {
        val menuIds = listOf(
            R.id.menuPrepMorning, R.id.menuCookingProgress, 
            R.id.menuVisualAI, R.id.menuFoodSafety, R.id.menuDistribution, 
            R.id.menuReturnWadah, R.id.menuSanitation, R.id.menuFinalReport
        )

        for (index in menuIds.indices) {
            val stepNumber = index + 2 
            val menuCard = findViewById<MaterialCardView>(menuIds[index]) ?: continue

            if (currentStep == 0) {
                menuCard.alpha = 0.3f
                menuCard.isEnabled = false
            } else if (currentStep >= 10) {
                menuCard.alpha = 1.0f
                menuCard.isEnabled = true
                menuCard.setOnClickListener { openActivity(stepNumber) }
            } else if (stepNumber <= currentStep) {
                menuCard.alpha = 1.0f
                menuCard.isEnabled = true
                menuCard.setOnClickListener { openActivity(stepNumber) }
            } else {
                menuCard.alpha = 0.3f
                menuCard.isEnabled = false
                menuCard.setOnClickListener {
                    Toast.makeText(this, "Selesaikan tahap sebelumnya!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openActivity(step: Int) {
        val intent = when (step) {
            2 -> Intent(this, MorningPrepActivity::class.java)
            3 -> Intent(this, CookingProgressActivity::class.java)
            4 -> Intent(this, AIVerificationActivity::class.java)
            5 -> Intent(this, FoodSafetyActivity::class.java)
            6 -> Intent(this, CourierHandoverActivity::class.java)
            7 -> Intent(this, VendorWadahScanActivity::class.java)
            8 -> Intent(this, SanitationActivity::class.java)
            9 -> Intent(this, FinalReportActivity::class.java)
            else -> null
        }
        intent?.let { startActivity(it) }
    }

    private fun setupBottomNav() {
        bottomNav?.selectedItemId = R.id.nav_home
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_schedule -> {
                    startActivity(Intent(this, ScheduleListActivity::class.java))
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, VendorReportsActivity::class.java))
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupLogout() {
        findViewById<View>(R.id.btnLogoutVendor)?.setOnClickListener {
            showCustomConfirmationDialog(
                title = "LOGOUT",
                message = "Apakah Anda yakin ingin mengakhiri sesi?",
                confirmText = "YA, KELUAR",
                iconRes = android.R.drawable.ic_lock_power_off
            ) {
                auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

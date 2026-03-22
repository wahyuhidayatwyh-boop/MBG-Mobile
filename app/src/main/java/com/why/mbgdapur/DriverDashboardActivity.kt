package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.why.mbgdapur.databinding.DriverDashboardBinding
import java.text.SimpleDateFormat
import java.util.*

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var binding: DriverDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private var profileListener: ListenerRegistration? = null
    private var routesListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null
    private var sanitationListener: ListenerRegistration? = null

    private var destinationsList: List<DocumentSnapshot> = emptyList()
    private var logsMap: Map<String, DocumentSnapshot> = emptyMap()
    private var currentHandoverStatus: String = "WAITING"
    private var currentDeliveryStatus: String = "WAITING"
    private var isSanitationDone: Boolean = false
    private var activeSchoolEmail: String? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { performRefreshUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, DriverLoginActivity::class.java))
            finish()
            return
        }

        setupBottomNavigation()
        setupDebugReset()
        
        binding.btnRefreshDashboard.setOnClickListener {
            refreshDataManually()
        }
    }

    private fun refreshDataManually() {
        Toast.makeText(this, "Memperbarui data...", Toast.LENGTH_SHORT).show()
        stopListeners()
        loadDriverProfile()
    }

    private fun setupDebugReset() {
        binding.tvDriverName.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Debug: Reset Data?")
                .setMessage("Semua status dan log pengiriman hari ini akan dihapus untuk mengulang alur dari awal.")
                .setPositiveButton("Reset") { _, _ -> forceResetAllData() }
                .setNegativeButton("Batal", null)
                .show()
            true
        }
    }

    private fun forceResetAllData() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val vendorId = "F5DGGLr93pySLzMe6std"
        
        val driverRef = db.collection("drivers").document(userEmail)
        driverRef.update(mapOf(
            "statusHandover" to "WAITING",
            "statusDelivery" to "WAITING",
            "currentNavigatingSchool" to "",
            "lastUpdate" to FieldValue.serverTimestamp()
        ))

        // Reset Sanitasi juga
        db.collection("vendors").document(vendorId).collection("daily_schedules").document(today)
            .collection("sanitasi_info").document("automatic").delete()

        driverRef.collection("delivery_logs").get().addOnSuccessListener { logs ->
            val batch = db.batch()
            for (log in logs) {
                if (log.id.startsWith(today)) {
                    batch.delete(log.reference)
                }
            }
            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "Data Berhasil Dibersihkan!", Toast.LENGTH_SHORT).show()
                refreshDataManually()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        loadDriverProfile()
    }

    override fun onStop() {
        super.onStop()
        stopListeners()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun stopListeners() {
        profileListener?.remove()
        routesListener?.remove()
        logsListener?.remove()
        sanitationListener?.remove()
        profileListener = null
        routesListener = null
        logsListener = null
        sanitationListener = null
    }

    private fun loadDriverProfile() {
        val userEmail = auth.currentUser?.email ?: return
        
        profileListener = db.collection("drivers").document(userEmail).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val lastUpdate = doc.getTimestamp("lastUpdate")?.toDate()
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val todayStart = calendar.time

                if (lastUpdate != null && lastUpdate.before(todayStart)) {
                    forceResetAllData()
                    return@addSnapshotListener
                }

                val name = doc.getString("name") ?: "Mitra Driver"
                val rating = doc.get("averageRating")?.toString() ?: "0.0"
                val sla = doc.get("slaScore")?.toString() ?: "100%"
                val vendorEmail = doc.getString("assignedVendorEmail")

                currentHandoverStatus = doc.getString("statusHandover") ?: "WAITING"
                currentDeliveryStatus = doc.getString("statusDelivery") ?: "WAITING"
                activeSchoolEmail = doc.getString("currentNavigatingSchool")

                updateUI(name, rating, sla)
                observeSanitationStatus()

                if (vendorEmail != null) {
                    observeDeliveryRoutes(vendorEmail)
                }
            }
        }
    }

    private fun observeSanitationStatus() {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val vendorId = "F5DGGLr93pySLzMe6std"
        
        sanitationListener?.remove()
        sanitationListener = db.collection("vendors").document(vendorId)
            .collection("daily_schedules").document(today)
            .collection("sanitasi_info").document("automatic")
            .addSnapshotListener { doc, _ ->
                isSanitationDone = doc?.getString("status") == "SELESAI"
                updateFlowUI(currentHandoverStatus, currentDeliveryStatus)
                triggerRefresh()
            }
    }

    private fun updateFlowUI(handover: String, delivery: String) {
        val userEmail = auth.currentUser?.email ?: return
        
        when {
            delivery == "FINISHED_ALL" -> {
                if (isSanitationDone) {
                    binding.tvTaskTitle.text = "Tugas Hari Ini Selesai!"
                    binding.btnMainAction.text = "LIHAT LAPORAN AKHIR"
                    binding.btnMainAction.isEnabled = true
                    binding.btnMainAction.alpha = 1.0f
                    binding.btnMainAction.setOnClickListener {
                        startActivity(Intent(this, FinalReportActivity::class.java))
                    }
                } else {
                    binding.tvTaskTitle.text = "Lakukan Sanitasi Dapur"
                    binding.btnMainAction.text = "ISI FORM SANITASI"
                    binding.btnMainAction.isEnabled = true
                    binding.btnMainAction.alpha = 1.0f
                    binding.btnMainAction.setOnClickListener {
                        startActivity(Intent(this, SanitationActivity::class.java))
                    }
                }
            }
            delivery == "RETUR_DONE" -> {
                binding.tvTaskTitle.text = "Kembalikan Boks ke Dapur"
                binding.btnMainAction.text = "TAMPILKAN QR PENGEMBALIAN"
                binding.btnMainAction.isEnabled = true
                binding.btnMainAction.alpha = 1.0f
                binding.btnMainAction.setOnClickListener {
                    val intent = Intent(this, DriverStandbyActivity::class.java)
                    intent.putExtra("IS_CLOSING_SHIFT", true)
                    startActivity(intent)
                }
            }
            delivery == "START_RETUR" -> {
                binding.tvTaskTitle.text = "Siap Jemput Boks Kotor?"
                binding.btnMainAction.text = "MULAI PENJEMPUTAN"
                binding.btnMainAction.isEnabled = true
                binding.btnMainAction.alpha = 1.0f
                binding.btnMainAction.setOnClickListener {
                    db.collection("drivers").document(userEmail).update(mapOf(
                        "statusDelivery" to "READY_RETUR",
                        "lastUpdate" to FieldValue.serverTimestamp()
                    ))
                }
            }
            delivery == "READY_RETUR" -> {
                binding.tvTaskTitle.text = "Silakan Jemput Boks Kotor"
                binding.btnMainAction.text = "PENJEMPUTAN TERBUKA"
                binding.btnMainAction.isEnabled = false
                binding.btnMainAction.alpha = 0.5f
            }
            handover == "COMPLETED" -> {
                binding.tvTaskTitle.text = "Silakan Pilih Rute Pengantaran"
                binding.btnMainAction.text = "PENGANTARAN TERBUKA"
                binding.btnMainAction.isEnabled = false
                binding.btnMainAction.alpha = 0.5f
            }
            handover == "VERIFIED" -> {
                binding.tvTaskTitle.text = "Menunggu Pengisian Manifest"
                binding.btnMainAction.text = "ISI MANIFEST SEKARANG"
                binding.btnMainAction.isEnabled = true
                binding.btnMainAction.alpha = 1.0f
                binding.btnMainAction.setOnClickListener {
                    startActivity(Intent(this, DriverManifestActivity::class.java))
                }
            }
            else -> {
                binding.tvTaskTitle.text = "Lakukan Verifikasi di Dapur"
                binding.btnMainAction.text = "TAMPILKAN QR VERIFIKASI"
                binding.btnMainAction.isEnabled = true
                binding.btnMainAction.alpha = 1.0f
                binding.btnMainAction.setOnClickListener {
                    startActivity(Intent(this, DriverStandbyActivity::class.java))
                }
            }
        }
    }

    private fun observeDeliveryRoutes(vendorEmail: String) {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val userEmail = auth.currentUser?.email ?: return

        db.collection("vendors").whereEqualTo("email", vendorEmail).get()
            .addOnSuccessListener { vendors ->
                if (vendors.isEmpty) return@addOnSuccessListener
                val vendorRef = vendors.documents[0].reference
                
                if (routesListener == null) {
                    routesListener = vendorRef.collection("daily_schedules").document(today)
                        .collection("destinations").orderBy("schoolName")
                        .addSnapshotListener { destinations, _ ->
                            if (destinations != null) {
                                destinationsList = destinations.documents
                                triggerRefresh()
                            }
                        }
                }

                if (logsListener == null) {
                    logsListener = db.collection("drivers").document(userEmail)
                        .collection("delivery_logs").addSnapshotListener { logs, _ ->
                            if (logs != null) {
                                logsMap = logs.documents.associate { it.id to it }
                                triggerRefresh()
                            }
                        }
                }
            }
    }

    private fun triggerRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.postDelayed(refreshRunnable, 100)
    }

    private fun performRefreshUI() {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        if (destinationsList.isEmpty()) {
            binding.containerRoutes.removeAllViews()
            binding.tvEmptyRoute.visibility = View.VISIBLE
            return
        }
        binding.tvEmptyRoute.visibility = View.GONE
        binding.containerRoutes.removeAllViews()

        val isReturMode = currentDeliveryStatus.contains("RETUR") || currentDeliveryStatus == "READY_RETUR" || currentDeliveryStatus == "FINISHED_ALL"
        binding.tvRouteHeader.text = if (isReturMode) "DAFTAR RUTE PENJEMPUTAN" else "DAFTAR RUTE PENGIRIMAN"

        destinationsList.forEachIndexed { index, doc ->
            val schoolEmail = doc.id
            val logDoc = logsMap["${today}_$schoolEmail"]
            
            val status = if (isReturMode) {
                logDoc?.getString("returStatus") ?: "WAITING"
            } else {
                logDoc?.getString("status") ?: "WAITING"
            }
            
            val isUnlocked = if (isReturMode) {
                currentDeliveryStatus == "READY_RETUR" || schoolEmail == activeSchoolEmail || currentDeliveryStatus.contains("RETUR") || currentDeliveryStatus == "FINISHED_ALL"
            } else {
                currentHandoverStatus == "COMPLETED" || schoolEmail == activeSchoolEmail
            }

            val porsi = doc.getLong("targetPorsi") ?: 0

            if (isReturMode) {
                addRouteCard(doc.getString("schoolName") ?: "Sekolah", schoolEmail, porsi, status, isUnlocked, index, true)
            } else {
                if (status != "DONE") {
                    addRouteCard(doc.getString("schoolName") ?: "Sekolah", schoolEmail, porsi, status, isUnlocked, index, false)
                }
            }
        }
    }

    private fun addRouteCard(schoolName: String, schoolEmail: String, porsi: Long, status: String, isUnlocked: Boolean, index: Int, isRetur: Boolean) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_route_card, binding.containerRoutes, false)
        val tvName = view.findViewById<TextView>(R.id.tvSchoolNameRoute)
        val tvDetail = view.findViewById<TextView>(R.id.tvRouteDetail)
        val ivStatus = view.findViewById<ImageView>(R.id.ivRouteStatus)

        tvName.text = schoolName
        tvDetail.text = if (isRetur) "Jemput $porsi Boks Kotor • Status: $status" else "$porsi Boks • Status: $status"
        
        val isAnyTaskActive = (currentDeliveryStatus.contains("OTW") || currentDeliveryStatus.contains("ARRIVED")) && activeSchoolEmail != null
        val isThisTheActiveTask = schoolEmail == activeSchoolEmail

        if (!isUnlocked) {
            view.alpha = 0.4f
            ivStatus.setColorFilter(android.graphics.Color.GRAY)
            view.setOnClickListener {
                Toast.makeText(this, "Selesaikan tugas di sekolah aktif terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (isAnyTaskActive && !isThisTheActiveTask && (status != "DONE" && status != "FINISHED_ALL")) {
                view.alpha = 0.4f
                view.setOnClickListener {
                    Toast.makeText(this, "Selesaikan tugas di sekolah sebelumnya", Toast.LENGTH_SHORT).show()
                }
            } else {
                when (status) {
                    "DONE" -> {
                        ivStatus.setColorFilter(android.graphics.Color.parseColor("#16A34A"))
                        view.alpha = 0.6f
                        view.setOnClickListener { Toast.makeText(this, "Sudah selesai", Toast.LENGTH_SHORT).show() }
                    }
                    "ARRIVED" -> {
                        ivStatus.setColorFilter(android.graphics.Color.parseColor("#0284C7"))
                        view.setOnClickListener { openNavigation(schoolEmail, index, isRetur) }
                    }
                    "OTW" -> {
                        ivStatus.setColorFilter(android.graphics.Color.parseColor("#FBC02D"))
                        view.setOnClickListener { openNavigation(schoolEmail, index, isRetur) }
                    }
                    else -> { // WAITING
                        ivStatus.setColorFilter(android.graphics.Color.parseColor("#94A3B8"))
                        view.setOnClickListener {
                            if (currentDeliveryStatus == "FINISHED_ALL") {
                                Toast.makeText(this, "Tugas sudah selesai", Toast.LENGTH_SHORT).show()
                            } else {
                                showStartConfirmation(schoolName, schoolEmail, index, isRetur, porsi)
                            }
                        }
                    }
                }
            }
        }
        binding.containerRoutes.addView(view)
    }

    private fun showStartConfirmation(schoolName: String, schoolEmail: String, index: Int, isRetur: Boolean, porsi: Long) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delivery, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogIcon)

        val actionText = if (isRetur) "Penjemputan" else "Pengantaran"
        tvTitle.text = "Konfirmasi $actionText"
        tvMessage.text = if (isRetur) "Apakah Anda yakin ingin menjemput $porsi boks kotor di $schoolName?"
                         else "Apakah Anda yakin ingin memulai pengantaran $porsi boks ke $schoolName?"

        if (isRetur) {
            ivIcon.setImageResource(android.R.drawable.ic_lock_idle_alarm)
            ivIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"))
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            openNavigation(schoolEmail, index, isRetur)
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun openNavigation(schoolEmail: String, stopIndex: Int, isRetur: Boolean) {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        val batch = db.batch()
        val driverRef = db.collection("drivers").document(userEmail)
        val newStatus = if (isRetur) "RETUR_OTW_STOP_${stopIndex + 1}" else "OTW_STOP_${stopIndex + 1}"

        batch.update(driverRef, mapOf(
            "currentNavigatingSchool" to schoolEmail,
            "statusDelivery" to newStatus,
            "lastUpdate" to FieldValue.serverTimestamp()
        ))

        val logRef = driverRef.collection("delivery_logs").document("${today}_$schoolEmail")
        val logData = if (isRetur) mapOf("returStatus" to "OTW", "updatedAt" to FieldValue.serverTimestamp())
                       else mapOf("status" to "OTW", "updatedAt" to FieldValue.serverTimestamp())

        batch.set(logRef, logData, SetOptions.merge())

        batch.commit().addOnSuccessListener {
            val intent = Intent(this, DriverNavigationActivity::class.java)
            intent.putExtra("TARGET_SCHOOL_EMAIL", schoolEmail)
            intent.putExtra("STOP_NUMBER", stopIndex + 1)
            intent.putExtra("IS_RETUR_MODE", isRetur)
            startActivity(intent)
        }
    }

    private fun updateUI(name: String?, rating: String?, sla: String?) {
        binding.tvDriverName.text = name ?: "Mitra Driver"
        binding.tvRating.text = "⭐ ${rating ?: "0.0"}"
        binding.tvSlaScore.text = sla ?: "100%"
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.menu_dashboard
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_dashboard -> true
                R.id.menu_help -> {
                    startActivity(Intent(this, DriverHelpActivity::class.java))
                    finish()
                    true
                }
                R.id.menu_profile -> {
                    startActivity(Intent(this, DriverProfileActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}

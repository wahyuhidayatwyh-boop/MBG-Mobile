package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration

class SchoolDashboardActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var currentVendorEmail: String? = null
    private var currentVendorDocId: String? = null
    private var currentDriverEmail: String? = null
    private var currentOrderDateId: String = ""

    private lateinit var navBeranda: View
    private lateinit var navMenu: View
    private lateinit var navHistory: View
    private lateinit var navAkun: View
    private lateinit var containerTodayMenus: LinearLayout

    private var driverLogListener: ListenerRegistration? = null
    private var driverProfileListener: ListenerRegistration? = null
    
    private var lastLogStatus: String? = null
    private var lastDriverDeliveryStatus: String? = null
    private var lastNavigatingTo: String? = null
    private var currentIssueType: String = "NONE"
    private var lastProductionStatus: String = "SEDANG DIMASAK"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_dashboard)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        currentOrderDateId = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        navBeranda = findViewById(R.id.navBeranda)
        navMenu = findViewById(R.id.navMenu)
        navHistory = findViewById(R.id.navHistory)
        navAkun = findViewById(R.id.navAkun)
        containerTodayMenus = findViewById(R.id.containerTodayMenus)

        setupClickListeners()
        setupBottomNavigation()
        
        updateNavSelection(navBeranda)
    }

    override fun onResume() {
        super.onResume()
        loadSchoolDashboard()
    }

    override fun onPause() {
        super.onPause()
        stopStatusListeners()
    }

    private fun stopStatusListeners() {
        driverLogListener?.remove()
        driverProfileListener?.remove()
        driverLogListener = null
        driverProfileListener = null
    }

    private fun checkRatingStatus() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        if (currentOrderDateId.isEmpty()) return

        val customDocId = "${currentOrderDateId}_${userEmail.replace(".", "_")}"

        db.collection("vendor_ratings").document(customDocId).get().addOnSuccessListener { doc ->
            val tvRateLabel = findViewById<TextView>(R.id.tvRateVendorLabel) ?: return@addOnSuccessListener
            val ivRateIcon = findViewById<ImageView>(R.id.ivRateVendorIcon)
            
            if (doc.exists()) {
                tvRateLabel.text = "Lihat Rating"
                findViewById<View>(R.id.btnRateVendor).alpha = 0.7f
                ivRateIcon?.setImageResource(android.R.drawable.btn_star_big_on)
            } else {
                tvRateLabel.text = "Beri Rating"
                findViewById<View>(R.id.btnRateVendor).alpha = 1.0f
                ivRateIcon?.setImageResource(android.R.drawable.btn_star_big_off)
            }
        }
    }

    private fun setupBottomNavigation() {
        navBeranda.setOnClickListener { updateNavSelection(it) }
        navMenu.setOnClickListener {
            updateNavSelection(it)
            startActivity(Intent(this, SchoolScheduleListActivity::class.java))
        }
        navHistory.setOnClickListener {
            updateNavSelection(it)
            startActivity(Intent(this, SchoolHistoryActivity::class.java))
        }
        navAkun.setOnClickListener {
            updateNavSelection(it)
            // MEMBUKA HALAMAN PROFIL BARU
            startActivity(Intent(this, SchoolProfileActivity::class.java))
        }
    }
    
    private fun updateNavSelection(selectedView: View) {
        navBeranda.isSelected = selectedView == navBeranda
        navMenu.isSelected = selectedView == navMenu
        navHistory.isSelected = selectedView == navHistory
        navAkun.isSelected = selectedView == navAkun
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnInputSiswa).setOnClickListener { startActivity(Intent(this, SchoolInputSiswaActivity::class.java)) }
        findViewById<View>(R.id.btnAIScanFeature).setOnClickListener { startActivity(Intent(this, SchoolAICheckActivity::class.java)) }
        findViewById<View>(R.id.btnRateVendor).setOnClickListener { 
            if (currentVendorDocId != null) {
                val intent = Intent(this, SchoolRatingActivity::class.java)
                intent.putExtra("VENDOR_ID", currentVendorDocId)
                intent.putExtra("HISTORY_ID", currentOrderDateId)
                startActivity(intent)
            } else {
                startActivity(Intent(this, SchoolHistoryActivity::class.java))
                Toast.makeText(this, "Silakan pilih pesanan yang ingin dinilai di Riwayat", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<View>(R.id.btnRateDriver).setOnClickListener { startActivity(Intent(this, SchoolCourierTrackingActivity::class.java)) }
        findViewById<View>(R.id.btnHelpTop).setOnClickListener { 
            startActivity(Intent(this, HelpActivity::class.java))
        }
        findViewById<View>(R.id.btnAnomali).setOnClickListener { startActivity(Intent(this, SchoolAnomaliReportActivity::class.java)) }
        findViewById<View>(R.id.btnInputSisaFeature).setOnClickListener { startActivity(Intent(this, SchoolInputSisaActivity::class.java)) }
        findViewById<View>(R.id.btnRetur).setOnClickListener { 
            val intent = Intent(this, SchoolQRActivity::class.java)
            intent.putExtra("QR_MODE", "RETUR")
            startActivity(intent)
        }
        findViewById<View>(R.id.btnShowSchoolQR).setOnClickListener {
            val intent = Intent(this, SchoolQRActivity::class.java)
            intent.putExtra("QR_MODE", "TERIMA")
            startActivity(intent)
        }
        findViewById<View>(R.id.btnAlergiFeature).setOnClickListener { startActivity(Intent(this, SchoolAllergyActivity::class.java)) }
    }

    private fun loadSchoolDashboard() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("schools").whereEqualTo("email", userEmail).limit(1).get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                val schoolDoc = querySnapshot.documents[0]
                val schoolName = schoolDoc.getString("name") ?: "Sekolah MBG"
                currentVendorEmail = schoolDoc.getString("assignedVendorEmail")
                findViewById<TextView>(R.id.tvSchoolName).text = schoolName
                if (currentVendorEmail != null) fetchDataFromVendor(currentVendorEmail!!, today, userEmail)
            }
        }
    }

    private fun fetchDataFromVendor(vEmail: String, date: String, schoolEmail: String) {
        db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendorDocs ->
            if (!vendorDocs.isEmpty) {
                val vendorDoc = vendorDocs.documents[0]
                currentVendorDocId = vendorDoc.id
                
                vendorDoc.reference.collection("daily_schedules").document(date).get().addOnSuccessListener { schedule ->
                    if (schedule.exists()) {
                        currentOrderDateId = date
                        processDocument(schedule, schoolEmail)
                    } else {
                        vendorDoc.reference.collection("history").document(date).get().addOnSuccessListener { historyDoc ->
                            if (historyDoc.exists()) {
                                currentOrderDateId = date
                                processDocument(historyDoc, schoolEmail)
                            } else {
                                vendorDoc.reference.collection("history")
                                    .orderBy("waktu_submit", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { lastHistory ->
                                        if (!lastHistory.isEmpty) {
                                            val doc = lastHistory.documents[0]
                                            currentOrderDateId = doc.id 
                                            processDocument(doc, schoolEmail)
                                        } else {
                                            containerTodayMenus.removeAllViews()
                                            findViewById<TextView>(R.id.tvTargetInfo).text = "0 Porsi"
                                            findViewById<TextView>(R.id.tvPhaseTitle).text = "BELUM MULAI"
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processDocument(doc: DocumentSnapshot, schoolEmail: String) {
        checkRatingStatus()
        
        val menuUmum = doc.getString("menu") ?: doc.getString("nama_menu") ?: "Menu MBG"
        val menuConfig = doc.get("menuConfig") as? Map<*, *>
        val productionStatus = doc.getString("statusProduksi") ?: doc.getString("status") ?: "SEDANG DIMASAK"
        currentDriverEmail = doc.getString("driverEmail")

        doc.reference.collection("destinations").document(schoolEmail).get().addOnSuccessListener { destDoc ->
            if (destDoc.exists()) {
                containerTodayMenus.removeAllViews()

                val p13 = destDoc.getLong("p13") ?: 0L
                val pOthers = (destDoc.getLong("p46") ?: 0L) + (destDoc.getLong("pSMP") ?: 0L) + 
                              (destDoc.getLong("pSMA") ?: 0L) + (destDoc.getLong("p3B") ?: 0L)
                val totalPorsi = destDoc.getLong("targetPorsi") ?: (p13 + pOthers)
                
                findViewById<TextView>(R.id.tvTargetInfo).text = "$totalPorsi Porsi"

                if (p13 > 0) {
                    val menuA = (menuConfig?.get("CAT_13K") as? Map<*, *>)?.get("menuName")?.toString() ?: menuUmum
                    addCategoryCard("GIZI KATEGORI A", menuA, p13, "photo_CAT_13K", doc.reference)
                }

                if (pOthers > 0) {
                    val menuB = (menuConfig?.get("CAT_15K") as? Map<*, *>)?.get("menuName")?.toString() ?: menuUmum
                    addCategoryCard("GIZI KATEGORI B", menuB, pOthers, "photo_CAT_15K", doc.reference)
                }

                if (currentDriverEmail != null) {
                    listenToDriverStatus(currentDriverEmail!!, productionStatus, schoolEmail)
                } else {
                    findViewById<TextView>(R.id.tvPhaseTitle).text = productionStatus
                }
            }
        }
    }

    private fun addCategoryCard(categoryName: String, menuName: String, porsi: Long, photoDocId: String, docRef: DocumentReference) {
        val cardView = LayoutInflater.from(this).inflate(R.layout.item_today_menu_card, containerTodayMenus, false)
        
        val tvCategory = cardView.findViewById<TextView>(R.id.tvTodayCategoryLabel)
        val tvMenu = cardView.findViewById<TextView>(R.id.tvTodayMenuName)
        val tvPorsi = cardView.findViewById<TextView>(R.id.tvTodayPorsiCount)
        val ivPhoto = cardView.findViewById<ImageView>(R.id.ivTodayFoodPreview)
        val btnDetails = cardView.findViewById<MaterialButton>(R.id.btnTodayDetails)

        tvCategory.text = categoryName
        tvMenu.text = menuName
        tvPorsi.text = "$porsi Porsi"

        containerTodayMenus.addView(cardView)

        docRef.collection("media").document(photoDocId).get().addOnSuccessListener { mediaDoc ->
            val photo = mediaDoc.getString("data") ?: mediaDoc.getString("imageUrl")
            if (!photo.isNullOrEmpty()) {
                if (photo.startsWith("http")) {
                    Glide.with(this).load(photo).into(ivPhoto)
                } else {
                    try {
                        val cleanBase64 = if (photo.contains(",")) photo.split(",")[1] else photo
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        ivPhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    } catch (e: Exception) { ivPhoto.setImageResource(R.drawable.logo_mbg) }
                }
            } else {
                ivPhoto.setImageResource(R.drawable.logo_mbg)
            }
        }

        btnDetails.setOnClickListener { 
            showSchoolOrderDetailsDialog(categoryName, menuName, porsi, photoDocId) 
        }
    }

    private fun listenToDriverStatus(driverEmail: String, productionStatus: String, schoolEmail: String) {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val logId = "${today}_$schoolEmail"
        lastProductionStatus = productionStatus

        stopStatusListeners()

        driverLogListener = db.collection("drivers").document(driverEmail).collection("delivery_logs").document(logId)
            .addSnapshotListener { logSnapshot, _ ->
                lastLogStatus = logSnapshot?.getString("status")
                updatePhaseTitle(schoolEmail)
            }

        driverProfileListener = db.collection("drivers").document(driverEmail).addSnapshotListener { driverSnapshot, _ ->
            if (driverSnapshot != null && driverSnapshot.exists()) {
                lastDriverDeliveryStatus = driverSnapshot.getString("statusDelivery")
                lastNavigatingTo = driverSnapshot.getString("currentNavigatingSchool")
                currentIssueType = driverSnapshot.getString("currentIssueType") ?: "NONE"
                updatePhaseTitle(schoolEmail)
            }
        }
    }

    private fun updatePhaseTitle(schoolEmail: String) {
        val tvPhase = findViewById<TextView>(R.id.tvPhaseTitle) ?: return
        val btnHelp = findViewById<View>(R.id.btnHelpTop)
        
        if (lastLogStatus == "DONE") {
            tvPhase.text = "PESANAN DITERIMA"
            tvPhase.setTextColor(android.graphics.Color.parseColor("#103A5C"))
            btnHelp?.setBackgroundResource(0)
            return
        }

        if (currentIssueType != "NONE") {
            tvPhase.text = "KENDALA: $currentIssueType"
            tvPhase.setTextColor(android.graphics.Color.RED)
            btnHelp?.setBackgroundColor(android.graphics.Color.parseColor("#FFF1F2"))
            return
        }

        tvPhase.setTextColor(android.graphics.Color.parseColor("#103A5C"))
        btnHelp?.setBackgroundResource(0)

        val finalStatus = if (lastNavigatingTo?.lowercase() == schoolEmail.lowercase()) {
            when (lastDriverDeliveryStatus) {
                "ARRIVED" -> "DRIVER SUDAH SAMPAI"
                "OTW" -> "DALAM PENGIRIMAN"
                else -> lastProductionStatus
            }
        } else {
            if (lastProductionStatus == "DALAM PENGIRIMAN") "MENUNGGU ANTREAN" else lastProductionStatus
        }
        tvPhase.text = finalStatus
    }

    private fun showSchoolOrderDetailsDialog(category: String, menuName: String, porsi: Long, photoId: String) {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        if (currentOrderDateId.isEmpty()) return

        db.collection("schools").whereEqualTo("email", userEmail).get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                val schoolDoc = querySnapshot.documents[0]
                val vendorEmail = schoolDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
                val schoolNameFromProfile = schoolDoc.getString("name") ?: "Sekolah"

                db.collection("vendors").whereEqualTo("email", vendorEmail).get().addOnSuccessListener { vendorDocs ->
                    if (!vendorDocs.isEmpty) {
                        val vendorRef = vendorDocs.documents[0].reference
                        db.collection("vendors").document(vendorRef.id).collection("daily_schedules").document(currentOrderDateId).get().addOnSuccessListener { scheduleDoc ->
                            if (scheduleDoc.exists()) {
                                showDialogWithDoc(scheduleDoc, schoolNameFromProfile, userEmail, category, menuName, porsi, photoId)
                            } else {
                                db.collection("vendors").document(vendorRef.id).collection("history").document(currentOrderDateId).get().addOnSuccessListener { historyDoc ->
                                    if (historyDoc.exists()) {
                                        showDialogWithDoc(historyDoc, schoolNameFromProfile, userEmail, category, menuName, porsi, photoId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun showDialogWithDoc(doc: DocumentSnapshot, schoolName: String, userEmail: String, category: String, menu: String, porsi: Long, photoId: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_order_details, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme).setView(dialogView).create()

        val ivMenu = dialogView.findViewById<ImageView>(R.id.ivDialogMenuPhoto)
        val tvMenu = dialogView.findViewById<TextView>(R.id.tvDialogMenuName)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDialogDate)
        val tvDeadline = dialogView.findViewById<TextView>(R.id.tvDialogDeadline)
        val containerSchools = dialogView.findViewById<LinearLayout>(R.id.containerDialogSchools)
        val tvTotalPorsi = dialogView.findViewById<TextView>(R.id.tvDialogTotalPorsi)

        tvMenu.text = menu
        tvDate.text = "Kategori: $category"
        tvDeadline.text = doc.getString("deliveryDeadline") ?: "11:30 WIB"
        tvTotalPorsi?.text = porsi.toString()

        doc.reference.collection("media").document(photoId).get().addOnSuccessListener { mediaDoc ->
            val photo = mediaDoc.getString("data") ?: mediaDoc.getString("imageUrl")
            if (!photo.isNullOrEmpty()) {
                if (photo.startsWith("http")) {
                    Glide.with(this).load(photo).into(ivMenu)
                } else {
                    try {
                        val cleanBase64 = if (photo.contains(",")) photo.split(",")[1] else photo
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        ivMenu.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    } catch (e: Exception) {}
                }
            }
        }

        containerSchools.removeAllViews()
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_dialog_school_porsi, containerSchools, false)
        itemView.findViewById<TextView>(R.id.tvDialogSchoolName).text = schoolName
        itemView.findViewById<TextView>(R.id.tvDialogSchoolPorsi).text = "$porsi Porsi"
        containerSchools.addView(itemView)

        dialogView.findViewById<MaterialButton>(R.id.btnDialogClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}

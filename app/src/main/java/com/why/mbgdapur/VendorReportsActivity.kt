package com.why.mbgdapur

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class VendorReportsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var rvReviews: RecyclerView? = null
    private var rvAllergy: RecyclerView? = null
    private var rvSisa: RecyclerView? = null
    private var containerReviews: View? = null
    private var containerAllergy: View? = null
    private var containerSisa: View? = null
    private var containerHelp: View? = null
    private var btnTabReviews: MaterialButton? = null
    private var btnTabAllergy: MaterialButton? = null
    private var btnTabSisa: MaterialButton? = null
    private var btnTabHelp: MaterialButton? = null
    private var bottomNav: BottomNavigationView? = null

    private var currentDriverEmail: String? = null
    
    private val schoolNamesCache = HashMap<String, String>()
    private val reviewList = ArrayList<Map<String, Any>>()
    private var reviewAdapter: ReviewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vendor_reports)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        rvReviews = findViewById(R.id.rvSchoolReviews)
        rvAllergy = findViewById(R.id.rvAllergySummary)
        rvSisa = findViewById(R.id.rvSisaSummary)
        
        containerReviews = findViewById(R.id.containerReviews)
        containerAllergy = findViewById(R.id.containerAllergy)
        containerSisa = findViewById(R.id.containerSisa)
        containerHelp = findViewById(R.id.containerHelp)
        
        btnTabReviews = findViewById(R.id.btnTabReviews)
        btnTabAllergy = findViewById(R.id.btnTabAllergy)
        btnTabSisa = findViewById(R.id.btnTabSisa)
        btnTabHelp = findViewById(R.id.btnTabHelp)
        bottomNav = findViewById(R.id.bottomNavVendor)

        rvReviews?.layoutManager = LinearLayoutManager(this)
        rvAllergy?.layoutManager = LinearLayoutManager(this)
        rvSisa?.layoutManager = LinearLayoutManager(this)
        
        reviewAdapter = ReviewAdapter(reviewList) { reviewId ->
            confirmDeleteReview(reviewId)
        }
        rvReviews?.adapter = reviewAdapter

        setupTabs()
        loadReviews()
        loadAllergySummary()
        loadSisaSummary()
        setupHelpLogic()
        setupBottomNav()
        listenToEmergencyNotifications()

        showTab(1)
    }

    override fun onResume() {
        super.onResume()
        bottomNav?.selectedItemId = R.id.nav_reports
    }

    private fun setupTabs() {
        btnTabAllergy?.setOnClickListener { showTab(1) }
        btnTabSisa?.setOnClickListener { showTab(2) }
        btnTabHelp?.setOnClickListener { showTab(3) }
        btnTabReviews?.setOnClickListener { showTab(4) }
    }

    private fun showTab(index: Int) {
        containerAllergy?.visibility = if (index == 1) View.VISIBLE else View.GONE
        containerSisa?.visibility = if (index == 2) View.VISIBLE else View.GONE
        containerHelp?.visibility = if (index == 3) View.VISIBLE else View.GONE
        containerReviews?.visibility = if (index == 4) View.VISIBLE else View.GONE

        updateTabButtonStyle(btnTabAllergy, index == 1)
        updateTabButtonStyle(btnTabSisa, index == 2)
        updateTabButtonStyle(btnTabHelp, index == 3)
        updateTabButtonStyle(btnTabReviews, index == 4)
    }

    private fun updateTabButtonStyle(button: MaterialButton?, active: Boolean) {
        if (active) {
            button?.setBackgroundColor(getColor(R.color.mbg_green_primary))
            button?.setTextColor(getColor(R.color.white))
            button?.strokeWidth = 0
        } else {
            button?.setBackgroundColor(getColor(R.color.white))
            button?.setTextColor(getColor(R.color.mbg_slate_500))
            button?.setStrokeColorResource(R.color.mbg_slate_200)
            button?.strokeWidth = 1
        }
    }

    private fun setupHelpLogic() {
        findViewById<MaterialButton>(R.id.btnCallCenter)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:08123456789")))
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal melakukan panggilan", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnSendBackup)?.setOnClickListener {
            updateVendorActionToDriver("ARMADA PENGGANTI BERANGKAT")
            Toast.makeText(this, "Armada Pengganti Sedang Dikirim!", Toast.LENGTH_LONG).show()
        }

        findViewById<MaterialButton>(R.id.btnNotifySchool)?.setOnClickListener {
            Toast.makeText(this, "Sekolah Telah Diberi Tahu Tentang Keterlambatan.", Toast.LENGTH_LONG).show()
        }

        listenToAssignedDriver()
    }

    private fun updateVendorActionToDriver(actionMsg: String) {
        val driverEmail = currentDriverEmail ?: return
        db.collection("drivers").document(driverEmail).update("vendorActionStatus", actionMsg)
    }

    private fun listenToEmergencyNotifications() {
        val vendorEmail = auth.currentUser?.email ?: return
        db.collection("drivers").whereEqualTo("assignedVendorEmail", vendorEmail)
            .addSnapshotListener { snapshots, _ ->
                if (isFinishing) return@addSnapshotListener
                val hasIssue = snapshots?.any { it.getString("currentIssueType") != "NONE" } ?: false
                updateNavBadge(hasIssue)
            }
    }

    private fun updateNavBadge(hasIssue: Boolean) {
        if (hasIssue) {
            btnTabHelp?.setTextColor(Color.RED)
            btnTabHelp?.text = "Bantuan 🔴"
            bottomNav?.getOrCreateBadge(R.id.nav_reports)?.apply {
                backgroundColor = Color.RED
                isVisible = true
            }
        } else {
            btnTabHelp?.text = "Bantuan"
            if (containerHelp?.visibility != View.VISIBLE) {
                btnTabHelp?.setTextColor(getColor(R.color.mbg_slate_500))
            }
            bottomNav?.removeBadge(R.id.nav_reports)
        }
    }

    private fun listenToAssignedDriver() {
        val vendorEmail = auth.currentUser?.email ?: return
        db.collection("drivers").whereEqualTo("assignedVendorEmail", vendorEmail)
            .addSnapshotListener { snapshots, _ ->
                if (isFinishing || snapshots == null) return@addSnapshotListener

                val layoutNoDriver = findViewById<View>(R.id.layoutNoDriver)
                val layoutDeliveryActive = findViewById<View>(R.id.layoutDeliveryActive)
                val cardDriverIssue = findViewById<MaterialCardView>(R.id.cardDriverIssue)
                val labelIssue = findViewById<View>(R.id.labelIssue)
                val tvDriverName = findViewById<TextView>(R.id.tvDriverNameHelp)
                val tvDriverStatus = findViewById<TextView>(R.id.tvDriverStatusHelp)
                val btnContactDriver = findViewById<MaterialButton>(R.id.btnContactDriver)
                val tvDriverIssueMsg = findViewById<TextView>(R.id.tvDriverIssueMsg)

                if (snapshots.isEmpty) {
                    layoutNoDriver?.visibility = View.VISIBLE
                    layoutDeliveryActive?.visibility = View.GONE
                    cardDriverIssue?.visibility = View.GONE
                    labelIssue?.visibility = View.GONE
                } else {
                    layoutNoDriver?.visibility = View.GONE
                    layoutDeliveryActive?.visibility = View.VISIBLE
                    
                    val doc = snapshots.documents[0]
                    currentDriverEmail = doc.id
                    tvDriverName?.text = doc.getString("name") ?: "Driver"
                    tvDriverStatus?.text = "Status: ${doc.getString("statusDelivery") ?: "Standby"}"
                    
                    val issueMsg = doc.getString("currentIssueMsg")
                    if (!issueMsg.isNullOrEmpty() && doc.getString("currentIssueType") != "NONE") {
                        cardDriverIssue?.visibility = View.VISIBLE
                        labelIssue?.visibility = View.VISIBLE
                        tvDriverIssueMsg?.text = "LAPORAN DARURAT: $issueMsg"
                    } else {
                        cardDriverIssue?.visibility = View.GONE
                        labelIssue?.visibility = View.GONE
                    }

                    val phone = doc.getString("phone") ?: ""
                    btnContactDriver?.visibility = if (phone.isNotEmpty()) View.VISIBLE else View.GONE
                    btnContactDriver?.setOnClickListener {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))) } catch (e: Exception) {}
                    }
                }
            }
    }

    private fun loadReviews() {
        val vendorEmail = auth.currentUser?.email?.lowercase() ?: return
        
        // Resolve Vendor Document ID first
        db.collection("vendors").whereEqualTo("email", vendorEmail).get().addOnSuccessListener { vendorSnapshots ->
            if (vendorSnapshots != null && !vendorSnapshots.isEmpty) {
                val vendorDocId = vendorSnapshots.documents[0].id
                
                // Query reviews using the document ID instead of email
                db.collection("vendor_ratings")
                    .whereEqualTo("targetId", vendorDocId)
                    .addSnapshotListener { snapshots, _ ->
                        if (isFinishing || snapshots == null) return@addSnapshotListener
                        if (snapshots.isEmpty) {
                            reviewList.clear()
                            reviewAdapter?.notifyDataSetChanged()
                            return@addSnapshotListener
                        }
                        
                        val currentData = ArrayList<Map<String, Any>>()
                        var pendingFetches = snapshots.size()
                        
                        snapshots.forEach { doc ->
                            val data = doc.data.toMutableMap()
                            data["reviewId"] = doc.id
                            val fromSchool = data["fromSchool"] as? String ?: ""
                            
                            if (schoolNamesCache.containsKey(fromSchool)) {
                                data["schoolName"] = schoolNamesCache[fromSchool]!!
                                currentData.add(data)
                                pendingFetches--
                                if (pendingFetches == 0) updateAdapterList(currentData)
                            } else {
                                db.collection("schools").document(fromSchool).get().addOnSuccessListener { sDoc ->
                                    val sName = sDoc.getString("name") ?: "Sekolah"
                                    schoolNamesCache[fromSchool] = sName
                                    data["schoolName"] = sName
                                    currentData.add(data)
                                    pendingFetches--
                                    if (pendingFetches == 0) updateAdapterList(currentData)
                                }.addOnFailureListener {
                                    data["schoolName"] = "Sekolah"
                                    currentData.add(data)
                                    pendingFetches--
                                    if (pendingFetches == 0) updateAdapterList(currentData)
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun updateAdapterList(newList: ArrayList<Map<String, Any>>) {
        newList.sortByDescending { it["timestamp"] as? Long ?: 0L }
        reviewList.clear()
        reviewList.addAll(newList)
        reviewAdapter?.notifyDataSetChanged()
    }

    private fun confirmDeleteReview(reviewId: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.tvDeleteTitle).text = "Hapus Ulasan"
        dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text = "Apakah Anda yakin ingin menghapus ulasan ini? Tindakan ini tidak dapat dibatalkan."

        dialogView.findViewById<MaterialButton>(R.id.btnConfirmDelete).setOnClickListener {
            val index = reviewList.indexOfFirst { it["reviewId"] == reviewId }
            if (index != -1) {
                reviewList.removeAt(index)
                reviewAdapter?.notifyItemRemoved(index)
            }
            db.collection("vendor_ratings").document(reviewId).delete()
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadAllergySummary() {
        val vendorEmail = auth.currentUser?.email ?: return
        db.collection("schools").whereEqualTo("assignedVendorEmail", vendorEmail)
            .addSnapshotListener { schoolSnapshots, _ ->
                if (isFinishing || schoolSnapshots == null) return@addSnapshotListener
                val finalAllergyList = ArrayList<Map<String, Any>>()
                var pendingSchools = schoolSnapshots.size()
                if (pendingSchools == 0) {
                    rvAllergy?.adapter = AllergyAdapter(emptyList())
                    return@addSnapshotListener
                }
                schoolSnapshots.forEach { schoolDoc ->
                    val schoolName = schoolDoc.getString("name") ?: "Sekolah"
                    val schoolId = schoolDoc.id
                    db.collection("schools").document(schoolId)
                        .collection("informasi_alergi")
                        .orderBy("allergyLastUpdate", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { allergyDocs ->
                            if (!allergyDocs.isEmpty) {
                                val allergyDoc = allergyDocs.documents[0]
                                finalAllergyList.add(mapOf(
                                    "schoolName" to schoolName,
                                    "note" to (allergyDoc.getString("allergyNotes") ?: "Tidak ada data")
                                ))
                            } else {
                                finalAllergyList.add(mapOf("schoolName" to schoolName, "note" to "Belum ada laporan"))
                            }
                            pendingSchools--
                            if (pendingSchools == 0) rvAllergy?.adapter = AllergyAdapter(finalAllergyList)
                        }.addOnFailureListener {
                            pendingSchools--
                            if (pendingSchools == 0) rvAllergy?.adapter = AllergyAdapter(finalAllergyList)
                        }
                }
            }
    }

    private fun loadSisaSummary() {
        val vendorEmail = auth.currentUser?.email ?: return
        db.collection("schools").whereEqualTo("assignedVendorEmail", vendorEmail)
            .addSnapshotListener { schoolSnapshots, _ ->
                if (isFinishing || schoolSnapshots == null) return@addSnapshotListener
                val finalSisaList = ArrayList<Map<String, Any>>()
                var pendingSchools = schoolSnapshots.size()
                if (pendingSchools == 0) {
                    rvSisa?.adapter = SisaAdapter(emptyList())
                    return@addSnapshotListener
                }
                schoolSnapshots.forEach { schoolDoc ->
                    val schoolName = schoolDoc.getString("name") ?: "Sekolah"
                    val schoolId = schoolDoc.id
                    db.collection("schools").document(schoolId)
                        .collection("informasi_sisa")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { sisaDocs ->
                            if (!sisaDocs.isEmpty) {
                                val sisaDoc = sisaDocs.documents[0]
                                finalSisaList.add(mapOf(
                                    "schoolName" to schoolName,
                                    "jumlah" to (sisaDoc.getLong("jumlahSisa") ?: 0L),
                                    "alasan" to (sisaDoc.getString("alasan") ?: "-"),
                                    "distribusi" to (sisaDoc.getString("distribusi") ?: "-"),
                                    "date" to (sisaDoc.getString("date") ?: "")
                                ))
                            }
                            pendingSchools--
                            if (pendingSchools == 0) rvSisa?.adapter = SisaAdapter(finalSisaList)
                        }.addOnFailureListener {
                            pendingSchools--
                            if (pendingSchools == 0) rvSisa?.adapter = SisaAdapter(finalSisaList)
                        }
                }
            }
    }

    private fun setupBottomNav() {
        bottomNav?.selectedItemId = R.id.nav_reports
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, DashboardActivity::class.java)); finish(); true }
                R.id.nav_schedule -> { startActivity(Intent(this, ScheduleListActivity::class.java)); finish(); true }
                R.id.nav_reports -> true
                R.id.nav_history -> { startActivity(Intent(this, HistoryActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    class ReviewAdapter(private val items: List<Map<String, Any>>, private val onDelete: (String) -> Unit) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView? = v.findViewById(R.id.tvReviewSchoolName)
            val tvRating: TextView? = v.findViewById(R.id.tvReviewRating)
            val tvDate: TextView? = v.findViewById(R.id.tvReviewDate)
            val tvText: TextView? = v.findViewById(R.id.tvReviewText)
            val btnDelete: ImageView? = v.findViewById(R.id.btnDeleteReview)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_school_review, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = items[p]
            h.tvName?.text = item["schoolName"] as? String
            h.tvRating?.text = "⭐ ${item["rating"]}"
            val ts = item["timestamp"] as? Long ?: 0L
            h.tvDate?.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
            h.tvText?.text = item["review"] as? String
            h.btnDelete?.setOnClickListener { (item["reviewId"] as? String)?.let { onDelete(it) } }
        }
        override fun getItemCount() = items.size
    }

    class AllergyAdapter(private val items: List<Map<String, Any>>) : RecyclerView.Adapter<AllergyAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView? = v.findViewById(R.id.tvAllergySchoolName)
            val tvDesc: TextView? = v.findViewById(R.id.tvAllergyDescription)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_allergy_summary, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = items[p]
            h.tvName?.text = item["schoolName"] as? String
            h.tvDesc?.text = item["note"] as? String
        }
        override fun getItemCount() = items.size
    }

    class SisaAdapter(private val items: List<Map<String, Any>>) : RecyclerView.Adapter<SisaAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView? = v.findViewById(R.id.tvSisaSchoolName)
            val tvInfo: TextView? = v.findViewById(R.id.tvSisaInfo)
            val tvAllocation: TextView? = v.findViewById(R.id.tvSisaAllocation)
            val tvDate: TextView? = v.findViewById(R.id.tvSisaDate)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_sisa_summary, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = items[p]
            h.tvName?.text = item["schoolName"] as? String
            h.tvInfo?.text = "${item["jumlah"]} Porsi - ${item["alasan"]}"
            h.tvAllocation?.text = "Alokasi: ${item["distribusi"]}"
            h.tvDate?.text = item["date"] as? String
        }
        override fun getItemCount() = items.size
    }
}

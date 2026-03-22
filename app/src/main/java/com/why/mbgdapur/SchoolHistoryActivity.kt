package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.*
import android.graphics.BitmapFactory
import android.util.Base64
import java.text.SimpleDateFormat

class SchoolHistoryActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvTotalPorsi: TextView
    private lateinit var layoutEmpty: View
    
    private val historyList = mutableListOf<SchoolHistoryItem>()
    private lateinit var adapter: SchoolHistoryAdapter

    data class SchoolHistoryItem(
        val id: String,
        val menuName: String,
        val totalPorsi: Long,
        val porsiA: Long,
        val porsiB: Long,
        val photoA: String?,
        val photoB: String?,
        val vendorId: String,
        val date: String,
        val timestamp: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_history)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        rvHistory = findViewById(R.id.rvSchoolHistory)
        tvTotalCount = findViewById(R.id.tvTotalHistoryCount)
        tvTotalPorsi = findViewById(R.id.tvTotalHistoryPorsi)
        layoutEmpty = findViewById(R.id.layoutEmptyHistory)

        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = SchoolHistoryAdapter(historyList)
        rvHistory.adapter = adapter

        findViewById<View>(R.id.btnBackHistory).setOnClickListener { finish() }

        setupBottomNavigation()
        loadHistoryData()
    }

    private fun setupBottomNavigation() {
        val navBeranda = findViewById<View>(R.id.navBeranda)
        val navMenu = findViewById<View>(R.id.navMenu)
        val navHistory = findViewById<View>(R.id.navHistory)
        val navAkun = findViewById<View>(R.id.navAkun)

        navHistory.isSelected = true

        navBeranda.setOnClickListener {
            startActivity(Intent(this, SchoolDashboardActivity::class.java))
            finish()
        }
        navMenu.setOnClickListener {
            startActivity(Intent(this, SchoolScheduleListActivity::class.java))
            finish()
        }
        navHistory.setOnClickListener { /* Already here */ }
        navAkun.setOnClickListener {
            startActivity(Intent(this, SchoolProfileActivity::class.java))
            finish()
        }
    }

    private fun loadHistoryData() {
        val userEmail = auth.currentUser?.email?.lowercase()?.trim() ?: return
        
        db.collection("schools").whereEqualTo("email", userEmail).get().addOnSuccessListener { schools ->
            if (!schools.isEmpty) {
                val schoolDoc = schools.documents[0]
                val vendorEmail = schoolDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
                
                db.collection("vendors").whereEqualTo("email", vendorEmail).get().addOnSuccessListener { vendorDocs ->
                    if (!vendorDocs.isEmpty) {
                        val vendorDoc = vendorDocs.documents[0]
                        val vendorId = vendorDoc.id
                        
                        vendorDoc.reference.collection("history").get(Source.SERVER).addOnSuccessListener { historySnap ->
                            val allDocs = historySnap.documents.toMutableList()
                            
                            vendorDoc.reference.collection("daily_schedules").get(Source.SERVER).addOnSuccessListener { scheduleSnap ->
                                for (sDoc in scheduleSnap.documents) {
                                    if (allDocs.none { it.id == sDoc.id }) {
                                        allDocs.add(sDoc)
                                    }
                                }
                                processDocuments(allDocs, userEmail, vendorId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processDocuments(documents: List<com.google.firebase.firestore.DocumentSnapshot>, userEmail: String, vendorId: String) {
        historyList.clear()
        if (documents.isEmpty()) {
            updateUI(0, 0)
            return
        }

        var totalAllPorsi = 0L
        var processedCount = 0
        
        for (doc in documents) {
            val dateId = doc.id
            
            doc.reference.collection("destinations").document(userEmail).get(Source.SERVER).addOnSuccessListener { destDoc ->
                if (destDoc.exists()) {
                    val status = destDoc.getString("statusDelivery") ?: ""
                    
                    // PERBAIKAN: Hanya masukkan ke riwayat jika status benar-benar DONE
                    // Tidak boleh hanya mengandalkan isFromHistory karena satu tanggal di history 
                    // bisa berisi data sekolah lain yang sudah selesai sementara sekolah ini belum.
                    if (status == "DONE") {
                        val p13 = destDoc.getLong("p13") ?: 0L
                        val pOthers = (destDoc.getLong("p46") ?: 0L) + (destDoc.getLong("pSMP") ?: 0L) + 
                                      (destDoc.getLong("pSMA") ?: 0L) + (destDoc.getLong("p3B") ?: 0L)
                        val target = destDoc.getLong("targetPorsi") ?: (p13 + pOthers)

                        val menuConfig = doc.get("menuConfig") as? Map<*, *>
                        val menuA = (menuConfig?.get("CAT_13K") as? Map<*, *>)?.get("menuName")?.toString()
                        val menuB = (menuConfig?.get("CAT_15K") as? Map<*, *>)?.get("menuName")?.toString()
                        
                        var menuUmum = doc.getString("nama_menu") ?: doc.getString("menu") ?: "Menu MBG"
                        if (menuUmum.contains(",")) menuUmum = menuUmum.replace(",", " & ")

                        val displayedMenuName = when {
                            p13 > 0 && pOthers > 0 -> menuUmum
                            p13 > 0 -> menuA ?: menuUmum
                            pOthers > 0 -> menuB ?: menuUmum
                            else -> menuUmum
                        }

                        val dateTimestamp = try {
                            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(dateId)?.time ?: 0L
                        } catch (e: Exception) { 0L }

                        doc.reference.collection("media").get().addOnSuccessListener { mediaDocs ->
                            var photoA: String? = null
                            var photoB: String? = null
                            for (m in mediaDocs) {
                                if (m.id == "photo_CAT_13K") photoA = m.getString("data")
                                if (m.id == "photo_CAT_15K") photoB = m.getString("data")
                            }

                            var finalPhotoA: String? = null
                            var finalPhotoB: String? = null
                            if (p13 > 0 && pOthers > 0) {
                                finalPhotoA = photoA; finalPhotoB = photoB
                            } else if (p13 > 0) {
                                finalPhotoA = photoA
                            } else if (pOthers > 0) {
                                finalPhotoA = photoB 
                            }

                            historyList.add(SchoolHistoryItem(
                                dateId, displayedMenuName, target, p13, pOthers, finalPhotoA, finalPhotoB, vendorId, dateId, dateTimestamp
                            ))
                            totalAllPorsi += target
                            checkFinished(++processedCount, documents.size, totalAllPorsi)
                        }.addOnFailureListener {
                            checkFinished(++processedCount, documents.size, totalAllPorsi)
                        }
                    } else {
                        checkFinished(++processedCount, documents.size, totalAllPorsi)
                    }
                } else {
                    checkFinished(++processedCount, documents.size, totalAllPorsi)
                }
            }.addOnFailureListener {
                checkFinished(++processedCount, documents.size, totalAllPorsi)
            }
        }
    }

    private fun checkFinished(current: Int, total: Int, totalPorsi: Long) {
        if (current == total) {
            historyList.sortByDescending { it.timestamp }
            adapter.notifyDataSetChanged()
            updateUI(historyList.size, totalPorsi)
        }
    }

    private fun updateUI(count: Int, porsi: Long) {
        tvTotalCount.text = "$count Pesanan"
        tvTotalPorsi.text = "$porsi Porsi"
        layoutEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    inner class SchoolHistoryAdapter(private val items: List<SchoolHistoryItem>) : RecyclerView.Adapter<SchoolHistoryAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvDate: TextView = v.findViewById(R.id.tvHistoryDate)
            val tvMenu: TextView = v.findViewById(R.id.tvHistoryMenuName)
            val tvTotal: TextView = v.findViewById(R.id.tvHistoryTotalPorsi)
            val tvPorsiA: TextView = v.findViewById(R.id.tvHistoryPorsiA)
            val tvPorsiB: TextView = v.findViewById(R.id.tvHistoryPorsiB)
            val layoutGiziA: View = v.findViewById(R.id.layoutGiziA)
            val layoutGiziB: View = v.findViewById(R.id.layoutGiziB)
            val ivA: ImageView = v.findViewById(R.id.ivHistoryGiziA)
            val ivB: ImageView = v.findViewById(R.id.ivHistoryGiziB)
            val cardA: View = v.findViewById(R.id.cardImgGiziA)
            val cardB: View = v.findViewById(R.id.cardImgGiziB)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_school_history, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvDate.text = item.date
            holder.tvMenu.text = item.menuName
            holder.tvTotal.text = "${item.totalPorsi} Porsi"
            
            if (item.porsiA > 0) {
                holder.layoutGiziA.visibility = View.VISIBLE
                holder.tvPorsiA.text = item.porsiA.toString()
            } else { holder.layoutGiziA.visibility = View.GONE }
            
            if (item.porsiB > 0) {
                holder.layoutGiziB.visibility = View.VISIBLE
                holder.tvPorsiB.text = item.porsiB.toString()
            } else { holder.layoutGiziB.visibility = View.GONE }

            if (!item.photoA.isNullOrEmpty()) {
                holder.cardA.visibility = View.VISIBLE
                decodeAndShow(item.photoA, holder.ivA)
            } else { holder.cardA.visibility = View.GONE }
            
            if (!item.photoB.isNullOrEmpty()) {
                holder.cardB.visibility = View.VISIBLE
                decodeAndShow(item.photoB, holder.ivB)
            } else { holder.cardB.visibility = View.GONE }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@SchoolHistoryActivity, SchoolHistoryDetailActivity::class.java)
                intent.putExtra("HISTORY_ID", item.id)
                intent.putExtra("VENDOR_ID", item.vendorId)
                startActivity(intent)
            }
        }

        private fun decodeAndShow(base64Str: String, iv: ImageView) {
            try {
                val cleanBase64 = if (base64Str.contains(",")) base64Str.split(",")[1] else base64Str
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                iv.setImageBitmap(bitmap)
            } catch (e: Exception) { iv.setImageResource(R.drawable.logo_mbg) }
        }

        override fun getItemCount() = items.size
    }
}

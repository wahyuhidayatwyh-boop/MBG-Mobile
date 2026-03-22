package com.why.mbgdapur

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class SchoolScheduleListActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var rvUpcoming: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var chipGroup: ChipGroup
    
    private val allSchedules = mutableListOf<ScheduleItem>()
    private val filteredList = mutableListOf<ScheduleItem>()
    private lateinit var adapter: SchoolScheduleAdapter
    
    private var currentFilter: String = "ALL"

    data class ScheduleItem(
        val id: String,
        val doc: com.google.firebase.firestore.DocumentSnapshot,
        val dateStr: String,
        val vendorId: String,
        val category: String 
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_schedule_list)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        rvUpcoming = findViewById(R.id.rvSchoolUpcomingSchedules)
        tvEmpty = findViewById(R.id.tvSchoolEmptySchedule)
        chipGroup = findViewById(R.id.chipGroupFilter)

        rvUpcoming.layoutManager = GridLayoutManager(this, 2)
        adapter = SchoolScheduleAdapter(filteredList)
        rvUpcoming.adapter = adapter

        findViewById<View>(R.id.btnBackSchoolSchedule).setOnClickListener { finish() }

        setupFilter()
        setupBottomNavigation()
        loadUpcomingSchedules()
    }

    private fun setupBottomNavigation() {
        val navBeranda = findViewById<View>(R.id.navBeranda)
        val navMenu = findViewById<View>(R.id.navMenu)
        val navHistory = findViewById<View>(R.id.navHistory)
        val navAkun = findViewById<View>(R.id.navAkun)

        navMenu.isSelected = true

        navBeranda.setOnClickListener {
            startActivity(Intent(this, SchoolDashboardActivity::class.java))
            finish()
        }
        navMenu.setOnClickListener { /* Already here */ }
        navHistory.setOnClickListener {
            startActivity(Intent(this, SchoolHistoryActivity::class.java))
            finish()
        }
        navAkun.setOnClickListener {
            startActivity(Intent(this, SchoolProfileActivity::class.java))
            finish()
        }
    }

    private fun setupFilter() {
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipGiziA -> "CAT_13K"
                R.id.chipGiziB -> "CAT_15K"
                else -> "ALL"
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        filteredList.clear()
        if (currentFilter == "ALL") {
            filteredList.addAll(allSchedules)
        } else {
            allSchedules.forEach { item ->
                if (item.category == currentFilter) {
                    filteredList.add(item)
                }
            }
        }
        
        tvEmpty.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun loadUpcomingSchedules() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        
        db.collection("schools").whereEqualTo("email", userEmail).get().addOnSuccessListener { schools ->
            if (!schools.isEmpty) {
                val schoolDoc = schools.documents[0]
                val vendorEmail = schoolDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
                
                db.collection("vendors").whereEqualTo("email", vendorEmail).get().addOnSuccessListener { vendorDocs ->
                    if (!vendorDocs.isEmpty) {
                        val vendorDoc = vendorDocs.documents[0]
                        val vendorRef = vendorDoc.reference
                        
                        vendorRef.collection("daily_schedules").get().addOnSuccessListener { snapshots ->
                            allSchedules.clear()
                            
                            val todayStr = sdf.format(Date())
                            val today = sdf.parse(todayStr) ?: Date()

                            val validDocs = snapshots.documents.filter { doc ->
                                try {
                                    val d = sdf.parse(doc.id)
                                    d != null && d.after(today)
                                } catch (e: Exception) { false }
                            }

                            if (validDocs.isEmpty()) {
                                applyFilter()
                                return@addOnSuccessListener
                            }

                            var processed = 0
                            for (doc in validDocs) {
                                doc.reference.collection("destinations").document(userEmail).get().addOnSuccessListener { destDoc ->
                                    if (destDoc.exists()) {
                                        val p13 = destDoc.getLong("p13") ?: 0L
                                        val pOthers = (destDoc.getLong("p46") ?: 0L) + 
                                                      (destDoc.getLong("pSMP") ?: 0L) + 
                                                      (destDoc.getLong("pSMA") ?: 0L) + 
                                                      (destDoc.getLong("p3B") ?: 0L)

                                        if (p13 > 0) {
                                            allSchedules.add(ScheduleItem(doc.id, doc, doc.id, vendorDoc.id, "CAT_13K"))
                                        }
                                        if (pOthers > 0) {
                                            allSchedules.add(ScheduleItem(doc.id, doc, doc.id, vendorDoc.id, "CAT_15K"))
                                        }
                                    }

                                    processed++
                                    if (processed == validDocs.size) {
                                        allSchedules.sortWith(compareBy({ try { sdf.parse(it.dateStr) } catch(e:Exception){null} }, { it.category }))
                                        applyFilter()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inner class SchoolScheduleAdapter(private val items: List<ScheduleItem>) : RecyclerView.Adapter<SchoolScheduleAdapter.ViewHolder>() {
        
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val viewPager: ViewPager2 = v.findViewById(R.id.viewPagerSchedule)
            val tvDate: TextView = v.findViewById(R.id.tvItemDate)
            val tvSummary: TextView = v.findViewById(R.id.tvMainSummary)
            val tvCategoryLabel: TextView = v.findViewById(R.id.tvLabelCategory)
            val tvLihatDetail: TextView = v.findViewById(R.id.tvLihatDetailLabel)

            init {
                v.findViewById<View>(R.id.btnEditItem).visibility = View.GONE
                v.findViewById<View>(R.id.btnDeleteItem).visibility = View.GONE
                
                tvCategoryLabel.visibility = View.VISIBLE
                tvLihatDetail.visibility = View.VISIBLE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_card, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val doc = item.doc
            
            holder.tvDate.text = item.dateStr
            holder.tvCategoryLabel.text = if (item.category == "CAT_13K") "GIZI KATEGORI A" else "GIZI KATEGORI B"

            val menuConfig = doc.get("menuConfig") as? Map<*, *>
            val menuUmum = doc.getString("menu") ?: doc.getString("nama_menu") ?: "Menu MBG"
            val catConfig = menuConfig?.get(item.category) as? Map<*, *>
            holder.tvSummary.text = catConfig?.get("menuName")?.toString() ?: menuUmum

            val pages = listOf(PageData(item.category))
            holder.viewPager.adapter = SwipeAdapter(pages, doc.reference)
            
            holder.itemView.setOnClickListener {
                val intent = Intent(this@SchoolScheduleListActivity, SchoolOrderDetailActivity::class.java)
                intent.putExtra("SCHEDULE_ID", item.id)
                intent.putExtra("VENDOR_DOC_ID", item.vendorId)
                intent.putExtra("CATEGORY_FILTER", item.category)
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }

    data class PageData(val catId: String)

    inner class SwipeAdapter(private val items: List<PageData>, private val scheduleRef: com.google.firebase.firestore.DocumentReference) : 
        RecyclerView.Adapter<SwipeAdapter.VH>() {
        
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView = v.findViewById(R.id.ivPagePhoto)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_swipe_page, parent, false)
            v.findViewById<View>(R.id.tvPageGiziLabel).visibility = View.GONE
            v.findViewById<View>(R.id.tvPageMenuName).visibility = View.GONE
            v.findViewById<View>(R.id.tvPageTotalPorsi).visibility = View.GONE
            v.findViewById<View>(R.id.tvPageSchools).visibility = View.GONE
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            scheduleRef.collection("media").document("photo_${item.catId}").get().addOnSuccessListener { photoDoc ->
                if (photoDoc.exists()) {
                    val base64 = photoDoc.getString("data")
                    if (base64 != null) {
                        try {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            holder.iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        } catch (e: Exception) {}
                    }
                } else {
                    holder.iv.setImageResource(R.drawable.logo_mbg)
                }
            }
        }
        override fun getItemCount() = items.size
    }
}

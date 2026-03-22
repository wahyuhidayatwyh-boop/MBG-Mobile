package com.why.mbgdapur

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class ScheduleListActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var rvToday: RecyclerView
    private lateinit var rvUpcoming: RecyclerView
    private lateinit var headerToday: TextView
    private lateinit var headerUpcoming: TextView
    private lateinit var tvEmpty: TextView
    private var bottomNav: BottomNavigationView? = null
    
    private var scheduleListener: ListenerRegistration? = null
    private val todayList = mutableListOf<ScheduleItem>()
    private val upcomingList = mutableListOf<ScheduleItem>()
    
    private lateinit var adapterToday: ScheduleAdapter
    private lateinit var adapterUpcoming: ScheduleAdapter

    data class ScheduleItem(
        val id: String,
        val doc: com.google.firebase.firestore.DocumentSnapshot,
        val dateStr: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_list)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        rvToday = findViewById(R.id.rvTodaySchedules)
        rvUpcoming = findViewById(R.id.rvUpcomingSchedules)
        headerToday = findViewById(R.id.headerToday)
        headerUpcoming = findViewById(R.id.headerUpcoming)
        tvEmpty = findViewById(R.id.tvEmptySchedule)
        bottomNav = findViewById(R.id.bottomNavVendor)

        rvToday.layoutManager = GridLayoutManager(this, 2)
        rvUpcoming.layoutManager = GridLayoutManager(this, 2)

        adapterToday = ScheduleAdapter(todayList)
        adapterUpcoming = ScheduleAdapter(upcomingList)

        rvToday.adapter = adapterToday
        rvUpcoming.adapter = adapterUpcoming

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        setupBottomNav()
        loadSavedSchedules()
    }

    override fun onResume() {
        super.onResume()
        bottomNav?.selectedItemId = R.id.nav_schedule
    }

    private fun setupBottomNav() {
        bottomNav?.selectedItemId = R.id.nav_schedule
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_schedule -> true
                R.id.nav_reports -> {
                    startActivity(Intent(this, VendorReportsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSavedSchedules() {
        val email = auth.currentUser?.email ?: return
        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { vendorDocs ->
            if (!vendorDocs.isEmpty) {
                val vendorRef = vendorDocs.documents[0].reference
                scheduleListener = vendorRef.collection("daily_schedules")
                    .addSnapshotListener { snapshots, e ->
                        if (e != null) return@addSnapshotListener
                        
                        todayList.clear()
                        upcomingList.clear()
                        
                        if (snapshots == null || snapshots.isEmpty) {
                            updateUI()
                            return@addSnapshotListener
                        }

                        val todayDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        val todayDate = sdf.parse(todayDateStr) ?: Date()

                        val sortedDocs = snapshots.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }

                        for (doc in sortedDocs) {
                            val scheduleDateStr = doc.id
                            val item = ScheduleItem(doc.id, doc, scheduleDateStr)
                            try {
                                val scheduleDate = sdf.parse(scheduleDateStr)
                                if (scheduleDateStr == todayDateStr) {
                                    todayList.add(item)
                                } else if (scheduleDate != null && scheduleDate.after(todayDate)) {
                                    upcomingList.add(item)
                                }
                            } catch (ex: Exception) {
                                upcomingList.add(item)
                            }
                        }
                        updateUI()
                    }
            }
        }
    }

    private fun updateUI() {
        headerToday.visibility = if (todayList.isNotEmpty()) View.VISIBLE else View.GONE
        headerUpcoming.visibility = if (upcomingList.isNotEmpty()) View.VISIBLE else View.GONE
        tvEmpty.visibility = if (todayList.isEmpty() && upcomingList.isEmpty()) View.VISIBLE else View.GONE
        
        adapterToday.notifyDataSetChanged()
        adapterUpcoming.notifyDataSetChanged()
    }

    inner class ScheduleAdapter(private val items: List<ScheduleItem>) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {
        
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val viewPager: ViewPager2 = v.findViewById(R.id.viewPagerSchedule)
            val tvDate: TextView = v.findViewById(R.id.tvItemDate)
            val tvSummary: TextView = v.findViewById(R.id.tvMainSummary)
            val btnEdit: View = v.findViewById(R.id.btnEditItem)
            val btnDelete: View = v.findViewById(R.id.btnDeleteItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule_card, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val doc = item.doc
            
            holder.tvDate.text = item.dateStr
            
            val targetPorsi = doc.getLong("targetPorsi") ?: 0L
            val menu = doc.getString("menu") ?: "Menu belum diatur"
            holder.tvSummary.text = "$targetPorsi Porsi • $menu"

            // Setup ViewPager Mini
            val menuConfig = doc.get("menuConfig") as? Map<String, Any>
            val pages = mutableListOf<SchedulePageData>()

            val cat13 = menuConfig?.get("CAT_13K") as? Map<String, Any>
            if (cat13 != null) pages.add(SchedulePageData(cat13["menuName"] as? String ?: "-", doc.id, "CAT_13K"))
            
            val cat15 = menuConfig?.get("CAT_15K") as? Map<String, Any>
            if (cat15 != null) pages.add(SchedulePageData(cat15["menuName"] as? String ?: "-", doc.id, "CAT_15K"))
            
            holder.viewPager.adapter = SwipeMiniAdapter(pages, doc.reference)

            holder.btnEdit.setOnClickListener {
                val intent = Intent(this@ScheduleListActivity, ScheduleActivity::class.java)
                intent.putExtra("EDIT_DATE", item.id)
                startActivity(intent)
            }

            holder.btnDelete.setOnClickListener {
                showDeleteDialog(item)
            }
        }

        private fun showDeleteDialog(item: ScheduleItem) {
            val dialogView = LayoutInflater.from(this@ScheduleListActivity).inflate(R.layout.dialog_delete_confirmation, null)
            val dialog = AlertDialog.Builder(this@ScheduleListActivity)
                .setView(dialogView)
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text = "Hapus perencanaan jadwal untuk tanggal ${item.dateStr}?"

            dialogView.findViewById<MaterialButton>(R.id.btnConfirmDelete).setOnClickListener {
                item.doc.reference.delete()
                dialog.dismiss()
            }

            dialogView.findViewById<MaterialButton>(R.id.btnCancelDelete).setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }

        override fun getItemCount() = items.size
    }

    data class SchedulePageData(val menuName: String, val date: String, val catId: String, var photoBase64: String? = null)

    inner class SwipeMiniAdapter(private val items: List<SchedulePageData>, private val scheduleRef: com.google.firebase.firestore.DocumentReference) : 
        RecyclerView.Adapter<SwipeMiniAdapter.VH>() {
        
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
            if (item.photoBase64 == null) {
                scheduleRef.collection("media").document("photo_${item.catId}").get().addOnSuccessListener { photoDoc ->
                    if (photoDoc.exists()) {
                        val base64 = photoDoc.getString("data")
                        item.photoBase64 = base64
                        if (base64 != null) {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            holder.iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        }
                    } else {
                        holder.iv.setImageResource(R.drawable.logo_mbg)
                    }
                }
            } else {
                val bytes = Base64.decode(item.photoBase64, Base64.DEFAULT)
                holder.iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }
        }
        override fun getItemCount() = items.size
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleListener?.remove()
    }
}

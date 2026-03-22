package com.why.mbgdapur

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class SchoolOrderDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var viewPager: ViewPager2
    private val photoList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_order_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        viewPager = findViewById(R.id.vpOrderMenuPhotos)
        findViewById<View>(R.id.btnBackOrderDetail).setOnClickListener { finish() }

        val scheduleId = intent.getStringExtra("SCHEDULE_ID")
        val vendorId = intent.getStringExtra("VENDOR_DOC_ID")
        val categoryFilter = intent.getStringExtra("CATEGORY_FILTER") ?: "ALL"

        if (scheduleId != null && vendorId != null) {
            loadOrderDetail(vendorId, scheduleId, categoryFilter)
        }
    }

    private fun loadOrderDetail(vendorId: String, scheduleId: String, categoryFilter: String) {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        findViewById<TextView>(R.id.tvOrderDate).text = "Produksi: $scheduleId"

        val vendorRef = db.collection("vendors").document(vendorId)
        val scheduleRef = vendorRef.collection("daily_schedules").document(scheduleId)

        vendorRef.get().addOnSuccessListener { vendorDoc ->
            findViewById<TextView>(R.id.tvOrderVendorName).text = vendorDoc.getString("name") ?: "Vendor MBG"
        }

        scheduleRef.get().addOnSuccessListener { scheduleDoc ->
            if (!scheduleDoc.exists()) return@addOnSuccessListener

            val menuConfig = scheduleDoc.get("menuConfig") as? Map<*, *>
            val menuUmum = scheduleDoc.getString("menu") ?: scheduleDoc.getString("nama_menu") ?: "Menu MBG"
            
            if (categoryFilter != "ALL") {
                val catConfig = menuConfig?.get(categoryFilter) as? Map<*, *>
                findViewById<TextView>(R.id.tvOrderMenuName).text = catConfig?.get("menuName")?.toString() ?: menuUmum
            } else {
                findViewById<TextView>(R.id.tvOrderMenuName).text = menuUmum
            }

            scheduleRef.collection("destinations").document(userEmail).get().addOnSuccessListener { destDoc ->
                if (destDoc.exists()) {
                    val p13 = destDoc.getLong("p13") ?: 0L
                    val pOthers = (destDoc.getLong("p46") ?: 0L) + (destDoc.getLong("pSMP") ?: 0L) + 
                                  (destDoc.getLong("pSMA") ?: 0L) + (destDoc.getLong("p3B") ?: 0L)

                    if (categoryFilter == "CAT_13K") {
                        findViewById<View>(R.id.layoutOrderGiziA).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvOrderPorsiA).text = "$p13 Porsi"
                        findViewById<View>(R.id.layoutOrderGiziB).visibility = View.GONE
                        findViewById<TextView>(R.id.tvOrderTotalPorsi).text = p13.toString()
                    } else if (categoryFilter == "CAT_15K") {
                        findViewById<View>(R.id.layoutOrderGiziA).visibility = View.GONE
                        findViewById<View>(R.id.layoutOrderGiziB).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvOrderPorsiB).text = "$pOthers Porsi"
                        findViewById<TextView>(R.id.tvOrderTotalPorsi).text = pOthers.toString()
                    } else {
                        // Default logic if no specific category filter
                        val targetPorsi = destDoc.getLong("targetPorsi") ?: 0L
                        findViewById<TextView>(R.id.tvOrderTotalPorsi).text = targetPorsi.toString()
                        
                        if (p13 > 0) {
                            findViewById<View>(R.id.layoutOrderGiziA).visibility = View.VISIBLE
                            findViewById<TextView>(R.id.tvOrderPorsiA).text = "$p13 Porsi"
                        }
                        if (pOthers > 0) {
                            findViewById<View>(R.id.layoutOrderGiziB).visibility = View.VISIBLE
                            findViewById<TextView>(R.id.tvOrderPorsiB).text = "$pOthers Porsi"
                        }
                    }
                }
            }

            // Load Photos based on filter
            photoList.clear()
            if (categoryFilter != "ALL") {
                scheduleRef.collection("media").document("photo_$categoryFilter").get().addOnSuccessListener { photoDoc ->
                    if (photoDoc.exists()) {
                        photoDoc.getString("data")?.let { photoList.add(it) }
                    }
                    setupPhotoCarousel()
                }
            } else {
                scheduleRef.collection("media").get().addOnSuccessListener { mediaDocs ->
                    for (doc in mediaDocs) {
                        doc.getString("data")?.let { photoList.add(it) }
                    }
                    setupPhotoCarousel()
                }
            }
        }
    }

    private fun setupPhotoCarousel() {
        if (photoList.isEmpty()) {
            findViewById<View>(R.id.cardOrderPhotos).visibility = View.GONE
            return
        } else {
            findViewById<View>(R.id.cardOrderPhotos).visibility = View.VISIBLE
        }

        viewPager.adapter = object : RecyclerView.Adapter<PhotoVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
                PhotoVH(LayoutInflater.from(parent.context).inflate(R.layout.item_menu_photo, parent, false))
            
            override fun onBindViewHolder(holder: PhotoVH, position: Int) {
                try {
                    val base64 = photoList[position]
                    val cleanBase64 = if (base64.contains(",")) base64.split(",")[1] else base64
                    val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    holder.iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                } catch (e: Exception) {
                    holder.iv.setImageResource(R.drawable.logo_mbg)
                }
            }
            override fun getItemCount() = photoList.size
        }

        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tlOrderPhotoIndicator)
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
    }

    class PhotoVH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivMenuCarousel)
    }
}

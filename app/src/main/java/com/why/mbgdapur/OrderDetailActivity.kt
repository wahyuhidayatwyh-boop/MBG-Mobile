package com.why.mbgdapur

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var viewPager: ViewPager2
    private val photoList = mutableListOf<PhotoItem>()
    private var scheduleListener: ListenerRegistration? = null
    private var destListener: ListenerRegistration? = null

    sealed class PhotoItem {
        data class Base64Item(val data: String) : PhotoItem()
        data class UrlItem(val url: String) : PhotoItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        viewPager = findViewById(R.id.vpMenuPhotos)
        findViewById<ImageView>(R.id.btnBackOrderDetail).setOnClickListener { finish() }

        loadOrderDetail()
    }

    private fun loadOrderDetail() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.tvDetailDate).text = "Produksi: $today"

        db.collection("vendors").whereEqualTo("email", userEmail).get().addOnSuccessListener { vendorDocs ->
            if (!vendorDocs.isEmpty) {
                val vendorRef = vendorDocs.documents[0].reference
                val scheduleRef = vendorRef.collection("daily_schedules").document(today)

                scheduleListener = scheduleRef.addSnapshotListener { scheduleDoc, error ->
                    if (error != null || scheduleDoc == null || !scheduleDoc.exists()) {
                        return@addSnapshotListener
                    }

                    // 1. Set Menu Utama dengan formatting
                    val menuRaw = scheduleDoc.getString("menu") ?: ""
                    findViewById<TextView>(R.id.tvMenuMainName).text = formatMenuDisplay(menuRaw)
                    findViewById<TextView>(R.id.tvDeadlineDetail).text = scheduleDoc.getString("deliveryDeadline") ?: "10:00 WIB"

                    // Ambil konfigurasi menu per kelompok gizi
                    val menuConfig = scheduleDoc.get("menuConfig") as? Map<*, *>
                    val menu13k = (menuConfig?.get("CAT_13K") as? Map<*, *>)?.get("menuName")?.toString() ?: menuRaw
                    val menu15k = (menuConfig?.get("CAT_15K") as? Map<*, *>)?.get("menuName")?.toString() ?: menuRaw

                    // 2. Load Photos for Carousel
                    photoList.clear()
                    scheduleRef.collection("media").get().addOnSuccessListener { mediaDocs ->
                        for (doc in mediaDocs) {
                            doc.getString("data")?.let { photoList.add(PhotoItem.Base64Item(it)) }
                        }
                        if (photoList.isEmpty()) {
                            scheduleDoc.getString("menuPhotoUrl")?.let { photoList.add(PhotoItem.UrlItem(it)) }
                            scheduleDoc.getString("menuPhotoBase64")?.let { photoList.add(PhotoItem.Base64Item(it)) }
                        }
                        setupPhotoCarousel()
                    }

                    // 3. Load Rincian Destinasi secara real-time
                    destListener?.remove()
                    destListener = scheduleRef.collection("destinations").addSnapshotListener { destinations, _ ->
                        val container = findViewById<LinearLayout>(R.id.containerDestinationDetails)
                        container.removeAllViews()
                        
                        var totalKeseluruhan = 0L
                        var totalGiziA = 0L
                        var totalGiziB = 0L

                        destinations?.forEach { dest ->
                            val schoolName = dest.getString("schoolName") ?: "Sekolah"
                            val porsiTotalSekolah = getLongFromDoc(dest, "targetPorsi")
                            totalKeseluruhan += porsiTotalSekolah

                            val itemView = LayoutInflater.from(this@OrderDetailActivity).inflate(R.layout.item_order_destination_card, container, false)
                            itemView.findViewById<TextView>(R.id.tvDestSchoolName).text = schoolName.uppercase()
                            itemView.findViewById<TextView>(R.id.tvDestTotalPorsi).text = "$porsiTotalSekolah Porsi"

                            val containerDetails = itemView.findViewById<LinearLayout>(R.id.containerPorsiDetails)
                            containerDetails.removeAllViews()

                            val p13 = getLongFromDoc(dest, "p13")
                            val p46 = getLongFromDoc(dest, "p46")
                            val pSMP = getLongFromDoc(dest, "pSMP")
                            val pSMA = getLongFromDoc(dest, "pSMA")
                            val p3B = getLongFromDoc(dest, "p3B")
                            val level = (dest.getString("level") ?: "SD").uppercase()

                            totalGiziA += p13
                            totalGiziB += (p46 + pSMP + pSMA + p3B)

                            val units = mutableListOf<Triple<String, Long, String>>()
                            if (p13 > 0) {
                                val label = when (level) {
                                    "PAUD", "TK", "RA" -> "Gizi A (PAUD/TK/RA)"
                                    else -> "Gizi A (SD Kelas 1-3)"
                                }
                                units.add(Triple(label, p13, menu13k))
                            }
                            if (p46 > 0) units.add(Triple("Gizi B (SD Kelas 4-6)", p46, menu15k))
                            if (pSMP > 0) units.add(Triple("Gizi B (SMP/MTs)", pSMP, menu15k))
                            if (pSMA + p3B > 0) units.add(Triple("Gizi B (SMA/SMK/MA)", pSMA + p3B, menu15k))

                            units.forEach { (label, count, menu) ->
                                val unitLayout = LinearLayout(this@OrderDetailActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(0, 16, 0, 16)
                                }
                                unitLayout.addView(TextView(this@OrderDetailActivity).apply {
                                    text = label
                                    textSize = 14f
                                    setTextColor(Color.parseColor("#1E293B"))
                                    typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                                })
                                unitLayout.addView(TextView(this@OrderDetailActivity).apply {
                                    text = "$count Porsi • $menu"
                                    textSize = 12f
                                    setTextColor(Color.parseColor("#64748B"))
                                    setPadding(0, 4, 0, 0)
                                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                })
                                containerDetails.addView(unitLayout)
                                
                                val div = View(this@OrderDetailActivity).apply {
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                                    setBackgroundColor(Color.parseColor("#F1F5F9"))
                                }
                                containerDetails.addView(div)
                            }
                            container.addView(itemView)
                        }
                        // Update Summary Totals
                        findViewById<TextView>(R.id.tvTotalPorsiDetail).text = totalKeseluruhan.toString()
                        findViewById<TextView>(R.id.tvTotalGiziA).text = totalGiziA.toString()
                        findViewById<TextView>(R.id.tvTotalGiziB).text = totalGiziB.toString()
                    }
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

    private fun getLongFromDoc(doc: DocumentSnapshot, key: String): Long {
        val v = doc.get(key)
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun setupPhotoCarousel() {
        if (photoList.isEmpty()) {
            findViewById<View>(R.id.vpMenuPhotos).visibility = View.GONE
            findViewById<View>(R.id.tlPhotoIndicator).visibility = View.GONE
            return
        }
        findViewById<View>(R.id.vpMenuPhotos).visibility = View.VISIBLE
        findViewById<View>(R.id.tlPhotoIndicator).visibility = View.VISIBLE
        
        viewPager.adapter = object : RecyclerView.Adapter<PhotoViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
                PhotoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_menu_photo, parent, false))
            
            override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
                when (val item = photoList[position]) {
                    is PhotoItem.Base64Item -> {
                        try {
                            val bytes = Base64.decode(item.data, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            holder.imageView.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            holder.imageView.setImageResource(R.drawable.logo_mbg)
                        }
                    }
                    is PhotoItem.UrlItem -> {
                        Glide.with(this@OrderDetailActivity)
                            .load(item.url)
                            .placeholder(R.drawable.logo_mbg)
                            .into(holder.imageView)
                    }
                }
            }
            override fun getItemCount() = photoList.size
        }

        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tlPhotoIndicator)
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
    }

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivMenuCarousel)
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleListener?.remove()
        destListener?.remove()
    }
}

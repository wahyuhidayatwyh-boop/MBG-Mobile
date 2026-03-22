package com.why.mbgdapur

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class SchoolCourierTrackingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var webView: WebView
    
    private var driverPhone: String? = null
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var isSchoolDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_courier_tracking)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        webView = findViewById(R.id.webViewTracking)
        setupWebView()

        findViewById<View>(R.id.btnBackTracking).setOnClickListener { finish() }
        
        findViewById<View>(R.id.btnCallDriver).setOnClickListener {
            driverPhone?.let { phone ->
                val formattedPhone = if (phone.startsWith("0")) {
                    "62" + phone.substring(1)
                } else if (phone.startsWith("+")) {
                    phone.substring(1)
                } else {
                    phone
                }
                
                try {
                    val url = "https://api.whatsapp.com/send?phone=$formattedPhone"
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(url)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp tidak terpasang", Toast.LENGTH_SHORT).show()
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    startActivity(dialIntent)
                }
            } ?: Toast.makeText(this, "Nomor kurir tidak tersedia", Toast.LENGTH_SHORT).show()
        }

        loadTrackingData()
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("intent://") || url.startsWith("google.navigation:")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (intent != null && intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            return true
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                    return true
                }
                return false
            }
        }
    }

    private fun loadTrackingData() {
        val userEmail = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("schools").document(userEmail).get().addOnSuccessListener { schoolDoc ->
            if (schoolDoc.exists()) {
                val vEmail = schoolDoc.getString("assignedVendorEmail")
                if (vEmail != null) {
                    db.collection("vendors").whereEqualTo("email", vEmail).get().addOnSuccessListener { vendorDocs ->
                        if (!vendorDocs.isEmpty) {
                            val vendorRef = vendorDocs.documents[0].reference
                            vendorRef.collection("daily_schedules").document(today).addSnapshotListener { schedule, _ ->
                                if (schedule != null && schedule.exists()) {
                                    val driverId = schedule.getString("driverEmail")
                                    if (driverId != null) {
                                        startLiveTracking(driverId, userEmail)
                                    } else {
                                        findViewById<TextView>(R.id.tvDeliveryStatus).text = "KURIR BELUM BERANGKAT"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startLiveTracking(driverId: String, schoolEmail: String) {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        // 1. Pantau Status Pengiriman Sekolah Ini
        db.collection("drivers").document(driverId)
            .collection("delivery_logs").document("${today}_$schoolEmail")
            .addSnapshotListener { logDoc, _ ->
                val specificStatus = logDoc?.getString("status") ?: "WAITING"
                isSchoolDone = specificStatus == "DONE"
                
                val tvStatus = findViewById<TextView>(R.id.tvDeliveryStatus)
                when (specificStatus) {
                    "DONE" -> {
                        tvStatus.text = "PENGIRIMAN SELESAI"
                        tvStatus.setTextColor(Color.parseColor("#10B981"))
                    }
                    "ARRIVED" -> {
                        tvStatus.text = "KURIR SUDAH SAMPAI"
                        tvStatus.setTextColor(Color.parseColor("#0284C7"))
                    }
                    "OTW" -> {
                        tvStatus.text = "KURIR SEDANG DI PERJALANAN"
                        tvStatus.setTextColor(Color.parseColor("#16A34A"))
                    }
                    "WAITING" -> {
                        tvStatus.text = "MENUNGGU ANTRIAN (STOP SEBELUMNYA)"
                        tvStatus.setTextColor(Color.parseColor("#64748B"))
                    }
                }
            }

        // 2. Pantau Pergerakan Kurir & Kendala
        db.collection("drivers").document(driverId).addSnapshotListener { driverDoc, error ->
            if (error != null) return@addSnapshotListener

            if (driverDoc != null && driverDoc.exists()) {
                val lat = driverDoc.getDouble("currentLat")
                val lng = driverDoc.getDouble("currentLng")
                val name = driverDoc.getString("name") ?: "Kurir MBG"
                val lastUpdate = driverDoc.getTimestamp("lastUpdate")
                driverPhone = driverDoc.getString("phone")

                // Handle Driver Issue (Hanya muncul jika belum DONE)
                val issueType = driverDoc.getString("currentIssueType") ?: "NONE"
                val issueMsg = driverDoc.getString("currentIssueMsg") ?: ""
                val issuePhoto = driverDoc.getString("currentIssuePhoto")
                val cardIssue = findViewById<View>(R.id.cardDriverIssue)

                if (issueType != "NONE" && !isSchoolDone) {
                    cardIssue.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tvIssueTitle).text = "KENDALA: $issueType"
                    findViewById<TextView>(R.id.tvIssueDescription).text = issueMsg
                    
                    val ivIssuePhoto = findViewById<ImageView>(R.id.ivIssuePhoto)
                    if (!issuePhoto.isNullOrEmpty()) {
                        ivIssuePhoto.visibility = View.VISIBLE
                        val bitmap = base64ToBitmap(issuePhoto)
                        ivIssuePhoto.setImageBitmap(bitmap)
                    } else {
                        ivIssuePhoto.visibility = View.GONE
                    }
                } else {
                    cardIssue.visibility = View.GONE
                }

                findViewById<TextView>(R.id.tvDriverName).text = name
                
                db.collection("drivers").document(driverId).collection("vehicle").document("info")
                    .get().addOnSuccessListener { vDoc ->
                        if (vDoc.exists()) {
                            val plate = vDoc.getString("plateNumber") ?: "---"
                            val type = vDoc.getString("vehicleType") ?: "Unit Logistik"
                            findViewById<TextView>(R.id.tvDriverVehicle).text = "$type ($plate)"
                        } else {
                            findViewById<TextView>(R.id.tvDriverVehicle).text = "Unit Logistik"
                        }
                    }
                
                val photoBase64 = driverDoc.getString("profilephoto")
                val ivDriverPhoto = findViewById<ImageView>(R.id.ivDriverPhoto)
                if (!photoBase64.isNullOrEmpty()) {
                    val bitmap = base64ToBitmap(photoBase64)
                    ivDriverPhoto.setImageBitmap(bitmap)
                    ivDriverPhoto.imageTintList = null
                }

                val tvTime = findViewById<TextView>(R.id.tvLastUpdate)
                lastUpdate?.let {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    tvTime.text = "Update terakhir: ${sdf.format(it.toDate())} WIB"
                }

                if (lat != null && lng != null) {
                    if (lat != lastLat || lng != lastLng) {
                        lastLat = lat
                        lastLng = lng
                        
                        val htmlData = """
                            <html>
                            <body style="margin:0;padding:0;">
                                <iframe 
                                    width="100%" 
                                    height="100%" 
                                    frameborder="0" 
                                    style="border:0; height:100vh; width:100vw" 
                                    src="https://maps.google.com/maps?q=$lat,$lng&hl=id&z=16&output=embed" 
                                    allowfullscreen>
                                </iframe>
                            </body>
                            </html>
                        """.trimIndent()
                        
                        webView.loadDataWithBaseURL("https://maps.google.com", htmlData, "text/html", "UTF-8", null)
                    }
                }
            }
        }
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

package com.why.mbgdapur

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.why.mbgdapur.databinding.DriverNavigationBinding
import java.text.SimpleDateFormat
import java.util.*

class DriverNavigationActivity : AppCompatActivity() {

    private lateinit var binding: DriverNavigationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var stopNumber = 1
    private var isReturMode = false
    private var driverEmail: String? = null
    private var currentLat: Double? = null
    private var currentLng: Double? = null
    
    private var isMapLoaded = false
    
    private var targetSchoolName: String = ""
    private var targetSchoolEmail: String = ""
    private var targetPorsi: Long = 0
    private var targetAddress: String = ""
    private var targetLat: Double? = null
    private var targetLng: Double? = null
    private var schoolPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        driverEmail = auth.currentUser?.email

        stopNumber = intent.getIntExtra("STOP_NUMBER", 1)
        isReturMode = intent.getBooleanExtra("IS_RETUR_MODE", false)
        targetSchoolEmail = intent.getStringExtra("TARGET_SCHOOL_EMAIL") ?: ""
        
        if (isReturMode) {
            binding.btnArrived.setBackgroundColor(android.graphics.Color.parseColor("#15803D"))
        }
        
        if (targetSchoolEmail.isNotEmpty()) {
            loadSchoolPhone("", targetSchoolEmail)
        }
        
        setupWebViewMap()
        setupActions()
        startLocationUpdates()
        loadDynamicData()
    }

    private fun loadDynamicData() {
        val email = driverEmail ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("drivers").document(email).get().addOnSuccessListener { driverDoc ->
            val vendorEmail = driverDoc.getString("assignedVendorEmail") ?: return@addOnSuccessListener
            
            db.collection("vendors").whereEqualTo("email", vendorEmail).get().addOnSuccessListener { vendorDocs ->
                if (!vendorDocs.isEmpty) {
                    val vendorRef = vendorDocs.documents[0].reference
                    val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                    
                    scheduleRef.collection("destinations").orderBy("schoolName").addSnapshotListener { destinations, _ ->
                        val docs = destinations?.documents ?: return@addSnapshotListener
                        if (stopNumber <= docs.size) {
                            val target = docs[stopNumber - 1]
                            
                            val emailField = target.getString("schoolEmail")
                            targetSchoolEmail = if (!emailField.isNullOrEmpty()) emailField else target.id
                            
                            targetSchoolName = target.getString("schoolName") ?: "Sekolah"
                            targetPorsi = target.getLong("targetPorsi") ?: 0
                            targetAddress = target.getString("address") ?: ""
                            targetLat = target.getDouble("latitude")
                            targetLng = target.getDouble("longitude")
                            
                            loadSchoolPhone(targetSchoolName, targetSchoolEmail)
                            
                            updateUI(docs.size)
                            calculateAndDisplayMetrics()
                        }
                    }
                }
            }
        }
    }

    private fun loadSchoolPhone(name: String, email: String) {
        if (email.isEmpty()) return
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim()

        db.collection("schools").document(cleanEmail).get().addOnSuccessListener { doc ->
            if (doc.exists() && !doc.getString("phone").isNullOrEmpty()) {
                schoolPhone = doc.getString("phone")
            } else {
                db.collection("schools").whereEqualTo("email", cleanEmail).get().addOnSuccessListener { queryEmail ->
                    if (!queryEmail.isEmpty) {
                        schoolPhone = queryEmail.documents[0].getString("phone")
                    } else if (cleanName.isNotEmpty()) {
                        db.collection("schools").whereEqualTo("name", cleanName).get().addOnSuccessListener { queryName ->
                            if (!queryName.isEmpty) {
                                schoolPhone = queryName.documents[0].getString("phone")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(totalStops: Int) {
        binding.tvStopIndicator.text = "Pemberhentian $stopNumber dari $totalStops"
        binding.tvTargetName.text = targetSchoolName
        if (isReturMode) {
            binding.tvTaskDetail.text = "Tugas: Jemput Kotak Kotor"
            binding.btnArrived.text = "SAYA SUDAH TIBA UNTUK JEMPUT"
        } else {
            binding.tvTaskDetail.text = "Tugas: Turunkan $targetPorsi Boks"
        }
    }

    private fun calculateAndDisplayMetrics() {
        val cLat = currentLat ?: return
        val cLng = currentLng ?: return
        val tLat = targetLat ?: return
        val tLng = targetLng ?: return
        val results = FloatArray(1)
        Location.distanceBetween(cLat, cLng, tLat, tLng, results)
        val distanceInKm = (results[0] * 1.2) / 1000 
        binding.tvDistance.text = String.format(Locale.getDefault(), "%.1f KM", distanceInKm)
        val estimatedMinutes = (distanceInKm / 20.0) * 60
        binding.tvETA.text = "${if (estimatedMinutes < 1) 1 else estimatedMinutes.toInt()} Menit"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewMap() {
        binding.webViewMap.settings.javaScriptEnabled = true
        binding.webViewMap.settings.domStorageEnabled = true
        binding.webViewMap.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return true 
            }
        }
    }

    private fun initMap(lat: Double, lng: Double) {
        if (isMapLoaded) return
        isMapLoaded = true
        val htmlData = """
            <html>
            <body style='margin:0;padding:0;'>
                <div id='map-container' style='width:100vw; height:100vh;'>
                    <iframe 
                        id='map-frame'
                        width='100%' 
                        height='100%' 
                        frameborder='0' 
                        style='border:0;' 
                        src='https://maps.google.com/maps?q=$lat,$lng&hl=id&z=17&output=embed' 
                        allowfullscreen>
                    </iframe>
                </div>
            </body>
            </html>
        """.trimIndent()
        binding.webViewMap.loadDataWithBaseURL("https://maps.google.com", htmlData, "text/html", "UTF-8", null)
    }

    private fun setupActions() {
        binding.btnCallSchool.setOnClickListener {
            if (!schoolPhone.isNullOrEmpty()) {
                var phone = schoolPhone!!
                phone = phone.replace(Regex("[^0-9]"), "")
                if (phone.startsWith("0")) phone = "62" + phone.substring(1)
                else if (!phone.startsWith("62")) phone = "62$phone"
                
                val url = "https://api.whatsapp.com/send?phone=$phone"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try { startActivity(intent) } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp tidak terinstal", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Nomor sekolah tidak ditemukan", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnArrived.setOnClickListener {
            stopLocationUpdates()
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            driverEmail?.let { email ->
                val batch = db.batch()
                val driverRef = db.collection("drivers").document(email)
                
                if (isReturMode) {
                    batch.update(driverRef, "statusDelivery", "RETUR_ARRIVED_STOP_$stopNumber")
                    if (targetSchoolEmail.isNotEmpty()) {
                        val logRef = driverRef.collection("delivery_logs").document("${today}_$targetSchoolEmail")
                        batch.update(logRef, "returStatus", "ARRIVED", "updatedAt", FieldValue.serverTimestamp())
                    }
                } else {
                    batch.update(driverRef, "statusDelivery", "ARRIVED_STOP_$stopNumber")
                    if (targetSchoolEmail.isNotEmpty()) {
                        val logRef = driverRef.collection("delivery_logs").document("${today}_$targetSchoolEmail")
                        batch.update(logRef, "status", "ARRIVED", "updatedAt", FieldValue.serverTimestamp())
                    }
                }

                batch.commit().addOnSuccessListener {
                    val nextIntent = if (isReturMode) Intent(this, DriverCollectionActivity::class.java)
                                     else Intent(this, DriverProofOfDeliveryActivity::class.java)
                    nextIntent.putExtra("STOP_NUMBER", stopNumber)
                    nextIntent.putExtra("TARGET_SCHOOL_EMAIL", targetSchoolEmail)
                    startActivity(nextIntent)
                    finish()
                }
            }
        }

        binding.btnOpenExternalMap.setOnClickListener {
            val query = if (targetAddress.isNotEmpty()) targetAddress else targetSchoolName
            val uri = Uri.parse("google.navigation:q=${Uri.encode(query)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
            try { startActivity(mapIntent) } catch (e: Exception) {}
        }

        binding.btnReportIssue.setOnClickListener {
            startActivity(Intent(this, DriverHelpActivity::class.java))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000).setMinUpdateIntervalMillis(2000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                currentLat = location.latitude
                currentLng = location.longitude
                
                // Hanya load map sekali saat aplikasi baru dibuka/mendapat lokasi pertama
                if (!isMapLoaded) {
                    initMap(location.latitude, location.longitude)
                }
                
                driverEmail?.let { email ->
                    db.collection("drivers").document(email).update("currentLat", currentLat, "currentLng", currentLng, "lastUpdate", FieldValue.serverTimestamp())
                }
                calculateAndDisplayMetrics()
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() { if (this::locationCallback.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback) }
    override fun onDestroy() { stopLocationUpdates(); super.onDestroy() }
}

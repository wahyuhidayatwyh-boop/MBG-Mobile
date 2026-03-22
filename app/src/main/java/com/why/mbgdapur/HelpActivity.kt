package com.why.mbgdapur

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class HelpActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverVehicle: TextView
    private lateinit var ivDriverPhoto: ImageView
    private lateinit var cardDriverIssue: MaterialCardView
    private lateinit var tvIssueMsg: TextView
    private lateinit var ivIssuePhoto: ImageView
    private lateinit var btnContactDriver: MaterialButton
    private lateinit var layoutDriverInfo: View
    private lateinit var layoutNoDriver: View

    private var driverListener: ListenerRegistration? = null
    private var logListener: ListenerRegistration? = null
    private var isSchoolDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupClickListeners()
        loadAssignedDriver()
    }

    private fun initViews() {
        tvDriverName = findViewById(R.id.tvDriverNameHelp)
        tvDriverVehicle = findViewById(R.id.tvDriverVehicleHelp)
        ivDriverPhoto = findViewById(R.id.ivDriverPhotoHelp)
        cardDriverIssue = findViewById(R.id.cardDriverIssueHelp)
        tvIssueMsg = findViewById(R.id.tvIssueMsgHelp)
        ivIssuePhoto = findViewById(R.id.ivIssuePhotoHelp)
        btnContactDriver = findViewById(R.id.btnContactDriverHelp)
        layoutDriverInfo = findViewById(R.id.layoutDriverInfoHelp)
        layoutNoDriver = findViewById(R.id.layoutNoDriverHelp)
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnBackHelp).setOnClickListener { finish() }
        
        findViewById<MaterialButton>(R.id.btnCallCenter).setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:08123456789")
            startActivity(intent)
        }
    }

    private fun loadAssignedDriver() {
        val userEmail = auth.currentUser?.email?.lowercase() ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("schools").whereEqualTo("email", userEmail).limit(1).get().addOnSuccessListener { schools ->
            if (!schools.isEmpty) {
                val schoolDoc = schools.documents[0]
                val vendorEmail = schoolDoc.getString("assignedVendorEmail")
                
                if (vendorEmail != null) {
                    listenToDriver(vendorEmail, userEmail, today)
                } else {
                    layoutNoDriver.visibility = View.VISIBLE
                    layoutDriverInfo.visibility = View.GONE
                }
            } else {
                layoutNoDriver.visibility = View.VISIBLE
                layoutDriverInfo.visibility = View.GONE
            }
        }
    }

    private fun listenToDriver(vendorEmail: String, schoolEmail: String, dateId: String) {
        driverListener = db.collection("drivers")
            .whereEqualTo("assignedVendorEmail", vendorEmail)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) {
                    layoutNoDriver.visibility = View.VISIBLE
                    layoutDriverInfo.visibility = View.GONE
                    return@addSnapshotListener
                }

                val driverDoc = snapshots.documents[0]
                val driverEmail = driverDoc.id
                
                // Pantau log pengiriman sekolah ini untuk menyembunyikan kendala jika sudah DONE
                logListener?.remove()
                logListener = db.collection("drivers").document(driverEmail)
                    .collection("delivery_logs").document("${dateId}_$schoolEmail")
                    .addSnapshotListener { logSnap, _ ->
                        isSchoolDone = logSnap?.getString("status") == "DONE"
                        updateIssueVisibility(driverDoc)
                    }

                updateUIWithDriverData(driverDoc)
            }
    }

    private fun updateUIWithDriverData(doc: com.google.firebase.firestore.DocumentSnapshot) {
        layoutNoDriver.visibility = View.GONE
        layoutDriverInfo.visibility = View.VISIBLE

        val name = doc.getString("name") ?: "Kurir MBG"
        val vehicle = doc.getString("vehicle") ?: "Unit Logistik MBG"
        val phone = doc.getString("phone") ?: ""
        val photoBase64 = doc.getString("profilephoto")

        tvDriverName.text = name
        tvDriverVehicle.text = vehicle

        if (!photoBase64.isNullOrEmpty()) {
            try {
                val cleanBase64 = if (photoBase64.contains(",")) photoBase64.split(",")[1] else photoBase64
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                ivDriverPhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            } catch (ex: Exception) {}
        }

        btnContactDriver.setOnClickListener {
            if (phone.isNotEmpty()) {
                val url = "https://wa.me/$phone"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } else {
                Toast.makeText(this, "Nomor telepon kurir tidak tersedia", Toast.LENGTH_SHORT).show()
            }
        }
        
        updateIssueVisibility(doc)
    }

    private fun updateIssueVisibility(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val issueType = doc.getString("currentIssueType") ?: "NONE"
        val issueMsg = doc.getString("currentIssueMsg") ?: ""
        val issuePhoto = doc.getString("currentIssuePhoto")

        // Jika sekolah sudah DONE, jangan tampilkan kendala apa pun lagi
        if (issueType != "NONE" && !isSchoolDone) {
            cardDriverIssue.visibility = View.VISIBLE
            tvIssueMsg.text = "KENDALA: $issueType\n$issueMsg"
            
            if (!issuePhoto.isNullOrEmpty()) {
                ivIssuePhoto.visibility = View.VISIBLE
                try {
                    val cleanBase64 = if (issuePhoto.contains(",")) issuePhoto.split(",")[1] else issuePhoto
                    val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    ivIssuePhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                } catch (ex: Exception) {}
            } else {
                ivIssuePhoto.visibility = View.GONE
            }
        } else {
            cardDriverIssue.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        driverListener?.remove()
        logListener?.remove()
    }
}

package com.why.mbgdapur

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.why.mbgdapur.databinding.DriverEndShiftBinding

class DriverEndShiftActivity : AppCompatActivity() {

    private lateinit var binding: DriverEndShiftBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverEndShiftBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        generateDriverQRCode()
        startScannerAnimation()
        setupRealtimeStatusListener()
    }

    private fun generateDriverQRCode() {
        val email = auth.currentUser?.email ?: "no-email"
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(email, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivEndShiftQRCode.setImageBitmap(bmp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startScannerAnimation() {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0.9f
        )
        animation.duration = 2000
        animation.repeatCount = Animation.INFINITE
        animation.repeatMode = Animation.REVERSE
        binding.scannerLine.startAnimation(animation)
    }

    private fun setupRealtimeStatusListener() {
        val driverEmail = auth.currentUser?.email ?: return

        // 1. Set status Driver menjadi Menunggu Scan
        db.collection("drivers").document(driverEmail)
            .update("statusShift", "WAITING_SCAN")

        // 2. Listen perubahan status secara Real-time
        db.collection("drivers").document(driverEmail)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("statusShift")
                    if (status == "COMPLETED") {
                        handleShiftCompleted()
                    }
                }
            }
    }

    private fun handleShiftCompleted() {
        binding.scannerLine.clearAnimation()
        binding.scannerLine.visibility = View.GONE
        
        binding.bottomStatusBar.setCardBackgroundColor(resources.getColor(android.R.color.holo_green_dark, null))
        binding.tvStatusBottom.text = "SHIFT SELESAI • TERVERIFIKASI"
        binding.pbWaitingKitchen.visibility = View.GONE
        
        Toast.makeText(this, "Wadah Kotor Diterima Dapur!", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, DriverDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 3000)
    }
}

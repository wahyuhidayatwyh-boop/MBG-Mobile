package com.why.mbgdapur

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.why.mbgdapur.databinding.DriverHandoverStandbyBinding

class DriverStandbyActivity : AppCompatActivity() {

    private lateinit var binding: DriverHandoverStandbyBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var handoverListener: ListenerRegistration? = null
    private var isUnlocked = false
    private var isClosingShift = false
    private var isTransitioning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DriverHandoverStandbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        isClosingShift = intent.getBooleanExtra("IS_CLOSING_SHIFT", false)

        setupUIForMode()
        resetHandoverStatus()
        verifyAndLoadDriver()
        setupChecklistLogic()
        startPulseAnimation()
        setupRealtimeHandoverListener()
    }

    private fun setupUIForMode() {
        if (isClosingShift) {
            binding.tvSopTitle.text = "KONFIRMASI PENGEMBALIAN"
            binding.tvSopTitle1.text = "Wadah & Boks Lengkap"
            binding.tvSopDesc1.text = "Pastikan semua boks kotor sudah di mobil"
            binding.tvSopTitle2.text = "Area Kargo Sudah Bersih"
            binding.tvSopDesc2.text = "Siap untuk proses sanitasi di dapur"
            binding.tvStatusBottom.text = "MENUNGGU SCAN PENERIMAAN"
            binding.tvLockMessage.text = "Lengkapi data\nretur"
        }
    }

    private fun resetHandoverStatus() {
        val email = auth.currentUser?.email ?: return
        db.collection("drivers").document(email).update("statusHandover", "WAITING")
    }

    private fun setupRealtimeHandoverListener() {
        val email = auth.currentUser?.email ?: return
        
        handoverListener = db.collection("drivers").document(email)
            .addSnapshotListener { snapshot, e ->
                if (e != null || isTransitioning) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("statusHandover")
                    if (status == "VERIFIED") {
                        isTransitioning = true
                        handoverListener?.remove()
                        handoverListener = null
                        
                        handleVerificationSuccess(email)
                    }
                }
            }
    }

    private fun handleVerificationSuccess(email: String) {
        if (isClosingShift) {
            Toast.makeText(this, "Pengembalian Berhasil Terverifikasi!", Toast.LENGTH_SHORT).show()
            db.collection("drivers").document(email).update("statusDelivery", "FINISHED_ALL")
            
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing) {
                    val intent = Intent(this, DriverShiftCompletedActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }, 500)
        } else {
            Toast.makeText(this, "Verifikasi Berhasil!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DriverManifestActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun verifyAndLoadDriver() {
        val email = auth.currentUser?.email ?: return
        db.collection("drivers").document(email).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    binding.tvDriverGreeting.text = "Halo, ${doc.getString("name") ?: "Driver"}"
                    binding.tvVehicleInfo.text = doc.getString("vehicle") ?: "Mobil Box"
                }
            }
    }

    private fun setupChecklistLogic() {
        binding.cbHealthy.setOnCheckedChangeListener { _, _ -> checkKesiapan() }
        binding.cbBoxClean.setOnCheckedChangeListener { _, _ -> checkKesiapan() }
        binding.cardChecklistHealth.setOnClickListener { binding.cbHealthy.isChecked = !binding.cbHealthy.isChecked }
        binding.cardChecklistBox.setOnClickListener { binding.cbBoxClean.isChecked = !binding.cbBoxClean.isChecked }
    }

    private fun checkKesiapan() {
        if (binding.cbHealthy.isChecked && binding.cbBoxClean.isChecked) {
            unlockQRCode()
        } else {
            lockQRCode()
        }
    }

    private fun unlockQRCode() {
        if (!isUnlocked) {
            isUnlocked = true
            binding.layoutQRLock.visibility = View.GONE
            binding.ivQRCode.alpha = 1.0f
            generateQRCode()
            binding.tvPulseInstruction.text = "SIAP SCAN"
        }
    }

    private fun lockQRCode() {
        isUnlocked = false
        binding.layoutQRLock.visibility = View.VISIBLE
        binding.ivQRCode.alpha = 0.1f
        binding.tvPulseInstruction.text = "STANDBY"
    }

    private fun generateQRCode() {
        val driverEmail = auth.currentUser?.email ?: ""
        if (driverEmail.isEmpty()) return
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(driverEmail, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQRCode.setImageBitmap(bmp)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startPulseAnimation() {
        val pulse = AlphaAnimation(0.4f, 1f).apply {
            duration = 1000
            repeatMode = AlphaAnimation.REVERSE
            repeatCount = AlphaAnimation.INFINITE
        }
        binding.tvPulseInstruction.startAnimation(pulse)
    }

    override fun onDestroy() {
        handoverListener?.remove()
        super.onDestroy()
    }
}

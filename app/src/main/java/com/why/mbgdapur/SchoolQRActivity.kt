package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class SchoolQRActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_qr)

        val ivQR = findViewById<ImageView>(R.id.ivSchoolQR)
        val tvLabel = findViewById<TextView>(R.id.tvLabelQR)
        val tvTitle = findViewById<TextView>(R.id.tvSchoolNameQR)
        val tvStatus = findViewById<TextView>(R.id.tvStatusLabelQR)
        val tvInstruction = findViewById<TextView>(R.id.tvInstructionQR)
        
        val auth = FirebaseAuth.getInstance()
        val email = auth.currentUser?.email ?: ""
        
        // Ambil Mode dari Intent
        val mode = intent.getStringExtra("QR_MODE") ?: "TERIMA"
        
        if (mode == "RETUR") {
            tvLabel.text = "VERIFIKASI RETUR"
            tvTitle.text = "QR Penjemputan Boks"
            tvStatus.text = "TOKEN RETUR AKTIF"
            tvStatus.setTextColor(Color.parseColor("#D97706")) // Warna oranye untuk retur
            tvInstruction.text = "Tunjukkan kode ini kepada kurir\nuntuk verifikasi penjemputan boks kotor"
        } else {
            tvLabel.text = "VERIFIKASI TERIMA"
            tvTitle.text = "QR Penerimaan Makan"
            tvStatus.text = "TOKEN KEAMANAN AKTIF"
            tvStatus.setTextColor(Color.parseColor("#10B981")) // Warna hijau untuk terima
            tvInstruction.text = "Tunjukkan kode ini kepada kurir\nuntuk proses verifikasi serah terima makanan"
        }
        
        if (email.isNotEmpty()) {
            // Sertakan MODE di dalam isi QR Code agar driver tidak salah scan
            val qrContent = "$email|$mode"
            generateQRCode(qrContent, ivQR)
        }

        findViewById<View>(R.id.btnBackQR).setOnClickListener { finish() }
    }

    private fun generateQRCode(text: String, imageView: ImageView) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            imageView.setImageBitmap(bmp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

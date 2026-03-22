package com.why.mbgdapur

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.why.mbgdapur.databinding.ActivityCookingProgressBinding
import com.why.mbgdapur.databinding.DialogManualInputBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CookingProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookingProgressBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var currentProgress = 0
    private var targetProgress = 0
    private var countDownTimer: CountDownTimer? = null
    private var scheduledDuration: Long = 0
    private var currentStep = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookingProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.btnBackCooking.setOnClickListener { finish() }

        loadSavedDataAndScheduledDuration()
        setupInputListeners()

        binding.btnCompleteCooking.setOnClickListener {
            if (currentStep == 3) {
                completePacking()
            }
        }
    }

    private fun setupInputListeners() {
        binding.btnAdd10.setOnClickListener { updateProgress(10) }
        binding.btnAdd50.setOnClickListener { updateProgress(50) }
        binding.btnAdd100.setOnClickListener { updateProgress(100) }
        binding.btnEditManual.setOnClickListener { 
            showMinimalistManualInputDialog()
        }
    }

    private fun showMinimalistManualInputDialog() {
        if (currentStep >= 4) {
            Toast.makeText(this, "Proses sudah selesai dan terkunci.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogManualInputBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.etManualCount.setText(if (currentProgress > 0) currentProgress.toString() else "")
        dialogBinding.etManualCount.requestFocus()

        dialogBinding.btnCancelManual.setOnClickListener { dialog.dismiss() }
        
        dialogBinding.btnSaveManual.setOnClickListener {
            val newValueStr = dialogBinding.etManualCount.text.toString()
            if (newValueStr.isNotEmpty()) {
                val newValue = newValueStr.toInt()
                if (newValue > targetProgress) {
                    Toast.makeText(this, "Jumlah melebihi target ($targetProgress)", Toast.LENGTH_SHORT).show()
                    currentProgress = targetProgress
                } else {
                    currentProgress = newValue
                }
                syncProgressToDatabase()
                refreshUI()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Masukkan jumlah!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun updateProgress(amount: Int) {
        if (currentStep >= 4) {
            Toast.makeText(this, "Proses sudah selesai dan terkunci.", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentProgress += amount
        if (targetProgress > 0 && currentProgress > targetProgress) currentProgress = targetProgress
        
        syncProgressToDatabase()
        refreshUI()
    }

    private fun refreshUI() {
        binding.tvProgressCount.text = "$currentProgress / $targetProgress (${if(targetProgress > 0) (currentProgress * 100 / targetProgress) else 0}%)"
        binding.progressCooking.progress = if(targetProgress > 0) (currentProgress * 100 / targetProgress) else 0
        
        if (currentStep >= 4) {
            lockAllOriginalUI()
        } else {
            binding.btnCompleteCooking.text = "SELESAI PENGEMASAN"
        }
    }

    private fun runTimer(duration: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hms = String.format(Locale.getDefault(), "%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(millisUntilFinished),
                    TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60,
                    TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60)
                binding.tvTimer.text = hms
                
                if (millisUntilFinished < 600000) {
                    binding.tvTimer.setTextColor(Color.RED)
                    binding.tvTimerStatus.text = "RITME KERJA: CEPATKAN!"
                }
            }

            override fun onFinish() {
                binding.tvTimer.text = "00:00:00"
                binding.tvTimerStatus.text = "WAKTU HABIS!"
            }
        }.start()
    }

    private fun syncProgressToDatabase() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        
        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val vendorRef = docs.documents[0].reference
                val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                
                val updates = mapOf(
                    "cookedCount" to currentProgress,
                    "statusProduksi" to "SEDANG DIKEMAS"
                )
                
                vendorRef.update(updates)
                scheduleRef.update(updates)
            }
        }
    }

    private fun loadSavedDataAndScheduledDuration() {
        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val vendorDoc = documents.documents[0]
                    val vendorRef = vendorDoc.reference

                    vendorRef.collection("daily_schedules").document(today).get()
                        .addOnSuccessListener { scheduleDoc ->
                            if (scheduleDoc.exists()) {
                                targetProgress = scheduleDoc.getLong("targetPorsi")?.toInt() ?: 0
                                scheduledDuration = scheduleDoc.getLong("durationMillis") ?: 0
                                currentProgress = scheduleDoc.getLong("cookedCount")?.toInt() ?: 0
                                currentStep = scheduleDoc.getLong("currentStep")?.toInt() ?: 3
                                
                                if (currentStep < 4) {
                                    var startTime = scheduleDoc.getTimestamp("cookingStartTime")
                                    
                                    if (startTime == null) {
                                        val now = com.google.firebase.Timestamp.now()
                                        startTime = now
                                        
                                        val startUpdates = mapOf(
                                            "cookingStartTime" to now,
                                            "statusProduksi" to "SEDANG DIKEMAS",
                                            "currentStep" to 3
                                        )
                                        
                                        vendorRef.update(startUpdates)
                                        scheduleDoc.reference.update(startUpdates)
                                        runTimer(scheduledDuration)
                                    } else {
                                        val startMillis = startTime.toDate().time
                                        val elapsed = System.currentTimeMillis() - startMillis
                                        val remaining = scheduledDuration - elapsed
                                        
                                        if (remaining > 0) {
                                            runTimer(remaining)
                                        } else {
                                            binding.tvTimer.text = "00:00:00"
                                            binding.tvTimerStatus.text = "WAKTU HABIS!"
                                        }
                                    }
                                }
                                refreshUI()
                            } else {
                                Toast.makeText(this, "Jadwal hari ini belum dibuat!", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                }
            }
    }

    private fun completePacking() {
        if (currentProgress == 0) {
            Toast.makeText(this, "Input progres masak Anda!", Toast.LENGTH_SHORT).show()
            return
        }

        val email = auth.currentUser?.email ?: return
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val vendorRef = documents.documents[0].reference
                val scheduleRef = vendorRef.collection("daily_schedules").document(today)
                
                val updates = mapOf(
                    "currentStep" to 4,
                    "statusProduksi" to "VERIFIKASI GIZI",
                    "cookingEndTime" to FieldValue.serverTimestamp(),
                    "cookedCount" to currentProgress
                )
                
                vendorRef.update(updates)
                scheduleRef.update(updates).addOnSuccessListener {
                    countDownTimer?.cancel()
                    currentStep = 4
                    refreshUI()
                    Toast.makeText(this, "Proses Pengemasan Selesai!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun lockAllOriginalUI() {
        binding.btnAdd10.isEnabled = false
        binding.btnAdd50.isEnabled = false
        binding.btnAdd100.isEnabled = false
        binding.btnEditManual.isEnabled = false
        binding.btnCompleteCooking.isEnabled = false
        binding.btnCompleteCooking.alpha = 0.5f
        binding.btnCompleteCooking.text = "PROSES SELESAI"
        binding.tvTimerStatus.text = "VERIFIKASI GIZI SIAP ✓"
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

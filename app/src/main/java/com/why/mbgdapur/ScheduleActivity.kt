package com.why.mbgdapur

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ScheduleActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private lateinit var btnSelectDate: MaterialButton
    private lateinit var btnSelectDuration: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var containerSchoolInputs: LinearLayout
    private lateinit var containerCategoryMenus: LinearLayout
    
    private var selectedDateStr: String = ""
    private var selectedDurationMillis: Long = 0
    private var editDateId: String? = null
    
    private val schoolList = mutableListOf<SchoolData>()
    private val categoryViews = mutableMapOf<String, CategoryViewHolder>()
    private var loadingDialog: AlertDialog? = null

    data class SchoolData(
        val email: String, val name: String, val address: String,
        val p13: Long, val p46: Long, val pSMP: Long, val pSMA: Long, val p3B: Long,
        val lat: Double, val lon: Double, val level: String, var isSelected: Boolean = false,
        var view: View? = null
    )

    class CategoryViewHolder(
        val categoryId: String,
        val root: View,
        val tvTotal: TextView,
        val etMenu: TextInputEditText,
        val ivPhoto: ImageView,
        val price: Int,
        var bitmap: Bitmap? = null
    )

    private var activeCategoryForPhoto: String? = null
    private val photoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { bmp ->
            activeCategoryForPhoto?.let { catId ->
                categoryViews[catId]?.let { holder ->
                    holder.bitmap = bmp
                    holder.ivPhoto.setImageBitmap(bmp)
                    holder.ivPhoto.imageTintList = null 
                    holder.ivPhoto.setPadding(0, 0, 0, 0)
                }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, it)
                }

                val scaledBitmap = scaleBitmap(bitmap)

                activeCategoryForPhoto?.let { catId ->
                    categoryViews[catId]?.let { holder ->
                        holder.bitmap = scaledBitmap
                        holder.ivPhoto.setImageBitmap(scaledBitmap)
                        holder.ivPhoto.imageTintList = null 
                        holder.ivPhoto.setPadding(0, 0, 0, 0)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxSize = 400 
        var width = bitmap.width
        var height = bitmap.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            finish()
            return
        }

        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectDuration = findViewById(R.id.btnSelectDuration)
        btnSave = findViewById(R.id.btnSaveSchedule)
        containerSchoolInputs = findViewById(R.id.containerSchoolInputs)
        containerCategoryMenus = findViewById(R.id.containerCategoryMenus)

        findViewById<View>(R.id.btnBackSchedule).setOnClickListener { finish() }
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectDuration.setOnClickListener { showTimePicker() }
        
        btnSave.setOnClickListener {
            showCustomConfirmationDialog(
                title = "SIMPAN JADWAL",
                message = "Apakah Anda yakin ingin menyimpan jadwal produksi ini?",
                confirmText = "YA, SIMPAN",
                iconRes = android.R.drawable.ic_menu_save
            ) {
                saveSchedule()
            }
        }

        editDateId = intent.getStringExtra("EDIT_DATE")
        if (editDateId != null) {
            selectedDateStr = editDateId!!
            btnSelectDate.text = selectedDateStr
            btnSelectDate.isEnabled = false 
            findViewById<TextView>(R.id.tvTitleSchedule).text = "EDIT JADWAL"
        }

        loadAssignedSchools()
    }

    private fun showCustomConfirmationDialog(
        title: String,
        message: String,
        confirmText: String,
        iconRes: Int,
        onConfirm: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        dialogView.findViewById<TextView>(R.id.tvExitTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvExitMessage).text = message
        dialogView.findViewById<ImageView>(R.id.ivExitIcon)?.setImageResource(iconRes)
        
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirmExit)
        btnConfirm.text = confirmText
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btnCancelExit).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showImageSourceDialog(id: String) {
        activeCategoryForPhoto = id
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_source, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<View>(R.id.btnSourceCamera).setOnClickListener {
            dialog.dismiss()
            photoLauncher.launch(null)
        }

        dialogView.findViewById<View>(R.id.btnSourceGallery).setOnClickListener {
            dialog.dismiss()
            galleryLauncher.launch("image/*")
        }

        dialogView.findViewById<View>(R.id.btnCancelSource).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showLoading(message: String) {
        runOnUiThread {
            if (loadingDialog == null) {
                val view = layoutInflater.inflate(R.layout.dialog_loading, null)
                loadingDialog = AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .create()
                loadingDialog?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            loadingDialog?.findViewById<TextView>(R.id.tvLoadingMessage)?.text = message
            if (!isFinishing) loadingDialog?.show()
        }
    }

    private fun hideLoading() {
        runOnUiThread {
            loadingDialog?.dismiss()
        }
    }

    private fun loadAssignedSchools() {
        val myEmail = auth.currentUser?.email ?: return
        showLoading("Memuat data...")
        db.collection("schools").whereEqualTo("assignedVendorEmail", myEmail).get()
            .addOnSuccessListener { schools ->
                schoolList.clear()
                containerSchoolInputs.removeAllViews()
                
                for (doc in schools) {
                    val sEmail = doc.id
                    val sName = doc.getString("name") ?: ""
                    val level = (doc.getString("level") ?: "SD").uppercase()
                    val totalSiswa = doc.getLong("lastStudentCount") ?: 0L

                    var p13 = 0L; var p46 = 0L; var pSMP = 0L; var pSMA = 0L; var p3B = 0L
                    when (level) {
                        "SD", "MI" -> { 
                            p13 = doc.getLong("count_grade_1_3") ?: 0L
                            p46 = doc.getLong("count_grade_4_6") ?: 0L 
                        }
                        "PAUD", "TK", "RA" -> p13 = totalSiswa
                        "SMP", "MTS" -> pSMP = totalSiswa
                        "SMA", "SMK", "MA" -> pSMA = totalSiswa
                        "3B" -> p3B = totalSiswa
                    }

                    val school = SchoolData(
                        sEmail, sName, doc.getString("address") ?: "",
                        p13, p46, pSMP, pSMA, p3B,
                        doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0, level
                    )
                    
                    val itemView = layoutInflater.inflate(R.layout.item_school_input, containerSchoolInputs, false)
                    itemView.findViewById<TextView>(R.id.tvSchoolNameInput).text = sName.uppercase()
                    itemView.findViewById<TextView>(R.id.tvTotalPorsiSekolah).text = "${p13+p46+pSMP+pSMA+p3B}"
                    
                    val tv13 = itemView.findViewById<TextView>(R.id.tvPorsi13)
                    val tv46 = itemView.findViewById<TextView>(R.id.tvPorsi46)
                    val tvSMP = itemView.findViewById<TextView>(R.id.tvPorsiSMP)
                    val tvSMA = itemView.findViewById<TextView>(R.id.tvPorsiSMA)

                    val label13 = when (level) {
                        "PAUD", "TK", "RA" -> "Gizi A (PAUD/TK/RA): $p13"
                        else -> "Gizi A (SD Kelas 1-3): $p13"
                    }
                    val label46 = "Gizi B (SD Kelas 4-6): $p46"
                    val labelSMP = "Gizi B (SMP/MTs): $pSMP"
                    val labelSMA = "Gizi B (SMA/SMK/MA): ${pSMA+p3B}"

                    if (p13 > 0) { tv13.visibility = View.VISIBLE; tv13.text = label13 }
                    if (p46 > 0) { tv46.visibility = View.VISIBLE; tv46.text = label46 }
                    if (pSMP > 0) { tvSMP.visibility = View.VISIBLE; tvSMP.text = labelSMP }
                    if ((pSMA + p3B) > 0) { tvSMA.visibility = View.VISIBLE; tvSMA.text = labelSMA }

                    val cb = itemView.findViewById<CheckBox>(R.id.cbSelectSchool)
                    cb.setOnCheckedChangeListener { _, isChecked ->
                        school.isSelected = isChecked
                        updateCategoryInputs()
                    }
                    school.view = itemView
                    schoolList.add(school)
                    containerSchoolInputs.addView(itemView)
                }

                if (editDateId != null) {
                    loadEditData()
                } else {
                    hideLoading()
                }
            }.addOnFailureListener {
                hideLoading()
            }
    }

    private fun loadEditData() {
        val email = auth.currentUser?.email ?: return
        db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { vendorDocs ->
            if (!vendorDocs.isEmpty) {
                val vendorRef = vendorDocs.documents[0].reference
                val scheduleRef = vendorRef.collection("daily_schedules").document(editDateId!!)
                
                scheduleRef.get().addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        selectedDurationMillis = doc.getLong("durationMillis") ?: 0L
                        if (selectedDurationMillis > 0) {
                            val hours = TimeUnit.MILLISECONDS.toHours(selectedDurationMillis)
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(selectedDurationMillis) % 60
                            btnSelectDuration.text = "$hours Jam $minutes Menit"
                        }

                        scheduleRef.collection("destinations").get().addOnSuccessListener { dests ->
                            val selectedEmails = dests.map { it.id }
                            schoolList.forEach { school ->
                                if (selectedEmails.contains(school.email)) {
                                    school.isSelected = true
                                    school.view?.findViewById<CheckBox>(R.id.cbSelectSchool)?.isChecked = true
                                }
                            }
                            updateCategoryInputs()

                            val menuConfig = doc.get("menuConfig") as? Map<String, Any>
                            menuConfig?.forEach { (catId, config) ->
                                val data = config as? Map<String, Any>
                                val menuName = data?.get("menuName") as? String
                                categoryViews[catId]?.etMenu?.setText(menuName)

                                scheduleRef.collection("media").document("photo_$catId").get().addOnSuccessListener { photoDoc ->
                                    val base64 = photoDoc.getString("data")
                                    if (base64 != null) {
                                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        categoryViews[catId]?.let { holder ->
                                            holder.bitmap = bmp
                                            holder.ivPhoto.setImageBitmap(bmp)
                                            holder.ivPhoto.imageTintList = null
                                            holder.ivPhoto.setPadding(0, 0, 0, 0)
                                        }
                                    }
                                }
                            }
                            hideLoading()
                        }
                    } else {
                        hideLoading()
                    }
                }.addOnFailureListener { hideLoading() }
            } else {
                hideLoading()
            }
        }.addOnFailureListener { hideLoading() }
    }

    private fun updateCategoryInputs() {
        val selectedSchools = schoolList.filter { it.isSelected }
        val totals = mapOf(
            "CAT_13K" to selectedSchools.sumOf { it.p13 },
            "CAT_15K" to selectedSchools.sumOf { it.p46 + it.pSMP + it.pSMA + it.p3B }
        )

        totals.forEach { (id, total) ->
            if (total > 0) {
                if (!categoryViews.containsKey(id)) {
                    addCategoryView(id)
                }
                categoryViews[id]?.tvTotal?.text = "$total Porsi"
                categoryViews[id]?.root?.visibility = View.VISIBLE
                
                val labelView = categoryViews[id]?.root?.findViewById<TextView>(R.id.tvCategoryName)
                val relevantLevels = selectedSchools.filter { 
                    if (id == "CAT_13K") it.p13 > 0 else (it.p46 + it.pSMP + it.pSMA + it.p3B) > 0
                }.map { it.level }.toSet()
                
                labelView?.text = "MENU " + generateGiziLabel(id, relevantLevels)
            } else {
                categoryViews[id]?.root?.visibility = View.GONE
            }
        }
    }

    private fun addCategoryView(id: String) {
        val view = layoutInflater.inflate(R.layout.item_category_menu_input, containerCategoryMenus, false)
        
        val holder = CategoryViewHolder(
            id, view,
            view.findViewById(R.id.tvCategoryTotalPorsi),
            view.findViewById(R.id.etCategoryMenu),
            view.findViewById(R.id.ivCategoryPhoto),
            if (id == "CAT_13K") 13000 else 15000
        )
        
        view.findViewById<View>(R.id.cardCategoryPhoto).setOnClickListener { showImageSourceDialog(id) }
        categoryViews[id] = holder
        containerCategoryMenus.addView(view)
    }

    private fun saveSchedule() {
        if (selectedDateStr.isEmpty() || selectedDurationMillis <= 0) {
            Toast.makeText(this, "Lengkapi Tanggal \u0026 Durasi!", Toast.LENGTH_SHORT).show()
            return
        }

        val activeCats = categoryViews.values.filter { it.root.visibility == View.VISIBLE }
        if (activeCats.isEmpty()) {
            Toast.makeText(this, "Pilih sekolah!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (activeCats.any { it.etMenu.text.isNullOrEmpty() || it.bitmap == null }) {
            Toast.makeText(this, "Lengkapi Menu \u0026 Foto!", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading("Sedang menyimpan...")
        btnSave.isEnabled = false

        thread {
            try {
                val email = auth.currentUser?.email ?: return@thread
                
                db.collection("vendors").whereEqualTo("email", email).get().addOnSuccessListener { docs ->
                    if (!docs.isEmpty) {
                        val batch = db.batch()
                        val vendorRef = docs.documents[0].reference
                        val scheduleRef = vendorRef.collection("daily_schedules").document(selectedDateStr)

                        val menuConfig = mutableMapOf<String, Any>()
                        var totalPGlobal = 0L
                        var estBudget = 0L
                        val combinedMenuNames = mutableListOf<String>()

                        activeCats.forEach { cat ->
                            val porsi = cat.tvTotal.text.toString().replace(" Porsi", "").toLongOrNull() ?: 0L
                            val menuName = cat.etMenu.text.toString()
                            val photoBase64 = bitmapToBase64(cat.bitmap!!)
                            
                            totalPGlobal += porsi
                            estBudget += (porsi * cat.price)
                            combinedMenuNames.add(menuName)
                            
                            val photoData = hashMapOf("data" to photoBase64)
                            batch.set(scheduleRef.collection("media").document("photo_${cat.categoryId}"), photoData)

                            menuConfig[cat.categoryId] = mapOf(
                                "menuName" to menuName,
                                "totalPorsi" to porsi,
                                "unitPrice" to cat.price,
                                "totalBudget" to (porsi * cat.price)
                            )
                        }

                        val mainData = hashMapOf(
                            "date" to selectedDateStr,
                            "menu" to combinedMenuNames.distinct().joinToString(", "),
                            "targetPorsi" to totalPGlobal,
                            "estimatedBudget" to estBudget,
                            "durationMillis" to selectedDurationMillis,
                            "menuConfig" to menuConfig,
                            "statusProduksi" to "PERSIAPAN PAGI",
                            "currentStep" to 2,
                            "createdAt" to System.currentTimeMillis()
                        )
                        batch.set(scheduleRef, mainData, SetOptions.merge())

                        schoolList.filter { it.isSelected }.forEach { s ->
                            val destData = hashMapOf(
                                "schoolEmail" to s.email, "schoolName" to s.name, "address" to s.address,
                                "p13" to s.p13, "p46" to s.p46, "pSMP" to s.pSMP, "pSMA" to s.pSMA, "p3B" to s.p3B,
                                "targetPorsi" to (s.p13 + s.p46 + s.pSMP + s.pSMA + s.p3B),
                                "latitude" to s.lat, "longitude" to s.lon, "statusPengiriman" to "MENUNGGU",
                                "budget" to (s.p13 * 13000 + (s.p46 + s.pSMP + s.pSMA + s.p3B) * 15000),
                                "level" to s.level
                            )
                            batch.set(scheduleRef.collection("destinations").document(s.email), destData)
                        }

                        batch.commit().addOnSuccessListener {
                            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                            if (selectedDateStr == today) {
                                vendorRef.update(mapOf("currentStep" to 2, "statusProduksi" to "PERSIAPAN PAGI", "targetPorsi" to totalPGlobal))
                            }
                            hideLoading()
                            Toast.makeText(this, "Jadwal Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                            finish()
                        }.addOnFailureListener {
                            hideLoading()
                            btnSave.isEnabled = true
                            Toast.makeText(this, "Gagal simpan: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                hideLoading()
                runOnUiThread {
                    btnSave.isEnabled = true
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateGiziLabel(catId: String, levels: Set<String>): String {
        return if (catId == "CAT_13K") {
            val hasSD = levels.any { it == "SD" || it == "MI" }
            val hasKecil = levels.any { it == "PAUD" || it == "TK" || it == "RA" }
            when {
                hasSD && hasKecil -> "GIZI A : PAUD-Kelas 3 (Rp13.000)"
                hasKecil -> "GIZI A : PAUD/TK/RA (Rp13.000)"
                else -> "GIZI A : SD Kelas 1-3 (Rp13.000)"
            }
        } else {
            val hasSD = levels.any { it == "SD" || it == "MI" }
            val hasSMP = levels.any { it == "SMP" || it == "MTS" }
            val hasSMA = levels.any { it == "SMA" || it == "SMK" || it == "MA" }
            val parts = mutableListOf<String>()
            if (hasSD) parts.add("Kelas 4-6")
            if (hasSMP) parts.add("SMP")
            if (hasSMA) parts.add("SMA")
            val range = if (parts.isEmpty()) "Standar" else parts.joinToString("/")
            "GIZI B : $range (Rp15.000)"
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.show(supportFragmentManager, "DP")
        picker.addOnPositiveButtonClickListener {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = it
            selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
            btnSelectDate.text = selectedDateStr
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(2).build()
        picker.show(supportFragmentManager, "TP")
        picker.addOnPositiveButtonClickListener {
            selectedDurationMillis = TimeUnit.HOURS.toMillis(picker.hour.toLong()) + TimeUnit.MINUTES.toMillis(picker.minute.toLong())
            btnSelectDuration.text = "${picker.hour} Jam ${picker.minute} Menit"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos) 
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }
}

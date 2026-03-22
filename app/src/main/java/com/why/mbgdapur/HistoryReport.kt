package com.why.mbgdapur

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class HistoryReport(
    var id: String = "",

    @get:PropertyName("menu") @set:PropertyName("menu")
    var menu: String? = null,

    @get:PropertyName("nama_menu") @set:PropertyName("nama_menu")
    var nama_menu: String? = null,

    @get:PropertyName("foto_menu") @set:PropertyName("foto_menu")
    var foto_menu: String? = null,

    @get:PropertyName("menuPhotoBase64") @set:PropertyName("menuPhotoBase64")
    var menuPhotoBase64: String? = null,

    @get:PropertyName("menuPhotoUrl") @set:PropertyName("menuPhotoUrl")
    var menuPhotoUrl: String? = null,

    @get:PropertyName("total_porsi") @set:PropertyName("total_porsi")
    var total_porsi: Long = 0,

    @get:PropertyName("porsi_target") @set:PropertyName("porsi_target")
    var porsi_target: Long = 0,

    @get:PropertyName("totalTargetPorsi") @set:PropertyName("totalTargetPorsi")
    var totalTargetPorsi: Long = 0,

    @get:PropertyName("porsi_realisasi") @set:PropertyName("porsi_realisasi")
    var porsi_realisasi: Long = 0,

    @get:PropertyName("porsiRealisasi") @set:PropertyName("porsiRealisasi")
    var porsiRealisasi: Long = 0,

    @get:PropertyName("wastageCount") @set:PropertyName("wastageCount")
    var wastageCount: Long = 0,

    @get:PropertyName("porsi_rusak") @set:PropertyName("porsi_rusak")
    var porsi_rusak: Long = 0,

    @get:PropertyName("wastageReason") @set:PropertyName("wastageReason")
    var wastageReason: String? = null,

    @get:PropertyName("alasan_utama") @set:PropertyName("alasan_utama")
    var alasan_utama: String? = null,

    @get:PropertyName("total_pendapatan") @set:PropertyName("total_pendapatan")
    var total_pendapatan: Long = 0,

    @get:PropertyName("totalRevenue") @set:PropertyName("totalRevenue")
    var totalRevenue: Long = 0,

    @get:PropertyName("total_modal") @set:PropertyName("total_modal")
    var total_modal: Long = 0,

    @get:PropertyName("totalCapital") @set:PropertyName("totalCapital")
    var totalCapital: Long = 0,

    @get:PropertyName("foto_nota") @set:PropertyName("foto_nota") var foto_nota: String? = null,
    @get:PropertyName("receiptPhotoUrl") @set:PropertyName("receiptPhotoUrl") var receiptPhotoUrl: String? = null,
    @get:PropertyName("foto_bahan") @set:PropertyName("foto_bahan") var foto_bahan: String? = null,
    @get:PropertyName("rawMaterialPhotoUrl") @set:PropertyName("rawMaterialPhotoUrl") var rawMaterialPhotoUrl: String? = null,
    @get:PropertyName("berat_bahan_baku") @set:PropertyName("berat_bahan_baku") var berat_bahan_baku: String? = null,
    @get:PropertyName("rawMaterialWeight") @set:PropertyName("rawMaterialWeight") var rawMaterialWeight: String? = null,
    @get:PropertyName("foto_ai") @set:PropertyName("foto_ai") var foto_ai: String? = null,
    @get:PropertyName("aiPhotoBase64") @set:PropertyName("aiPhotoBase64") var aiPhotoBase64: String? = null,
    @get:PropertyName("foto_ai_2") @set:PropertyName("foto_ai_2") var foto_ai_2: String? = null,
    @get:PropertyName("aiPhoto2Base64") @set:PropertyName("aiPhoto2Base64") var aiPhoto2Base64: String? = null,
    @get:PropertyName("berat_boks") @set:PropertyName("berat_boks") var berat_boks: String? = null,
    @get:PropertyName("boxWeight") @set:PropertyName("boxWeight") var boxWeight: String? = null,
    @get:PropertyName("hasil_ai") @set:PropertyName("hasil_ai") var hasil_ai: String? = null,
    @get:PropertyName("aiResult") @set:PropertyName("aiResult") var aiResult: String? = null,
    @get:PropertyName("foto_safety") @set:PropertyName("foto_safety") var foto_safety: String? = null,
    @get:PropertyName("samplePhotoUrl") @set:PropertyName("samplePhotoUrl") var samplePhotoUrl: String? = null,
    @get:PropertyName("foto_sanitasi") @set:PropertyName("foto_sanitasi") var foto_sanitasi: String? = null,
    @get:PropertyName("sanitasiPhotoBase64") @set:PropertyName("sanitasiPhotoBase64") var sanitasiPhotoBase64: String? = null,
    @get:PropertyName("sanitasiPhotoUrl") @set:PropertyName("sanitasiPhotoUrl") var sanitasiPhotoUrl: String? = null,
    @get:PropertyName("suhu_chiller") @set:PropertyName("suhu_chiller") var suhu_chiller: String? = null,
    @get:PropertyName("chillerTemp") @set:PropertyName("chillerTemp") var chillerTemp: String? = null,

    @get:PropertyName("transaction_id") @set:PropertyName("transaction_id")
    var transaction_id: String? = null,

    @get:PropertyName("submitted_by") @set:PropertyName("submitted_by")
    var submitted_by: String? = null,

    @get:PropertyName("waktu_submit") @set:PropertyName("waktu_submit")
    var waktu_submit: Timestamp? = null,

    @get:PropertyName("categories") @set:PropertyName("categories")
    var categories: List<Map<String, Any>>? = null
) {
    fun resolvedMenuName(): String = (nama_menu ?: menu) ?: ""
    fun resolvedMenuPhoto(): String = (foto_menu ?: menuPhotoUrl ?: menuPhotoBase64) ?: ""
    fun resolvedTargetPorsi(): Long = if (porsi_target > 0) porsi_target else if (total_porsi > 0) total_porsi else totalTargetPorsi
    fun resolvedRealisasiPorsi(): Long = if (porsi_realisasi > 0) porsi_realisasi else porsiRealisasi
    fun resolvedWastageCount(): Long = if (porsi_rusak > 0) porsi_rusak else wastageCount
    fun resolvedWastageReason(): String = (alasan_utama ?: wastageReason) ?: ""
    fun resolvedRevenue(): Long = if (total_pendapatan > 0) total_pendapatan else totalRevenue
    fun resolvedCapital(): Long = if (total_modal > 0) total_modal else totalCapital
    fun resolvedFotoNota(): String = (foto_nota ?: receiptPhotoUrl) ?: ""
    fun resolvedFotoBahan(): String = (foto_bahan ?: rawMaterialPhotoUrl) ?: ""
    fun resolvedBeratBahan(): String = (berat_bahan_baku ?: rawMaterialWeight) ?: "--"
    fun resolvedFotoAi(): String = (foto_ai ?: aiPhotoBase64) ?: ""
    fun resolvedFotoAi2(): String = (foto_ai_2 ?: aiPhoto2Base64) ?: ""
    fun resolvedBeratBoks(): String = (berat_boks ?: boxWeight) ?: "--"
    fun resolvedHasilAi(): String = (hasil_ai ?: aiResult) ?: "Lulus Verifikasi"
    fun resolvedFotoSafety(): String = (foto_safety ?: samplePhotoUrl) ?: ""
    fun resolvedFotoSanitasi(): String = (foto_sanitasi ?: sanitasiPhotoUrl ?: sanitasiPhotoBase64) ?: ""
    fun resolvedSuhuChiller(): String = (suhu_chiller ?: chillerTemp) ?: "--"
}

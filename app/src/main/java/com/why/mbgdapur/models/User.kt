package com.why.mbgdapur.models

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "", // school, vendor, driver, pengawas
    val isApproved: Boolean = false,
    
    // School specific fields (Optional or grouped)
    val schoolDetails: SchoolDetails? = null,
    
    // Assignment fields
    val assignedVendorId: String = "",
    val assignedDriverId: String = "",
    
    val createdAt: Long = System.currentTimeMillis()
)

data class SchoolDetails(
    val npsn: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val level: String = "", // SD, SMP, SMA
    val contactPerson: String = "",
    val whatsapp: String = "",
    val totalStudents: Int = 0,
    val breakTime: String = "",
    val verificationImageUrl: String = ""
)

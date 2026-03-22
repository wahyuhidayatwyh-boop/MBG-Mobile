package com.why.mbgdapur.models

data class Vendor(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val status: String = "Active",
    val integrityScore: Int = 100,
    val dailyTarget: Int = 0,
    val currentProgress: Int = 0
)
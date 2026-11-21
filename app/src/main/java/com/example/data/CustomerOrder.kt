package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_orders")
data class CustomerOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val customerUsername: String,
    val medicineId: Long,
    val medicineName: String,
    val medicineType: String,
    val quantity: Int,
    val totalPrice: Double,
    val status: String, // "Pending" / "Confirmed" / "Cancelled"
    val pharmacyName: String,
    val dateString: String
)

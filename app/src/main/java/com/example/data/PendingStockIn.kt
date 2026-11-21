package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_stock_ins")
data class PendingStockIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val medicineName: String,
    val medicineType: String,            // "tablet", "syrup", "ointment", "injection", "other"
    val timestamp: Long,                 // Time of the stock update request
    val addedBoxes: Int = 0,
    val addedStrips: Int = 0,
    val addedPcs: Int = 0,
    val addedBottles: Int = 0,
    val addedTubes: Int = 0,
    val addedVials: Int = 0,
    val addedOtherQty: Double = 0.0,
    val purchasePrice: Double? = null,
    val salePrice: Double? = null,
    val staffUsername: String = ""
)

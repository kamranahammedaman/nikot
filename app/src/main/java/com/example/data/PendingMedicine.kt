package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_medicines")
data class PendingMedicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val staffUsername: String,
    
    val type: String,
    val name: String,
    val dosageStrength: String = "",
    val company: String,
    val genericName: String,
    val barcode: String,
    val expiryDate: String,
    
    val purchasePriceBox: Double = 0.0,
    val salePriceBox: Double = 0.0,
    val stripsPerBox: Int = 1,
    val pcsPerStrip: Int = 1,
    val boxesInStock: Int = 0,
    val stripsInStock: Int = 0,
    val pcsInStock: Int = 0,
    
    val mlPerBottle: Int = 0,
    val bottlesInStock: Int = 0,
    
    val gramsPerTube: Int = 0,
    val tubesInStock: Int = 0,
    
    val vialsInStock: Int = 0,
    
    val otherQuantity: Double = 0.0,
    val otherUnit: String = "",
    
    val purchasePriceUnit: Double = 0.0,
    val salePriceUnit: Double = 0.0,
    
    // Low stock alert threshold
    val lowStockThreshold: Int = 0,
    
    // If it is modifying an existing medicine:
    val targetMedicineId: Long? = null // null means a brand new medicine
)

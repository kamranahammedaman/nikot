package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class BillItem(
    val medicineId: Long,
    val name: String,
    val type: String, // "tablet", "syrup", "ointment", "injection", "other"
    val formatDescription: String, // e.g. "১ পাতা ৫ পিস" or "২ বোতল"
    val totalPrice: Double,
    
    // quantities sold (to restore/deduct stock)
    val soldBoxes: Int = 0,
    val soldStrips: Int = 0,
    val soldPcs: Int = 0,
    val soldBottles: Int = 0,
    val soldTubes: Int = 0,
    val soldVials: Int = 0,
    val soldOtherQty: Double = 0.0
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val billNumber: String, // e.g. RO200626001
    val timestamp: Long,               // millisecond timestamp
    val totalAmount: Double,
    val itemsJson: String,               // list of BillItem serialized as JSON
    val isReturned: Boolean = false,
    val returnedTime: Long = 0L,
    val returnedAmount: Double = 0.0,
    val returnedItemsJson: String = "", // List of BillItem that were returned serialized as JSON
    val discount: Double = 0.0,           // Added discount field
    val pharmacyOwnerUsername: String = "admin", // মালিকানা ফার্মেসী ইউজারনেম
    val isOnlineOrder: Boolean = false, // অনলাইন অর্ডারের বিল কিনা তা নির্ধারণ করার ফিল্ড
    val staffUsername: String = "" // বিল বিক্রয়কারী স্টাফ সদস্যের ইউজারনেম
)

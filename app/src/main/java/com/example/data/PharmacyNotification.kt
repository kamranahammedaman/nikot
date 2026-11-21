package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pharmacy_notifications")
data class PharmacyNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleEn: String,
    val titleBn: String,
    val messageEn: String,
    val messageBn: String,
    val timestamp: Long,
    val targetRole: String, // "admin", "staff", "customer", or "all"
    val targetUsername: String = "", // empty means sent to all users of that role
    val isRead: Boolean = false,
    val isDismissedFromDashboard: Boolean = false
)

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,               // "tablet" (Tablet/Capsule), "syrup" (Syrup/Drop), "ointment" (Ointment/Cream), "injection" (Injection), "other" (Other)
    val name: String,               // ওষুধের নাম (ব্র্যান্ড)
    val dosageStrength: String = "",// ডোজ/শক্তি (যেমন: 20mg, 40mg, 100ml, 500mg)
    val company: String,            // কোম্পানির নাম
    val genericName: String,        // জেনরীক নাম
    val barcode: String,            // বারকোড নম্বর
    val expiryDate: String,         // মেয়াদ তারিখ (e.g. YYYY-MM-DD)
    val pharmacyOwnerUsername: String = "admin", // মালিকানা ফার্মেসী ইউজারনেম
    
    // Tablet/Capsule Price & Stock Sub-structure
    val purchasePriceBox: Double = 0.0,  // ১ বক্সের মোট ক্রয় মূল্য
    val salePriceBox: Double = 0.0,      // ১ বক্সের মোট বিক্রয় মূল্য
    val stripsPerBox: Int = 1,          // প্রতি বক্সে কতটি পাতা
    val pcsPerStrip: Int = 1,           // প্রতি পাতায় কতটি পিস
    val boxesInStock: Int = 0,          // এখন কতটি বক্স
    val stripsInStock: Int = 0,         // এখন কতটি পাতা
    val pcsInStock: Int = 0,            // এখন কতটি পিস
    
    // Syrup/Drop Details
    val mlPerBottle: Int = 0,           // ML per bottle
    val bottlesInStock: Int = 0,        // how many bottles in stock
    
    // Ointment/Cream Details
    val gramsPerTube: Int = 0,          // Grams per tube/pot
    val tubesInStock: Int = 0,          // how many tubes/pots in stock
    
    // Injection Details
    val vialsInStock: Int = 0,          // how many vials/ampules in stock
    
    // Other Details
    val otherQuantity: Double = 0.0,    // Custom quantity
    val otherUnit: String = "",         // Custom unit
    
    // General unit pricing for non-tablet items
    val purchasePriceUnit: Double = 0.0,
    val salePriceUnit: Double = 0.0,
    
    // Low stock alert threshold
    val lowStockThreshold: Int = 0
) {
    @get:com.google.firebase.database.Exclude
    val fullName: String
        get() = if (dosageStrength.isNotBlank()) "$name $dosageStrength" else name

    fun normalized(): Medicine {
        if (type != "tablet") return this
        val sPerBox = if (stripsPerBox > 0) stripsPerBox else 10
        val pPerStrip = if (pcsPerStrip > 0) pcsPerStrip else 10
        val totalPieces = (boxesInStock * sPerBox * pPerStrip) + (stripsInStock * pPerStrip) + pcsInStock
        val finalBoxes = totalPieces / (sPerBox * pPerStrip)
        val remPieces = totalPieces % (sPerBox * pPerStrip)
        val finalStrips = remPieces / pPerStrip
        val finalPcs = remPieces % pPerStrip
        return this.copy(
            stripsPerBox = sPerBox,
            pcsPerStrip = pPerStrip,
            boxesInStock = finalBoxes,
            stripsInStock = finalStrips,
            pcsInStock = finalPcs
        )
    }

    // Computed Properties for Tablet
    val purchasePriceStrip: Double
        get() = if (stripsPerBox > 0) purchasePriceBox / stripsPerBox else 0.0

    val purchasePricePiece: Double
        get() {
            val stripPrice = purchasePriceStrip
            return if (pcsPerStrip > 0) stripPrice / pcsPerStrip else 0.0
        }

    val salePriceStrip: Double
        get() = if (stripsPerBox > 0) salePriceBox / stripsPerBox else 0.0

    val salePricePiece: Double
        get() {
            val stripPrice = salePriceStrip
            return if (pcsPerStrip > 0) stripPrice / pcsPerStrip else 0.0
        }

    val profitPerPiece: Double
        get() = salePricePiece - purchasePricePiece

    val profitPercentageTablet: Double
        get() = if (purchasePricePiece > 0.0) (profitPerPiece / purchasePricePiece) * 100.0 else 0.0

    // Computed Properties for general types
    val profitPerUnit: Double
        get() = salePriceUnit - purchasePriceUnit

    val profitPercentageUnit: Double
        get() = if (purchasePriceUnit > 0.0) (profitPerUnit / purchasePriceUnit) * 100.0 else 0.0

    // Total pieces calculation for tablet
    val totalPiecesInStock: Int
        get() = (boxesInStock * stripsPerBox * pcsPerStrip) + (stripsInStock * pcsPerStrip) + pcsInStock

    fun calculatePriceForQuantity(qty: Int): Double {
        if (type == "tablet") {
            val pcsPerBox = stripsPerBox * pcsPerStrip
            if (pcsPerBox <= 0) return 0.0
            val boxes = qty / pcsPerBox
            val rem = qty % pcsPerBox
            val strips = if (pcsPerStrip > 0) rem / pcsPerStrip else 0
            val pieces = if (pcsPerStrip > 0) rem % pcsPerStrip else rem
            
            return (boxes * salePriceBox) + (strips * salePriceStrip) + (pieces * salePricePiece)
        } else {
            return salePriceUnit * qty
        }
    }

    fun formatQuantityString(qty: Int, isBengali: Boolean): String {
        if (type == "tablet") {
            val pcsPerBox = stripsPerBox * pcsPerStrip
            if (pcsPerBox <= 0) {
                val num = if (isBengali) formatBengaliNumberLocal(qty.toString()) else qty.toString()
                return "$num ${if (isBengali) "পিস" else "Pcs"}"
            }
            val boxes = qty / pcsPerBox
            val rem = qty % pcsPerBox
            val strips = if (pcsPerStrip > 0) rem / pcsPerStrip else 0
            val pieces = if (pcsPerStrip > 0) rem % pcsPerStrip else rem
            
            val parts = mutableListOf<String>()
            if (boxes > 0) {
                val num = if (isBengali) formatBengaliNumberLocal(boxes.toString()) else boxes.toString()
                parts.add("$num ${if (isBengali) "বক্স" else "Box"}")
            }
            if (strips > 0) {
                val num = if (isBengali) formatBengaliNumberLocal(strips.toString()) else strips.toString()
                parts.add("$num ${if (isBengali) "পাতা" else "Strip"}")
            }
            if (pieces > 0) {
                val num = if (isBengali) formatBengaliNumberLocal(pieces.toString()) else pieces.toString()
                parts.add("$num ${if (isBengali) "পিস" else "Pcs"}")
            }
            if (parts.isEmpty()) {
                val zero = if (isBengali) "০" else "0"
                return "$zero ${if (isBengali) "পিস" else "Pcs"}"
            }
            return parts.joinToString(" + ")
        } else {
            val num = if (isBengali) formatBengaliNumberLocal(qty.toString()) else qty.toString()
            return "$num ${if (isBengali) "পিস" else "Pcs"}"
        }
    }

    private fun formatBengaliNumberLocal(input: String): String {
        val englishToBengaliMap = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
        return input.map { englishToBengaliMap[it] ?: it }.joinToString("")
    }
}

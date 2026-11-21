package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Medicine::class, Bill::class, StockInRecord::class, PendingStockIn::class, PendingMedicine::class, CustomerOrder::class, PharmacyNotification::class], version = 12, exportSchema = false)
abstract class PharmacyDatabase : RoomDatabase() {
    abstract fun pharmacyDao(): PharmacyDao

    companion object {
        @Volatile
        private var INSTANCE: PharmacyDatabase? = null

        fun getDatabase(context: Context): PharmacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabase::class.java,
                    "pharmacy_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

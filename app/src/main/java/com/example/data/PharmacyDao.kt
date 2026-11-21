package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PharmacyDao {
    @Query("SELECT * FROM medicines ORDER BY name ASC")
    fun getAllMedicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    suspend fun getMedicineById(id: Long): Medicine?

    @Query("SELECT * FROM medicines WHERE barcode = :barcode LIMIT 1")
    suspend fun getMedicineByBarcode(barcode: String): Medicine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: Medicine): Long

    @Update
    suspend fun updateMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)

    @Query("SELECT * FROM bills ORDER BY timestamp DESC")
    fun getAllBills(): Flow<List<Bill>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("SELECT * FROM bills WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getBillsForRange(startTime: Long, endTime: Long): Flow<List<Bill>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockInRecord(record: StockInRecord): Long

    @Query("SELECT * FROM stock_in_records ORDER BY timestamp DESC")
    fun getAllStockInRecords(): Flow<List<StockInRecord>>

    @Delete
    suspend fun deleteStockInRecord(record: StockInRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingStockIn(pendingStockIn: PendingStockIn): Long

    @Query("SELECT * FROM pending_stock_ins ORDER BY timestamp DESC")
    fun getAllPendingStockIns(): Flow<List<PendingStockIn>>

    @Delete
    suspend fun deletePendingStockIn(pendingStockIn: PendingStockIn)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingMedicine(pendingMedicine: PendingMedicine): Long

    @Query("SELECT * FROM pending_medicines ORDER BY timestamp DESC")
    fun getAllPendingMedicines(): Flow<List<PendingMedicine>>

    @Delete
    suspend fun deletePendingMedicine(pendingMedicine: PendingMedicine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerOrder(customerOrder: CustomerOrder): Long

    @Query("SELECT * FROM customer_orders ORDER BY timestamp DESC")
    fun getAllCustomerOrders(): Flow<List<CustomerOrder>>

    @Query("SELECT * FROM customer_orders WHERE customerUsername = :username ORDER BY timestamp DESC")
    fun getCustomerOrdersForUser(username: String): Flow<List<CustomerOrder>>

    @Delete
    suspend fun deleteCustomerOrder(customerOrder: CustomerOrder)

    @Query("SELECT * FROM pharmacy_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<PharmacyNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: PharmacyNotification): Long

    @Update
    suspend fun updateNotification(notification: PharmacyNotification)

    @Delete
    suspend fun deleteNotification(notification: PharmacyNotification)

    @Query("SELECT * FROM pharmacy_notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: Long): PharmacyNotification?
}

package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class PharmacyRepository(private val pharmacyDao: PharmacyDao) {
    val allMedicines: Flow<List<Medicine>> = pharmacyDao.getAllMedicines()
    val allBills: Flow<List<Bill>> = pharmacyDao.getAllBills()
    val allStockInRecords: Flow<List<StockInRecord>> = pharmacyDao.getAllStockInRecords()
    val allPendingStockIns: Flow<List<PendingStockIn>> = pharmacyDao.getAllPendingStockIns()
    val allPendingMedicines: Flow<List<PendingMedicine>> = pharmacyDao.getAllPendingMedicines()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, BillItem::class.java)
    private val billItemAdapter = moshi.adapter<List<BillItem>>(listType)

    fun serializeBillItems(items: List<BillItem>): String {
        return try {
            billItemAdapter.toJson(items)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserializeBillItems(json: String): List<BillItem> {
        return try {
            billItemAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMedicineById(id: Long): Medicine? {
        return pharmacyDao.getMedicineById(id)
    }

    suspend fun getMedicineByBarcode(barcode: String): Medicine? {
        return pharmacyDao.getMedicineByBarcode(barcode)
    }

    suspend fun insertMedicine(medicine: Medicine): Long {
        return pharmacyDao.insertMedicine(medicine.normalized())
    }

    suspend fun updateMedicine(medicine: Medicine) {
        pharmacyDao.updateMedicine(medicine.normalized())
    }

    suspend fun deleteMedicine(medicine: Medicine) {
        pharmacyDao.deleteMedicine(medicine)
    }

    suspend fun confirmBill(bill: Bill, items: List<BillItem>) {
        // 1. Insert the bill
        pharmacyDao.insertBill(bill)

        // 2. Decrement stock for each item
        for (item in items) {
            val medicine = pharmacyDao.getMedicineById(item.medicineId) ?: continue
            val updatedMedicine = when (medicine.type) {
                "tablet" -> {
                    val currentTotalPieces = medicine.totalPiecesInStock
                    val soldTotalPieces = (item.soldBoxes * medicine.stripsPerBox * medicine.pcsPerStrip) +
                            (item.soldStrips * medicine.pcsPerStrip) +
                            item.soldPcs
                    val remainingPcsTotal = (currentTotalPieces - soldTotalPieces).coerceAtLeast(0)

                    val multiplier = medicine.stripsPerBox * medicine.pcsPerStrip
                    if (multiplier > 0) {
                        val remBoxes = remainingPcsTotal / multiplier
                        val remPcsAfterBoxes = remainingPcsTotal % multiplier
                        val remStrips = if (medicine.pcsPerStrip > 0) remPcsAfterBoxes / medicine.pcsPerStrip else 0
                        val remPcs = if (medicine.pcsPerStrip > 0) remPcsAfterBoxes % medicine.pcsPerStrip else 0

                        medicine.copy(
                            boxesInStock = remBoxes,
                            stripsInStock = remStrips,
                            pcsInStock = remPcs
                        )
                    } else {
                        medicine.copy(
                            boxesInStock = (medicine.boxesInStock - item.soldBoxes).coerceAtLeast(0),
                            stripsInStock = (medicine.stripsInStock - item.soldStrips).coerceAtLeast(0),
                            pcsInStock = (medicine.pcsInStock - item.soldPcs).coerceAtLeast(0)
                        )
                    }
                }
                "syrup" -> {
                    medicine.copy(
                        bottlesInStock = (medicine.bottlesInStock - item.soldBottles).coerceAtLeast(0)
                    )
                }
                "ointment" -> {
                    medicine.copy(
                        tubesInStock = (medicine.tubesInStock - item.soldTubes).coerceAtLeast(0)
                    )
                }
                "injection" -> {
                    medicine.copy(
                        vialsInStock = (medicine.vialsInStock - item.soldVials).coerceAtLeast(0)
                    )
                }
                else -> { // "other"
                    medicine.copy(
                        otherQuantity = (medicine.otherQuantity - item.soldOtherQty).coerceAtLeast(0.0)
                    )
                }
            }
            pharmacyDao.updateMedicine(updatedMedicine.normalized())
        }
    }

    fun getBillsForToday(): Flow<List<Bill>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = startTime + (24 * 60 * 60 * 1000) - 1
        return pharmacyDao.getBillsForRange(startTime, endTime)
    }

    fun getBillsForRange(startTime: Long, endTime: Long): Flow<List<Bill>> {
        return pharmacyDao.getBillsForRange(startTime, endTime)
    }

    suspend fun insertStockInRecord(record: StockInRecord): Long {
        return pharmacyDao.insertStockInRecord(record)
    }

    suspend fun deleteStockInRecord(record: StockInRecord) {
        pharmacyDao.deleteStockInRecord(record)
    }

    suspend fun insertPendingStockIn(pendingStockIn: PendingStockIn): Long {
        return pharmacyDao.insertPendingStockIn(pendingStockIn)
    }

    suspend fun deletePendingStockIn(pendingStockIn: PendingStockIn) {
        pharmacyDao.deletePendingStockIn(pendingStockIn)
    }

    suspend fun insertPendingMedicine(pendingMedicine: PendingMedicine): Long {
        return pharmacyDao.insertPendingMedicine(pendingMedicine)
    }

    suspend fun deletePendingMedicine(pendingMedicine: PendingMedicine) {
        pharmacyDao.deletePendingMedicine(pendingMedicine)
    }

    suspend fun returnBill(bill: Bill, returnedItems: List<BillItem>, refundAmount: Double) {
        val updatedBill = bill.copy(
            isReturned = true,
            returnedTime = System.currentTimeMillis(),
            returnedAmount = refundAmount,
            returnedItemsJson = serializeBillItems(returnedItems)
        )
        pharmacyDao.insertBill(updatedBill)

        for (item in returnedItems) {
            val medicine = pharmacyDao.getMedicineById(item.medicineId) ?: continue
            val updatedMedicine = when (medicine.type) {
                "tablet" -> {
                    val currentTotalPieces = medicine.totalPiecesInStock
                    val returnedTotalPieces = (item.soldBoxes * medicine.stripsPerBox * medicine.pcsPerStrip) +
                            (item.soldStrips * medicine.pcsPerStrip) +
                            item.soldPcs
                    val newPiecesTotal = currentTotalPieces + returnedTotalPieces

                    val multiplier = medicine.stripsPerBox * medicine.pcsPerStrip
                    if (multiplier > 0) {
                        val remBoxes = newPiecesTotal / multiplier
                        val remPcsAfterBoxes = newPiecesTotal % multiplier
                        val remStrips = if (medicine.pcsPerStrip > 0) remPcsAfterBoxes / medicine.pcsPerStrip else 0
                        val remPcs = if (medicine.pcsPerStrip > 0) remPcsAfterBoxes % medicine.pcsPerStrip else 0

                        medicine.copy(
                            boxesInStock = remBoxes,
                            stripsInStock = remStrips,
                            pcsInStock = remPcs
                        )
                    } else {
                        medicine.copy(
                            boxesInStock = medicine.boxesInStock + item.soldBoxes,
                            stripsInStock = medicine.stripsInStock + item.soldStrips,
                            pcsInStock = medicine.pcsInStock + item.soldPcs
                        )
                    }
                }
                "syrup" -> {
                    medicine.copy(
                        bottlesInStock = medicine.bottlesInStock + item.soldBottles
                    )
                }
                "ointment" -> {
                    medicine.copy(
                        tubesInStock = medicine.tubesInStock + item.soldTubes
                    )
                }
                "injection" -> {
                    medicine.copy(
                        vialsInStock = medicine.vialsInStock + item.soldVials
                    )
                }
                else -> {
                    medicine.copy(
                        otherQuantity = medicine.otherQuantity + item.soldOtherQty
                    )
                }
            }
            pharmacyDao.updateMedicine(updatedMedicine.normalized())
        }
    }

    suspend fun insertCustomerOrder(order: CustomerOrder): Long {
        return pharmacyDao.insertCustomerOrder(order)
    }

    fun getAllCustomerOrders(): Flow<List<CustomerOrder>> {
        return pharmacyDao.getAllCustomerOrders()
    }

    fun getCustomerOrdersForUser(username: String): Flow<List<CustomerOrder>> {
        return pharmacyDao.getCustomerOrdersForUser(username)
    }

    suspend fun deleteCustomerOrder(order: CustomerOrder) {
        pharmacyDao.deleteCustomerOrder(order)
    }

    val allNotifications: Flow<List<PharmacyNotification>> = pharmacyDao.getAllNotifications()

    suspend fun insertNotification(notification: PharmacyNotification): Long {
        return pharmacyDao.insertNotification(notification)
    }

    suspend fun updateNotification(notification: PharmacyNotification) {
        pharmacyDao.updateNotification(notification)
    }

    suspend fun deleteNotification(notification: PharmacyNotification) {
        pharmacyDao.deleteNotification(notification)
    }

    suspend fun getNotificationById(id: Long): PharmacyNotification? {
        return pharmacyDao.getNotificationById(id)
    }
}

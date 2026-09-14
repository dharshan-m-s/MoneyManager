package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val database: MoneyManagerDatabase,
    private val billDao: BillDao,
    private val transactionDao: TransactionDao,
    private val backupManager: BackupManager
) {
    /**
     * Marks an existing bill instance as paid by linking it to an already-recorded transaction.
     * Passing null means the user explicitly chose "Paid by Cash"; this does not create a ledger
     * transaction because the user may already have recorded the cash movement elsewhere.
     */
    suspend fun markAsPaid(instance: BillInstanceEntity, paymentTransactionId: Long?) {
        database.withTransaction {
            val current = billDao.findByPaymentTransactionId(paymentTransactionId ?: -1L)
            if (paymentTransactionId != null) {
                require(transactionDao.findById(paymentTransactionId) != null) {
                    "Payment transaction no longer exists"
                }
                require(current == null || current.id == instance.id) {
                    "Payment transaction is already linked to another bill"
                }
            }

            billDao.updateInstance(
                instance.copy(
                    paid = true,
                    paidAtEpochMillis = System.currentTimeMillis(),
                    paymentTransactionId = paymentTransactionId
                )
            )
        }
        backupManager.scheduleAfterWrite()
    }
}
